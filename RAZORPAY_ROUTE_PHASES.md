# Razorpay Route — Payment Split Implementation

## Business Model

CoLive Connect acts as a **marketplace platform**. Tenants pay rent through the platform, and the payment is **automatically split**:
- **Platform fee** (₹49 flat) → retained by CoLive Connect
- **Owner amount** (rent – ₹49) → routed to landlord's primary bank account via Razorpay Route

### Entity Hierarchy

```
coliveUserName (Landlord)
 ├── coliveName-A (Property 1)
 │    ├── Bank Account 1  ★ PRIMARY
 │    ├── Bank Account 2
 │    └── Bank Account 3
 ├── coliveName-B (Property 2)
 │    ├── Bank Account 4  ★ PRIMARY
 │    └── Bank Account 5
 └── coliveName-C (Property 3)
      └── Bank Account 6  ★ PRIMARY (auto-set, only account)
```

Each `coliveName` can have **multiple bank accounts**, but only **one is PRIMARY** at any time. The moderator can switch the primary account from the dashboard. Rent payments are always routed to the **primary** account.

---

## Phase 1 — Foundation & Bank Account Management ✅

### Objective
Build the backend infrastructure for Razorpay Route, multi-bank-account support per property, moderator UI for managing bank accounts, and tenant-facing primary account display.

### Backend Changes (payment-services, port 8083)

#### Dependencies & Config
| Item | Details |
|------|---------|
| `spring-boot-starter-data-mongodb` | Added to pom.xml |
| MongoDB database | `payments` (same MongoDB instance as registration-services) |
| `razorpay.webhookSecret` | For verifying Razorpay webhook signatures |
| `razorpay.platformFee` | `4900` paise (₹49) |
| `razorpay.routeEnabled` | `true` — feature flag to disable route transfers |

#### MongoDB Documents

```
linked_accounts
├── ownerUsername (indexed)
├── coliveName
├── accountNumber
├── razorpayAccountId    ← Razorpay Route linked account ID
├── bankName, ifscCode, beneficiaryName, upiId
├── contactName, email, phone
├── businessType, legalBusinessName
├── primary (boolean)    ← only one true per owner+colive
├── status               ← CREATED | ACTIVE | SUSPENDED
└── createdAt, updatedAt
Unique Index: {ownerUsername, coliveName, accountNumber}

payment_transfers
├── razorpayPaymentId (indexed)
├── razorpayOrderId, razorpayTransferId
├── linkedAccountId, ownerUsername, coliveName
├── registrationId, tenantName
├── totalAmount, platformFee, ownerAmount (all paise)
├── status               ← CREATED | PROCESSED | SETTLED | FAILED
├── failureReason
└── createdAt, processedAt, settledAt

webhook_events
├── eventId (unique indexed)  ← idempotency key
├── eventType                 ← payment.captured, payment.failed, transfer.settled
├── razorpayPaymentId, razorpayOrderId
├── amount, currency, status
├── rawPayload               ← full JSON for audit
├── processed (boolean)
└── receivedAt, processedAt
```

#### REST API Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `POST` | `/payments/route/linked-accounts` | Add new bank account for owner+colive |
| `GET` | `/payments/route/linked-accounts?ownerUsername=X&coliveName=Y` | List all bank accounts for a property |
| `GET` | `/payments/route/linked-accounts/primary?ownerUsername=X&coliveName=Y` | Get the primary bank account |
| `PUT` | `/payments/route/linked-accounts/{id}/set-primary` | Switch primary account |
| `DELETE` | `/payments/route/linked-accounts/{id}` | Remove a bank account |
| `POST` | `/payments/webhook` | Razorpay webhook receiver |
| `GET` | `/payments/route/transfers?ownerUsername=X` | List transfers for an owner |
| `GET` | `/payments/route/transfers/{paymentId}` | Transfer details by payment ID |
| `GET` | `/payments/route/config` | Route config (enabled, platform fee) |

#### Services

| Service | Responsibility |
|---------|---------------|
| `LinkedAccountService` | CRUD for bank accounts, primary switching, auto-promote on delete, Razorpay onboarding |
| `RouteService` | Initiate transfer after payment.captured, deduct platform fee, settle tracking |
| `PaymentService` | Updated — `createOrder()` now includes `ownerUsername` + `coliveName` in Razorpay order notes |

#### Webhook Flow

```
Razorpay → POST /payments/webhook
  │
  ├── Verify X-Razorpay-Signature (HMAC-SHA256)
  ├── Idempotency check (eventId in webhook_events)
  ├── Save raw event for audit
  │
  ├── payment.captured → RouteService.initiateTransfer()
  │     ├── Find PRIMARY linked account for owner+colive
  │     ├── Calculate: ownerAmount = totalAmount - 4900 (platform fee)
  │     ├── Razorpay API: POST /v1/payments/{id}/transfers
  │     └── Save PaymentTransferDocument (status=PROCESSED)
  │
  ├── payment.failed → Log for audit (no transfer)
  │
  └── transfer.settled → Mark PaymentTransferDocument as SETTLED
```

### Frontend Changes

#### ModeratorApprovalDashboard — New Tabbed Layout

```
┌──────────────────────┬────────────────────┐
│ ✅ Payment Approvals │ 🏦 Bank Accounts   │
└──────────────────────┴────────────────────┘
```

**Bank Accounts Tab**:
- Property selector dropdown (required — selects coliveName)
- Toolbar with "Add Bank Account" button
- Add form: bank name, account number*, IFSC*, beneficiary name*, UPI ID, contact info
- Account cards showing: bank name, masked account number, IFSC, beneficiary, status dot
- Primary badge (green gradient) on the active primary account
- "★ Set as Primary" button on non-primary accounts
- "🗑 Remove" button with confirmation dialog (warns if removing primary)
- First account added auto-becomes primary
- Deleting primary auto-promotes next available account

#### User.js — Bank Details Display

- Fetches primary bank account from `GET /payments/route/linked-accounts/primary` first
- Falls back to existing `GET /rentpayments/owner/{}/bankdetails` if Route API has no data
- Works on both initial load and property switch (`handleCoLiveChange`)
- Payment order request now includes `ownerUsername` and `coliveName` for webhook routing

### Files Created/Modified

```
payment-services/
  src/main/java/com/elite4/anandan/paymentservices/
    document/
      LinkedAccountDocument.java        ← NEW (multi-bank, primary flag)
      PaymentTransferDocument.java      ← NEW
      WebhookEventDocument.java         ← NEW
    repository/
      LinkedAccountRepository.java      ← NEW
      PaymentTransferRepository.java    ← NEW
      WebhookEventRepository.java       ← NEW
    service/
      LinkedAccountService.java         ← NEW (CRUD, primary switch, onboarding)
      RouteService.java                 ← NEW (transfer initiation, settlement)
      PaymentService.java               ← MODIFIED (owner/colive in order notes)
    controller/
      RouteController.java              ← NEW (bank account + transfer endpoints)
      WebhookController.java            ← NEW (webhook receiver)
      PaymentController.java            ← MODIFIED (logging owner/colive)
    dto/
      LinkedAccountRequest.java         ← MODIFIED (bankName, upiId added)
      LinkedAccountResponse.java        ← MODIFIED (bank fields, primary flag)
      TransferRequest.java              ← NEW
      TransferResponse.java             ← NEW
      PaymentRequest.java               ← MODIFIED (ownerUsername, coliveName)
  src/main/resources/
    application.properties              ← MODIFIED (webhook, platformFee, MongoDB)
  pom.xml                               ← MODIFIED (MongoDB dep)

elite4UI-master/client/src/
  ModeratorApprovalDashboard.js         ← MODIFIED (tabs + bank account management)
  ModeratorApprovalDashboard.css        ← MODIFIED (tabs + bank account styles)
  User.js                               ← MODIFIED (Route API primary fetch, order params)
```

---

## Phase 2 — Live Razorpay Onboarding & KYC ✅

### Objective
Replace test/placeholder Razorpay account IDs with real Razorpay Route onboarding via the `/v2/accounts` API. Implement KYC verification, stakeholder management, and account activation workflows.

### Backend

#### Razorpay Route Account Creation (Real API)

```
POST https://api.razorpay.com/v2/accounts
{
  "email": "owner@example.com",
  "phone": "9876543210",
  "legal_business_name": "ABC Properties",
  "business_type": "individual",
  "contact_name": "John Doe",
  "profile": {
    "category": "housing",
    "subcategory": "pg_and_hostels",
    "addresses": { ... }
  },
  "legal_info": {
    "pan": "ABCDE1234F",
    "gst": "..."
  },
  "bank_account": {
    "ifsc_code": "HDFC0001234",
    "beneficiary_name": "John Doe",
    "account_number": "1234567890123"
  }
}
```

#### LinkedAccountService Changes

| Change | Details |
|--------|---------|
| Replace `acc_` placeholder | Real `POST /v2/accounts` call via Razorpay SDK |
| KYC status tracking | New field: `kycStatus` (NOT_STARTED, PENDING, VERIFIED, FAILED) |
| Stakeholder creation | `POST /v2/accounts/{id}/stakeholders` for owner identity verification |
| Product config | `POST /v2/accounts/{id}/products` to enable `route` product |
| Webhook for account | Listen for `account.activated` webhook events |

#### New Fields on LinkedAccountDocument

```java
private String kycStatus;           // NOT_SUBMITTED, SUBMITTED, VERIFIED, FAILED
private String productConfigStatus; // NEEDS_CLARIFICATION, UNDER_REVIEW, ACTIVATED
private String stakeholderId;       // Razorpay stakeholder ID
private String panNumber;           // PAN for KYC
private String gstNumber;           // Optional GST
private String businessAddress;     // Business address for Razorpay
private String activationStatus;    // NEW, ACTIVATED, SUSPENDED, UNDER_REVIEW, FUNDS_ON_HOLD
```

#### New Webhook Events

```
account.activated        → Update activationStatus=ACTIVATED, kycStatus=VERIFIED
account.suspended        → Update activationStatus=SUSPENDED
account.under_review     → Update activationStatus=UNDER_REVIEW, kycStatus=SUBMITTED
account.funds_on_hold    → Update activationStatus=FUNDS_ON_HOLD
```

#### New API Endpoints

| Method | Endpoint | Purpose |
|--------|----------|--------|
| `POST` | `/payments/route/linked-accounts/{id}/submit-kyc` | Submit PAN/GST/stakeholder for KYC |
| `GET` | `/payments/route/linked-accounts/{id}/kyc-status` | Check KYC verification status from Razorpay |

### Frontend — Moderator Dashboard (Bank Accounts Tab)

#### KYC Status Indicators

```
┌────────────────────────────────────────────────┐
│ HDFC Bank                    ★ PRIMARY         │
│ ••••7890  |  HDFC0001234  |  John Doe          │
│                                                 │
│ KYC: ✅ Verified     Razorpay: 🟢 ACTIVE       │
│                                                 │
│ [★ Set Primary]  [🗑 Remove]                    │
└────────────────────────────────────────────────┘

┌────────────────────────────────────────────────┐
│ SBI                                             │
│ ••••4567  |  SBIN0001234  |  John Doe           │
│                                                 │
│ KYC: ⏳ Pending      Razorpay: 🟡 UNDER REVIEW │
│ ⚠️ Complete KYC to receive payments              │
│                                                 │
│ [Submit KYC Documents]  [🗑 Remove]             │
└────────────────────────────────────────────────┘
```

#### KYC Submission Form

- PAN Number input with validation (AAAAA9999A format)
- GST Number input (optional)
- Business address fields
- Submit button → calls `/submit-kyc` endpoint
- Status polling or webhook-driven updates

### Environment Configuration

```properties
# Production Razorpay credentials
razorpay.keyId=${RAZORPAY_KEY_ID}
razorpay.keySecret=${RAZORPAY_KEY_SECRET}
razorpay.webhookSecret=${RAZORPAY_WEBHOOK_SECRET}

# Webhook URL (public-facing)
razorpay.webhookUrl=https://api.coliveconnect.com/payments/webhook
```

### Files Created/Modified

```
payment-services/
  src/main/java/com/elite4/anandan/paymentservices/
    document/
      LinkedAccountDocument.java        ← MODIFIED (kycStatus, activationStatus, panNumber, gstNumber, businessAddress, stakeholderId, productConfigStatus)
    dto/
      LinkedAccountRequest.java         ← MODIFIED (panNumber, gstNumber, businessAddress)
      LinkedAccountResponse.java        ← MODIFIED (kycStatus, productConfigStatus, activationStatus, panNumber, gstNumber)
      KycSubmissionRequest.java         ← NEW (PAN, GST, business address, type, legal name, stakeholder info)
    service/
      LinkedAccountService.java         ← MODIFIED (real Razorpay /v2/accounts API, createStakeholder, submitKyc, fetchKycStatus, configureProduct, handleAccountWebhook)
    controller/
      WebhookController.java            ← MODIFIED (account.activated/suspended/under_review/funds_on_hold handlers)
      RouteController.java              ← MODIFIED (submit-kyc, kyc-status endpoints)

elite4UI-master/client/src/
  ModeratorApprovalDashboard.js         ← MODIFIED (KYC badges, KYC form, refresh status)
  ModeratorApprovalDashboard.css        ← MODIFIED (KYC badge styles, form styles)
```

---

## Phase 3 — Reporting, Reconciliation & Advanced Features ✅

### Objective
Build comprehensive reporting for platform earnings, owner settlements, reconciliation tools, refund support, and notification flows for all payment events.

### Backend

#### 3.1 — Settlement Reporting Service

New service: `SettlementReportService`

```java
// Platform earnings report
PlatformEarningsReport getEarnings(LocalDate from, LocalDate to);
// → totalTransactions, totalPlatformFee, totalAmountProcessed

// Owner settlement report
OwnerSettlementReport getOwnerReport(String ownerUsername, LocalDate from, LocalDate to);
// → per-property breakdown, settled vs pending, failed transfers

// Colive-level report
ColiveSettlementReport getColiveReport(String ownerUsername, String coliveName, LocalDate from, LocalDate to);
// → per-tenant breakdown, monthly totals
```

#### New API Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `GET` | `/payments/route/reports/owner-settlements?ownerUsername=X&year=Y&month=M` | Owner/Mod: monthly settlement summary |
| `GET` | `/payments/route/reports/colive-settlements?ownerUsername=X&coliveName=Y&year=Y&month=M` | Per-property settlement details |
| `GET` | `/payments/route/reports/platform-earnings?year=Y&month=M` | Admin: platform fee earnings with owner breakdown |
| `GET` | `/payments/route/reports/reconciliation?ownerUsername=X` | Match settled/processed/failed transfers per owner |
| `POST` | `/payments/route/refunds` | Initiate refund (with optional transfer reversal) |
| `GET` | `/payments/route/refunds/{paymentId}` | Get refunds for a specific payment |
| `GET` | `/payments/route/refunds?ownerUsername=X` | Get all refunds for an owner |

#### 3.2 — Refund & Reversal Flow

```
Admin initiates refund (POST /payments/route/refunds)
  │
  ├── Razorpay API: POST /v1/payments/{id}/refund (with reverse_all=true if requested)
  ├── Save PaymentRefundDocument (status=INITIATED → PROCESSED/FAILED)
  ├── Update PaymentTransferDocument (status=REVERSED) if transfer reversal
  ├── Notify owner via NotificationClient: "Refund processed"
  └── Return RefundResponse with status
```

**New MongoDB Document — `payment_refunds`:**
```
payment_refunds
├── razorpayPaymentId (indexed)
├── razorpayRefundId
├── ownerUsername, coliveName, tenantName
├── originalAmount, refundAmount (paise)
├── reason
├── transferReversed (boolean)
├── status                ← INITIATED | PROCESSED | FAILED
└── createdAt
```

#### 3.3 — Reconciliation Engine

```java
// SettlementReportService.getReconciliationSummary(ownerUsername)
// Returns counts + amounts grouped by transfer status:
//   settled (count, amount)
//   processed (count, amount)  — awaiting settlement
//   failed (count, amount)
//   reversed (count, amount)   — refunded transfers
```

#### 3.4 — Notification Integration

| Event | Email | SMS | In-App |
|-------|-------|-----|--------|
| Payment captured | ✅ Tenant | ✅ Tenant | — |
| Transfer to owner | ✅ Owner | ✅ Owner | — |
| Transfer settled | ✅ Owner | — | — |
| Transfer failed | ✅ Owner + Admin | ✅ Owner | — |
| KYC approved | ✅ Owner | ✅ Owner | — |
| Primary account changed | ✅ Owner | — | — |
| Refund initiated | ✅ Tenant + Owner | ✅ Both | — |

### Frontend

#### 3.5 — Moderator Settlement Dashboard (New Tab)

```
┌──────────────────┬──────────────────┬────────────────────┐
│ ✅ Approvals     │ 🏦 Bank Accounts │ 📊 Settlements     │
└──────────────────┴──────────────────┴────────────────────┘

┌─────────────────────────────────────────────────────────┐
│  Monthly Settlement Summary — March 2026                │
│                                                          │
│  Total Collected: ₹2,45,000    Platform Fee: ₹4,900     │
│  Settled to Owner: ₹2,40,100   Pending: ₹0              │
│                                                          │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Property: Sunshine PG                            │    │
│  │ Tenants: 18  |  Collected: ₹1,62,000            │    │
│  │ Settled: ₹1,59,138  |  Platform: ₹882           │    │
│  └─────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Property: Green Valley                           │    │
│  │ Tenants: 10  |  Collected: ₹83,000              │    │
│  │ Settled: ₹80,962  |  Platform: ₹490             │    │
│  └─────────────────────────────────────────────────┘    │
│                                                          │
│  [📥 Download CSV]  [🔄 Reconcile]                      │
└─────────────────────────────────────────────────────────┘
```

#### 3.6 — Tenant Payment Receipt Enhancement

After successful payment, show:
```
┌─────────────────────────────────────────┐
│  ✅ Payment Successful!                 │
│                                          │
│  Amount Paid: ₹9,000                    │
│  Payment ID: pay_XYZ123                 │
│  Rent Month: April 2026                 │
│                                          │
│  Paid to: HDFC Bank ••••7890            │
│  Owner: John Properties                 │
│  Platform Fee: ₹49                      │
│  Owner Receives: ₹8,951                 │
│                                          │
│  [📥 Download Receipt]                  │
└─────────────────────────────────────────┘
```

#### 3.7 — Admin Panel (New Route: `/admin-payments`)

New component: `AdminPaymentDashboard` — 3-tab layout:

```
┌───────────────────┬──────────────┬───────────────────┐
│ 💰 Platform       │ ↩️ Refunds   │ 🔍 Reconciliation │
│    Earnings       │              │                   │
└───────────────────┴──────────────┴───────────────────┘
```

**Platform Earnings Tab:**
- Month/year picker
- Summary cards (total earnings, transactions processed, total volume)
- Owner breakdown table (owner, transactions, collected, platform fee)

**Refunds Tab:**
- Refund form: Payment ID, amount (0 = full), reason, reverse transfer checkbox
- Refund history table with status indicators

**Reconciliation Tab:**
- Owner username lookup
- Status grid: settled/processed/failed/reversed counts + amounts

### Files Created/Modified

```
payment-services/
  src/main/java/com/elite4/anandan/paymentservices/
    document/
      PaymentRefundDocument.java        ← NEW (refund record storage)
    repository/
      PaymentRefundRepository.java      ← NEW (refund queries)
      PaymentTransferRepository.java    ← MODIFIED (date-range + colive queries)
    dto/
      RefundRequest.java                ← NEW (paymentId, amount, reason, reverseTransfer)
      RefundResponse.java               ← NEW (refundId, status, transferReversed)
      SettlementReport.java             ← NEW (owner/colive settlement summary)
      PlatformEarningsReport.java       ← NEW (admin earnings + owner breakdown)
    service/
      SettlementReportService.java      ← NEW (owner/colive/platform reports, reconciliation)
      RefundService.java                ← NEW (Razorpay refund API, transfer reversal, notifications)
    controller/
      RouteController.java              ← MODIFIED (settlement, refund, reconciliation endpoints)

elite4UI-master/client/src/
  ModeratorApprovalDashboard.js         ← MODIFIED (Settlements tab with monthly reports)
  ModeratorApprovalDashboard.css        ← MODIFIED (Settlements tab styles)
  AdminPaymentDashboard.js              ← NEW (Admin 3-tab dashboard)
  AdminPaymentDashboard.css             ← NEW (Admin dashboard styles)
  App.js                                ← MODIFIED (added /admin-payments route)
  User.js                               ← MODIFIED (payment breakdown in receipt)
```

---

## Phase Summary

| Phase | Scope | Status |
|-------|-------|--------|
| **Phase 1** | Backend infrastructure, multi-bank CRUD, moderator UI, webhook handler, tenant primary fetch | ✅ Complete |
| **Phase 2** | Live Razorpay onboarding, KYC verification, account activation, webhook events for account lifecycle | ✅ Complete |
| **Phase 3** | Settlement reporting, reconciliation, refunds, notifications, admin panel, receipt enhancements | ✅ Complete |

## Architecture Diagram

```
                    ┌──────────────┐
                    │   Tenant UI  │
                    │  (User.js)   │
                    └──────┬───────┘
                           │ Pay Rent
                           ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────────┐
│  Moderator   │    │   Payment    │    │    Razorpay       │
│  Dashboard   │◄──►│   Service    │◄──►│    API            │
│ (Bank Mgmt)  │    │  (port 8083) │    │  (Orders/Route)   │
└──────────────┘    └──────┬───────┘    └────────┬─────────┘
                           │                      │
                           │                      │ Webhook
                           ▼                      ▼
                    ┌──────────────┐    ┌──────────────────┐
                    │   MongoDB    │    │  WebhookController│
                    │  (payments)  │◄───│  → RouteService   │
                    │              │    │  → Transfer split  │
                    └──────────────┘    └──────────────────┘
                                               │
                                               ▼
                                        ┌──────────────┐
                                        │  Owner Bank   │
                                        │  (PRIMARY)    │
                                        │  Rent - ₹49   │
                                        └──────────────┘
```
