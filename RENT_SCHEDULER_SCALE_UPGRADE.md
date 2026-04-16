# Rent Scheduler — Scale Upgrade (100K+ Tenants)

## Problem

The original `RentSchedulerService` was designed for small deployments. At 100K tenants it would:

- **OOM** — loaded all 100K `RegistrationDocument` objects into memory at once
- **N+1 queries** — 100K individual DB queries for pending balance calculation
- **100+ minute runtime** — 300K sequential HTTP calls for notifications (email + SMS + WhatsApp)
- **Crash on audit log** — embedded 100K `TenantJobDetail` objects in a single MongoDB document (16MB BSON limit)
- **Block other jobs** — single-threaded `@Scheduled` pool
- **Hang indefinitely** — no timeout on notification HTTP calls

## Solution Overview

| Optimization | Before | After |
|-------------|--------|-------|
| Tenant loading | All 100K in memory | Paginated (500 per batch) |
| Pending balance | 100K individual queries | 1 aggregation per batch (200 total) |
| Record inserts | 100K individual `save()` | Bulk `saveAll()` per batch |
| Notifications | 300K sequential HTTP calls | 300K parallel (20-thread pool) |
| Audit log | 100K objects in 1 document (~50MB, crashes) | Separate collection |
| HTTP timeouts | None (can hang forever) | 5s connect, 10s read |
| Scheduler threads | 1 (blocks everything) | 3 concurrent |
| **Estimated runtime** | **100+ minutes** | **5–10 minutes** |

## Files Created

### 1. `TenantJobDetailDocument.java`
**Path:** `registration-services/src/main/java/.../document/TenantJobDetailDocument.java`

Separate MongoDB collection (`schedulerTenantDetails`) for per-tenant scheduler audit details. Each document is small (~500 bytes). Compound index on `(jobId, tenantId)` for fast lookups.

### 2. `TenantJobDetailRepository.java`
**Path:** `registration-services/src/main/java/.../repository/TenantJobDetailRepository.java`

Spring Data repository for the new collection. Methods: `findByJobId()`, `countByJobId()`.

### 3. `RentPaymentTransactionCustomRepositoryImpl.java`
**Path:** `registration-services/src/main/java/.../repository/RentPaymentTransactionCustomRepositoryImpl.java`

Custom MongoDB aggregation that computes pending balances for a batch of tenant IDs in **one query**:

```
Pipeline:
  $match  → { tenantId: { $in: [batch] }, status: { $in: ["PENDING","PARTIAL","OVERDUE"] } }
  $group  → { _id: "$tenantId", totalRemaining: { $sum: "$remainingAmount" } }
```

Replaces the old N+1 pattern (1 query per tenant → 1 query per 500 tenants).

### 4. `AsyncNotificationService.java`
**Path:** `registration-services/src/main/java/.../service/AsyncNotificationService.java`

`@Async` wrapper around `NotificationClient`. Each method (`sendEmailAsync`, `sendSmsAsync`, `sendWhatsappAsync`) returns `CompletableFuture<NotificationChannelResult>`. Uses the `notificationExecutor` thread pool.

For each tenant, all 3 channels fire concurrently instead of sequentially.

### 5. `SchedulerInfraConfig.java`
**Path:** `registration-services/src/main/java/.../config/SchedulerInfraConfig.java`

Centralized configuration:

| Bean | Purpose |
|------|---------|
| `RestTemplate` (primary) | Connect timeout 5s, read timeout 10s |
| `notificationExecutor` | 20 core / 50 max threads, queue 5000 |
| `taskScheduler` | 3-thread pool for `@Scheduled` jobs |

## Files Modified

### 1. `RentSchedulerService.java` — Full Rewrite

**Processing flow (per batch of 500):**

```
1. Load page of 500 tenants (paginated query)
2. Filter: skip already-generated + zero-rent tenants
3. Batch aggregation: compute pending balances (1 MongoDB aggregation)
4. Build PENDING records → bulk saveAll() (1 DB call)
5. Fire async notifications (3 × CompletableFuture per tenant)
6. Await all futures, collect results
7. Bulk-insert audit details to schedulerTenantDetails collection
```

**Key changes:**
- `@RequiredArgsConstructor` → explicit constructor (added `AsyncNotificationService`, `TenantJobDetailRepository`)
- Counters use `AtomicInteger` (thread-safe with async operations)
- `processBatch()` method handles one page of tenants
- Owner summaries sent after all batches complete
- Job log stored with `tenantDetails: null` and `tenantDetailsCount: N`

### 2. `RentPaymentTransactionRepository.java`

- Extended with `RentPaymentTransactionCustomRepository` (for batch aggregation)
- Added `findTenantIdsByRentMonth()` — projection query returning only `tenantId` field (lightweight idempotency check)

### 3. `RegistrationRepository.java`

- Added `Page<RegistrationDocument> findByOccupied(occupied, Pageable)` — paginated query
- Added `long countByOccupied(occupied)` — for progress logging

### 4. `SchedulerJobLog.java`

- Added `tenantDetailsCount` field (long) — stores count of tenant details in separate collection
- `tenantDetails` list set to `null` for large runs (was causing 16MB BSON limit)

### 5. `RegistrationServiceApplication.java`

- Removed duplicate `RestTemplate` bean (now defined in `SchedulerInfraConfig` with timeouts)

### 6. `application.properties`

Added configurable properties:

```properties
# RestTemplate timeouts
scheduler.resttemplate.connect-timeout-ms=5000
scheduler.resttemplate.read-timeout-ms=10000

# Notification thread pool
scheduler.notification.pool.core-size=20
scheduler.notification.pool.max-size=50
scheduler.notification.pool.queue-capacity=5000

# Scheduler thread pool
scheduler.pool.size=3

# Batch processing size
scheduler.batch.size=500
```

All values are overridable via environment variables (e.g., `SCHEDULER_BATCH_SIZE=1000`).

## Configuration Tuning Guide

| Tenants | `batch.size` | `notif.pool.core-size` | `notif.pool.max-size` | `notif.pool.queue-capacity` |
|---------|-------------|----------------------|---------------------|---------------------------|
| < 1,000 | 500 | 10 | 20 | 1000 |
| 1K–10K | 500 | 20 | 50 | 5000 |
| 10K–100K | 500 | 30 | 80 | 10000 |
| 100K+ | 1000 | 50 | 100 | 20000 |

> **Note:** Increasing the notification pool beyond 50 threads only helps if `notification-services` can handle that many concurrent requests. Monitor notification-services CPU/memory when tuning.

## Backward Compatibility

- The `processMonthlyRent(LocalDate)` and `processMonthlyRent(LocalDate, TriggerType, String)` method signatures are unchanged
- The REST endpoint `POST /rentpayments/scheduler/generate-monthly-rent` works exactly as before
- The `SchedulerJobLog` document retains all existing fields — `tenantDetails` is `null` instead of a list, `tenantDetailsCount` provides the count
- The scheduler logs endpoint `GET /rentpayments/scheduler/logs` returns the same structure
- Per-tenant details can be queried from the new `schedulerTenantDetails` collection using `jobId`

## MongoDB Indexes to Add (Production)

```javascript
// Pending balance aggregation performance
db.rentPaymentTransactions.createIndex({ "tenantId": 1, "status": 1 })

// Idempotency check
db.rentPaymentTransactions.createIndex({ "rentMonth": 1 })

// Tenant audit lookup
db.schedulerTenantDetails.createIndex({ "jobId": 1, "tenantId": 1 })
```

## Failure Handling

Every failure scenario is handled gracefully — no data is silently lost.

### Failure Matrix

| Failure Scenario | What Happens | Data Lost? |
|-----------------|-------------|-----------|
| **Email throws exception** | Caught inside `sendEmailAsync()` → returns `CompletableFuture(FAILED, errorMsg)`. SMS & WhatsApp still fire independently | No — status recorded as `FAILED` in audit |
| **SMS throws exception** | Same — caught, returns `FAILED`. Email & WhatsApp unaffected | No |
| **WhatsApp throws exception** | Same — caught, returns `FAILED` | No |
| **HTTP timeout (10s)** | `RestTemplate` throws `ResourceAccessException` → caught → `FAILED` | No |
| **notification-services is completely down** | All 3 channels return `FAILED` → `notificationsFailed` counter incremented. **Rent record already saved before notifications fire** | No — payment record exists, notifications marked failed in audit |
| **`.join()` fails unexpectedly** | Outer catch in `processBatch()` records `overallError` in audit + increments `notificationsFailed` | No |
| **Thread pool queue full (5000 tasks)** | `CallerRunsPolicy` kicks in — the scheduler thread runs the notification synchronously (slower but no data loss) | No — just runs slower |
| **Bulk insert fails** | Falls back to individual `save()` per record, each wrapped in try/catch. Failed tenants get `FAILED` audit entry | No |
| **MongoDB down mid-batch** | Top-level catch records `topLevelError` → `JobStatus.FAILURE` in the job log. Already-inserted records from previous batches are safe (idempotency check on re-run skips them) | Partial — resume-safe on re-run |
| **App crashes mid-run** | On restart + re-trigger, the idempotency check (`alreadyGenerated` set) skips all tenants that already have records. Only unprocessed tenants get new records | None — idempotent |

### Key Design Principle

**Payment record creation always completes before notifications fire.** Even if the entire notification infrastructure goes down, every tenant still gets their `PENDING` rent record. Notifications can be re-sent manually — rent records can't be duplicated.

### CallerRunsPolicy (Thread Pool Back-Pressure)

When the notification thread pool queue is full (all 5000 slots occupied), instead of throwing `TaskRejectedException` and losing the notification:

```
Queue full → CallerRunsPolicy activates
           → The scheduler thread itself runs the notification (synchronously)
           → This slows down the scheduler (natural back-pressure)
           → But no notifications are dropped
           → Once queue slots free up, async dispatch resumes
```

## Re-Run / Recovery After Failure

### How Re-Run Works

The scheduler is **idempotent by design**. When you trigger it for a month that was partially processed:

```
1. Load all existing RentPaymentTransaction records for the month
2. Build a Set<tenantId> of already-generated records
3. For each tenant in the batch:
   → if tenantId is in the Set → SKIP (already has a record)
   → if tenantId is NOT in the Set → CREATE new PENDING record
```

So re-running after a crash **only processes the tenants that were missed** — no duplicates.

### How to Trigger a Re-Run

**Option 1: Wait for next month's cron (NOT recommended for recovery)**

The cron `0 0 0 1 * *` fires on the 1st of the **next** month. This won't help recover a failed April run because it will process May.

**Option 2: Manual trigger via REST API (RECOMMENDED)**

```bash
# Re-run for a specific month (e.g., April 2026)
curl -X POST "http://localhost:8082/rentpayments/scheduler/generate-monthly-rent?month=2026-04-01" \
  -H "Authorization: Bearer <ADMIN_TOKEN>"
```

This calls `processMonthlyRent(2026-04-01, MANUAL, "MANUAL")` — the exact same code path as the cron, but for the month you specify. The idempotency check ensures already-processed tenants are skipped.

**Requires:** `ADMIN` or `MODERATOR` role.

### Recovery Workflow

```
1. Check the failure:
   GET /rentpayments/scheduler/logs
   → Find the failed job, note the jobId, status, recordsCreated vs totalActiveTenants

2. Check per-tenant details (what succeeded, what failed):
   Query MongoDB: db.schedulerTenantDetails.find({ jobId: "<jobId>" })

3. Fix the root cause (e.g., MongoDB back online, notification-services restarted)

4. Re-trigger:
   POST /rentpayments/scheduler/generate-monthly-rent?month=2026-04-01

5. Verify:
   GET /rentpayments/scheduler/logs
   → New job should show:
     recordsCreated = (remaining tenants)
     recordsSkipped = (previously processed tenants)
     status = SUCCESS
```

### What About Missed Notifications?

If records were created but notifications failed (notification-services was down):

- The rent records exist — tenants will see "Rent Due" when they open the app
- The `schedulerTenantDetails` collection shows exactly which tenants had `emailStatus: FAILED`, `smsStatus: FAILED`, etc.
- **Re-running the scheduler will NOT re-send notifications** for already-created records (they're skipped by idempotency)
- To re-notify, you would need a separate "resend notifications" endpoint (not yet implemented — this could be a future enhancement)
