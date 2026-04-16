# Elite4 CoLive Connect — Complete Project Knowledge Base

> **Purpose**: This document provides a complete understanding of the Elite4 CoLive Connect platform — architecture, APIs, data models, business flows, frontend components, deployment, and security. Any AI agent or developer reading this should be able to understand, modify, debug, or extend the system.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Architecture](#2-architecture)
3. [Tech Stack](#3-tech-stack)
4. [Backend: Registration Service (Port 8082)](#4-backend-registration-service-port-8082)
5. [Backend: Payment Service (Port 8083)](#5-backend-payment-service-port-8083)
6. [Backend: Notification Service (Port 8084)](#6-backend-notification-service-port-8084)
7. [Database Schema (MongoDB)](#7-database-schema-mongodb)
8. [DTOs & Request/Response Objects](#8-dtos--requestresponse-objects)
9. [Enums & Constants](#9-enums--constants)
10. [Security & Authentication](#10-security--authentication)
11. [Inter-Service Communication](#11-inter-service-communication)
12. [Scheduled Jobs](#12-scheduled-jobs)
13. [Frontend: React Application](#13-frontend-react-application)
14. [Frontend: Component Reference](#14-frontend-component-reference)
15. [Frontend: API Client & Auth Utilities](#15-frontend-api-client--auth-utilities)
16. [Key Business Flows (End-to-End)](#16-key-business-flows-end-to-end)
17. [File Storage](#17-file-storage)
18. [Docker & Deployment](#18-docker--deployment)
19. [Environment Variables Reference](#19-environment-variables-reference)
20. [Dependencies](#20-dependencies)
21. [Known Issues & Context](#21-known-issues--context)
22. [Razorpay Integration Fixes & Improvements](#22-razorpay-integration-fixes--improvements-applied)

---

## 1. Project Overview

**Elite4 CoLive Connect** is a microservices-based property and tenant management platform for co-living spaces (PGs, hostels, apartments, flats). It provides:

- **Property owner registration** with room definitions, bank details, KYC documents
- **Tenant onboarding** with document uploads, room assignment, advance/rent tracking
- **Room occupancy tracking** with real-time availability, gender counts
- **Rent payment processing** via Razorpay (online), cash, and bank transfer
- **Financial settlements** — automatic money routing to owner bank accounts with platform fee
- **Multi-channel notifications** — Email, SMS, WhatsApp, Telegram
- **Admin approval workflows** for new owners, tenants, and payments
- **Tenant transfer requests** between properties (2-step approval)
- **Monthly rent generation** via scheduled jobs with automated notifications

### Workspace Layout

```
elite4-main/                          ← Backend (Java/Spring Boot)
├── pom.xml                           ← Parent POM (multi-module Maven)
├── docker-compose.yml                ← Full stack deployment
├── registration-services/            ← Core service (port 8082)
│   ├── pom.xml
│   └── src/main/java/com/elite4/anandan/registrationservices/
│       ├── controller/
│       ├── service/
│       ├── repository/
│       ├── model/
│       ├── dto/
│       ├── security/
│       └── config/
├── payment-services/                 ← Razorpay integration (port 8083)
│   ├── pom.xml
│   └── src/main/java/com/elite4/anandan/paymentservices/
│       ├── controller/
│       ├── service/
│       ├── repository/
│       ├── model/
│       ├── dto/
│       └── config/
└── notification-services/            ← Multi-channel notifications (port 8084)
    ├── pom.xml
    └── src/main/java/com/elite4/anandan/notificationservices/

elite4UI-main/                        ← Frontend (React 18)
├── server.js                         ← Express proxy (port 3000)
├── package.json
└── client/
    ├── package.json
    ├── public/
    ├── build/                        ← Production bundle
    └── src/
        ├── App.js                    ← Router + auth setup
        ├── Login.js, Signup.js, Registration.js, ...
        ├── Dashboard.js, Admin.js, User.js, ...
        ├── Payment.js, AdminPaymentDashboard.js, ...
        └── utils/apiClient.js        ← Auth interceptor
```

---

## 2. Architecture

```
┌───────────────────────────────────────────────────────────────┐
│                    Frontend (React 18.2)                       │
│              Express proxy @ :3000 / Nginx @ :80               │
└──────────┬──────────────┬──────────────┬──────────────────────┘
           │              │              │
    /adminservices   /api/payments   /api/notifications
    /registrations    pathRewrite     pathRewrite
    /rentpayments     → /payments     → /notifications
           │              │              │
           ▼              ▼              ▼
   ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
   │ Registration  │ │   Payment    │ │ Notification │
   │   Service     │ │   Service    │ │   Service    │
   │   :8082       │ │   :8083      │ │   :8084      │
   │              │ │              │ │              │
   │ • Auth (JWT) │ │ • Razorpay   │ │ • Email      │
   │ • Users      │ │ • KYC        │ │ • SMS        │
   │ • Tenants    │ │ • Transfers  │ │ • WhatsApp   │
   │ • Rooms      │ │ • Refunds    │ │ • Telegram   │
   │ • Payments   │ │ • Settlements│ │              │
   │ • Transfers  │ │ • Webhooks   │ │              │
   │ • Files      │ │              │ │              │
   └──────┬───────┘ └──────┬───────┘ └──────────────┘
          │                │                │
          ▼                ▼                ▼
   ┌──────────────────────────────────────────┐
   │              MongoDB 7                    │
   │   Database: admin (registration data)     │
   │   Database: payments (payment data)       │
   └──────────────────────────────────────────┘
```

### Service Communication:
- **Registration → Notification**: RestTemplate (send emails/sms/whatsapp/telegram)
- **Registration → Payment**: RestTemplate (create linked accounts, submit KYC, upload documents)
- **Payment → Notification**: RestTemplate (payment confirmations)
- **Frontend → Backend**: fetch() via Express proxy

---

## 3. Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| **Backend** | Java | 17 |
| **Framework** | Spring Boot | 3.3.0 |
| **Database** | MongoDB | 7 |
| **Auth** | JWT (JJWT) | 0.11.5 |
| **Payments** | Razorpay Java SDK | 1.4.6 |
| **SMS** | Twilio | 8.31.1 |
| **Email** | JavaMail + Thymeleaf | Spring Boot Starter |
| **Phone Validation** | libphonenumber | 8.13.40 |
| **Cloud Storage** | AWS S3 SDK / Azure Blob | 2.20.100 / 12.20.2 |
| **Frontend** | React | 18.2.0 |
| **Routing** | React Router DOM | 6.8.0 |
| **Build** | Create React App | 5.0.1 |
| **Proxy** | Express + http-proxy-middleware | 4.18.2 / 2.0.6 |
| **Containerization** | Docker + Docker Compose | Multi-stage |

---

## 4. Backend: Registration Service (Port 8082)

**Base package**: `com.elite4.anandan.registrationservices`

### 4.1 Controllers

#### UserController — `@RequestMapping("/adminservices")`

| Method | Path | Request | Response | Auth |
|--------|------|---------|----------|------|
| POST | `/signup-with-files` | `UserCreateRequest` + multipart (userAadhar, propertyPhotos[], licenseDocuments[], kycDocuments[]) | `User` | Public |
| POST | `/login` | `LoginRequest` | `JwtResponse` | Public |
| PUT | `/change-password` | `ChangePasswordRequest` | JSON | All roles |
| GET | `/admin/pending-approvals` | — | `List<User>` | ADMIN |
| GET | `/admin/active-users` | — | `List<User>` | ADMIN |
| GET | `/admin/user/{identifier}` | — | `User` | ADMIN, MODERATOR |
| GET | `/admin/search?term=` | query param | `List<User>` | ADMIN |
| POST | `/admin/approve/{username}` | — | JSON | ADMIN |
| POST | `/admin/reject/{username}` | — | JSON | ADMIN |
| GET | `/user/{username}/coLive/{coliveName}/documents` | — | `List<FileDetails>` | ADMIN, MODERATOR |
| GET | `/user/{username}/coLive/{coliveName}/photos` | — | `List<FileDetails>` | ADMIN, MODERATOR |
| GET | `/download-user-aadhar/{userId}` | — | byte[] (image) | ADMIN, MODERATOR |

#### RegistrationController — `@RequestMapping("/registrations")`

| Method | Path | Request | Response | Auth |
|--------|------|---------|----------|------|
| POST | `/onboardUser` | `Registration` + `RoomForRegistration` + files | `RegistrationWithRoomRequest` | USER, ADMIN, MODERATOR |
| PUT | `/checkout` | `UpdateUserForCheckOut` | `RegistrationWithRoomRequest` | ADMIN, MODERATOR |
| PUT | `/checkoutAllMembers` | `Set<UpdateUserForCheckOut>` | String | ADMIN, MODERATOR |
| POST | `/usersForClient` | `GetUserInfoByClient` | `List<RegistrationWithRoomRequest>` | ALL |
| GET | `/user/{id}` | — | `RegistrationWithRoomRequest` | ALL |
| GET | `/user/email/{email}` | — | `RegistrationWithRoomRequest` | ALL |
| GET | `/user/contact/{contactNo}` | — | `RegistrationWithRoomRequest` | ALL |
| GET | `/user/room/{roomNumber}?coliveUserName=&coliveName=` | — | `List<RegistrationWithRoomRequest>` | ADMIN, MODERATOR |
| GET | `/user/house/{houseNumber}?coliveUserName=&coliveName=` | — | `List<RegistrationWithRoomRequest>` | ADMIN, MODERATOR |
| GET | `/user/getVacatedRoomsAndMembers?coliveUserName=&coliveName=` | — | `List<RegistrationWithRoomRequest>` | ADMIN, MODERATOR |
| GET | `/gender/{gender}` | — | `List<RegistrationWithRoomRequest>` | ADMIN, MODERATOR |

#### TransferController — `@RequestMapping("/registrations/transfer")`

| Method | Path | Request | Response | Auth |
|--------|------|---------|----------|------|
| POST | `/request` | `TransferRequestDTO` | `TransferRequestDocument` | ADMIN, MODERATOR, USER |
| GET | `/pending` | — | `List<TransferRequestDocument>` | ADMIN, MODERATOR |
| GET | `/pending/{coliveUserName}?coliveName=` | — | `List<TransferRequestDocument>` | ADMIN, MODERATOR |
| GET | `/all/{coliveUserName}` | — | `List<TransferRequestDocument>` | ADMIN, MODERATOR |
| GET | `/{id}` | — | `TransferRequestDocument` | ALL |
| GET | `/status/tenant/{registrationId}` | — | `TransferRequestDocument` or 204 | ALL |
| PUT | `/{id}/approve` | `TransferApprovalDTO` (optional) | `TransferRequestDocument` | ADMIN, MODERATOR |
| PUT | `/{id}/reject` | `TransferRejectDTO` (optional) | `TransferRequestDocument` | ADMIN, MODERATOR |

#### RentPaymentController — `@RequestMapping("/rentpayments")`

| Method | Path | Request | Response | Auth |
|--------|------|---------|----------|------|
| POST | `/cash` | `CashPaymentRequest` | `PaymentTransactionResponse` | ADMIN, MODERATOR, USER |
| POST | `/online` | `OnlinePaymentRequest` | `PaymentTransactionResponse` | ADMIN, MODERATOR, USER |
| GET | `/tenant/{tenantId}/history` | — | `List<TenantPaymentHistoryItem>` | ALL |
| GET | `/tenant/{tenantId}/payment-summary` | — | `TenantPaymentSummary` | ALL |
| GET | `/owner/{username}/dashboard?month=` | — | `OwnerPaymentDashboard` | ADMIN, MODERATOR |
| GET | `/owner/{username}/monthly-collection-status?month=&coliveName=` | — | Collection report | ADMIN, MODERATOR |
| GET | `/owner/{username}/pending-approvals?coliveName=` | — | Pending list | ADMIN, MODERATOR |
| PUT | `/approve-payment/{transactionId}` | — | Approved transaction | ADMIN, MODERATOR |
| PUT | `/reject-payment/{transactionId}` | reason | Rejected transaction | ADMIN, MODERATOR |

#### KycController — `@RequestMapping("/adminservices/kyc")`

| Method | Path | Request | Response | Auth |
|--------|------|---------|----------|------|
| GET | `/linked-accounts?ownerUsername=&coliveName=` | — | `List<LinkedAccountDocument>` | ADMIN, MODERATOR |
| GET | `/document-types` | — | List of doc types | ADMIN, MODERATOR |
| POST | `/linked-accounts/{accountId}/upload-document` | multipart (documentType, file) | JSON | ADMIN, MODERATOR |

### 4.2 Services

| Service | Key Methods | Purpose |
|---------|-------------|---------|
| **AuthService** | `login(LoginRequest)`, `changePassword(Auth, ChangePasswordRequest)` | JWT auth, password management |
| **UserCreationService** | `createUser(UserCreateRequest, files...)`, `addClientToUser(username, colive, files...)` | User account creation with file uploads |
| **AdminService** | `getPendingApprovals()`, `getActiveUsers()`, `approveUser(username)`, `getUserWithWildCard(term)`, `getAllCoLives()` | Admin dashboard operations |
| **RegistrationService** | `create(Registration, Room)`, `createWithFiles(...)`, `findByEmail()`, `findByContactNo()`, `findByColiveNameAndColiveUserName()`, `updateCheckOutDateByID()`, `checkoutAll()` | Tenant CRUD, checkout |
| **RoomAvailabilityService** | `calculateRoomAvailability(coliveName, coliveUserName, roomNumber, capacity)`, `calculateHouseAvailability(...)` | Real-time occupancy calculation, gender counts |
| **RentPaymentService** | `recordCashPayment()`, `recordOnlinePayment()`, `getTenantPaymentHistory()`, `getTenantPaymentSummary()`, `getOwnerPaymentDashboard()`, `approveCashPayment()`, `rejectCashPayment()` | Payment recording, history, dashboards |
| **RentSchedulerService** | `generateMonthlyRentRecords()` (cron), `processMonthlyRent(date)` (manual) | Monthly rent generation + notifications |
| **TransferService** | `createTransferRequest()`, `approveTransfer()` (2-step), `rejectTransfer()`, `getPendingTransfers*()` | Inter-property transfer workflow |
| **NotificationClient** | `sendEmail()`, `sendSms()`, `sendWhatsapp()`, `sendTelegram()` | HTTP calls to notification-services |
| **PaymentRouteClient** | `initiateTransfer()`, `getSettlementReport()` | HTTP calls to payment-services |
| **FileStorageService** | `storeFile(bytes, filename)`, `getFile(path)`, `deleteFile(path)` | Abstract interface — LOCAL/S3/AZURE implementations |

### 4.3 Repositories

**RegistrationRepository** (extends `MongoRepository<RegistrationDocument, String>`):
```
findByEmail, findByEmailAndOccupied, findByContactNo, findAllByContactNo,
findByContactNoAndOccupied, findByColiveNameAndColiveUserName,
findAllByColiveUserNameAndColiveNameAndRoomForRegistrationRoomType,
findAllByColiveUserNameAndColiveNameAndRoomForRegistrationHouseType,
findAllByGender, findByfname, findBylname, findBymname, findByOccupied,
findByOccupiedAndColiveUserNameAndColiveName,
findByColiveNameAndColiveUserNameAndRoomForRegistrationRoomNumberAndOccupied,
findByColiveUserNameAndColiveNameAndRoomForRegistrationHouseNumberAndOccupied
```

**UserRepository** (extends `MongoRepository<User, String>`):
```
findByPhoneE164, findByPhoneRaw, findByUsername, findByclientDetailsColiveName,
findAllByclientDetailsColiveName, findByEmail,
findByUsernameContainingIgnoreCaseAndActiveTrue,
findByEmailContainingIgnoreCaseAndActiveTrue,
findByPhoneE164ContainingIgnoreCaseAndActiveTrue
```

**RentPaymentTransactionRepository** (extends `MongoRepository<RentPaymentTransaction, String>`):
```
findByTenantId, findByTenantIdAndRentMonth, findByTenantIdAndRentMonthIsBetween,
findByColiveOwnerUsername, findByColiveNameAndRentMonth,
findByColiveOwnerUsernameAndRentMonth, findByColiveOwnerUsernameAndRentMonthIsBetween,
findByStatus, findByRazorpayOrderId, findByRazorpayPaymentId,
findByRoomIdAndRentMonth, countByColiveOwnerUsernameAndRentMonthAndStatus,
findByColiveOwnerUsernameAndApprovalStatus, findByRentMonth
@Query findOverduePayments(LocalDate today) — custom query for past due
```

**TransferRequestRepository** (extends `MongoRepository<TransferRequestDocument, String>`):
```
findByStatus, findByFromColiveUserName, findByToColiveUserName,
findByTenantRegistrationId, findByFromColiveUserNameOrToColiveUserName,
findByStatusAndFromColiveUserName, findByStatusAndToColiveUserName, findByStatusIn,
findByStatusAndFromColiveUserNameAndFromColiveName,
findByStatusAndToColiveUserNameAndToColiveName
```

**Other**: `RoleRepository`, `RoomsOrHouseRepository`, `SchedulerJobLogRepository`

---

## 5. Backend: Payment Service (Port 8083)

**Base package**: `com.elite4.anandan.paymentservices`

### 5.1 Controllers

#### PaymentController — `@RequestMapping("/payments")`

| Method | Path | Request | Response | Auth |
|--------|------|---------|----------|------|
| GET | `/config` | — | `{configured, keyId, companyName}` | None |
| POST | `/order` | `PaymentRequest` | `PaymentResponse` (orderId, amount, keyId) | None |
| POST | `/verify` | `PaymentRequest` (orderId, paymentId, signature) | `PaymentResponse` (verified=true) | None |

#### WebhookController — `@RequestMapping("/payments")`

| Method | Path | Request | Response | Auth |
|--------|------|---------|----------|------|
| POST | `/webhook` | Raw JSON + `X-Razorpay-Signature` header | `{status: "ok"}` | HMAC-SHA256 |

#### RouteController — `@RequestMapping("/payments/route")`

| Method | Path | Request | Response | Auth |
|--------|------|---------|----------|------|
| POST | `/linked-accounts` | `LinkedAccountRequest` | `LinkedAccountResponse` | None |
| GET | `/linked-accounts?ownerUsername=&coliveName=` | — | `List<LinkedAccountDocument>` | None |
| GET | `/linked-accounts/by-owner?ownerUsername=&coliveName=` | — | `List<LinkedAccountDocument>` | None |
| GET | `/linked-accounts/primary?ownerUsername=&coliveName=` | — | `{found, account}` | None |
| PUT | `/linked-accounts/{accountId}/set-primary` | — | `LinkedAccountResponse` | None |
| DELETE | `/linked-accounts/{accountId}` | — | JSON | None |
| POST | `/linked-accounts/{accountId}/submit-kyc` | `KycSubmissionRequest` | `LinkedAccountResponse` | None |
| GET | `/linked-accounts/{accountId}/kyc-status` | — | `LinkedAccountResponse` | None |
| POST | `/linked-accounts/{accountId}/upload-document` | multipart (documentType, file) | JSON | None |
| GET | `/linked-accounts/document-types` | — | `List<String>` | None |
| POST | `/linked-accounts/retry-sync` | — | `{message, syncedCount, remainingUnsynced}` | None |
| GET | `/linked-accounts/unsynced` | — | `{count, accounts}` | None |
| GET | `/transfers?ownerUsername=` | — | `List<PaymentTransferDocument>` | None |
| GET | `/transfers/{paymentId}` | — | `PaymentTransferDocument` | None |
| GET | `/reports/owner-settlements?ownerUsername=&year=&month=` | — | `SettlementReport` | None |
| GET | `/reports/colive-settlements?ownerUsername=&coliveName=&year=&month=` | — | `SettlementReport` | None |
| GET | `/reports/platform-earnings?year=&month=` | — | `PlatformEarningsReport` | None |
| GET | `/reports/reconciliation?ownerUsername=` | — | Reconciliation data | None |
| POST | `/refunds` | `RefundRequest` | `RefundResponse` | None |
| GET | `/refunds/{paymentId}` | — | `List<PaymentRefundDocument>` | None |
| GET | `/refunds?ownerUsername=` | — | `List<PaymentRefundDocument>` | None |
| GET | `/config` | — | `{routeEnabled, platformFee, platformFeeDisplay}` | None |

### 5.2 Services

| Service | Key Methods | Purpose |
|---------|-------------|---------|
| **PaymentService** | `createOrder(PaymentRequest)`, `verifySignature(orderId, paymentId, signature)`, `isConfigured()`, `getKeyId()` | Razorpay order creation + signature verification |
| **LinkedAccountService** | `createLinkedAccount()`, `getAccountsByOwner()`, `getPrimaryAccount()`, `setPrimary()`, `deleteAccount()`, `submitKyc()`, `fetchKycStatus()`, `uploadDocument()`, `retryUnsyncedAccounts()`, `getUnsyncedAccounts()`, `handleAccountWebhook()` | Bank account management via Razorpay /v2/accounts API. Includes sync tracking (`razorpaySynced` flag) and automatic retry of failed Razorpay API calls. |
| **RouteService** | `initiateTransfer(paymentId, orderId, amount, ...)`, `getTransferStatus()`, `bulkSettle()` | Money routing to owner accounts, platform fee deduction |
| **RefundService** | `initiateRefund(RefundRequest)`, `getRefundStatus()` | Razorpay refund processing |
| **SettlementReportService** | `generateOwnerSettlementReport()`, `generatePlatformEarningsReport()`, `getMonthlyBreakdown()` | Financial reporting |

### 5.3 Repositories

- **LinkedAccountRepository**: `findByOwnerUsernameAndColiveName`, `findByOwnerUsernameAndColiveNameAndPrimaryTrue`, `findByRazorpayAccountId`, `findByOwnerUsername`, `findByRazorpaySyncedFalse`
- **PaymentTransferRepository**: `findByRazorpayPaymentId`, `findByRazorpayTransferId`, `findByOwnerUsernameAndStatus`, `findByOwnerUsernameAndColiveNameAndCreatedAtBetween`
- **PaymentRefundRepository**: Basic CRUD, indexed by `razorpayPaymentId`
- **WebhookEventRepository**: `findByEventId`, `existsByEventId` (idempotency)

---

## 6. Backend: Notification Service (Port 8084)

**Base package**: `com.elite4.anandan.notificationservices`

### 6.1 Controller — `@RequestMapping("/notifications")`

| Method | Path | Request | Response |
|--------|------|---------|----------|
| POST | `/email` | `NotificationRequest` | 202 Accepted |
| POST | `/sms` | `NotificationRequest` | 202 Accepted |
| POST | `/whatsapp` | `NotificationRequest` | 202 Accepted |
| POST | `/telegram` | `NotificationRequest` | 202 Accepted |

### 6.2 Service

**NotificationService** — All methods are `@Async` + `@Retryable(maxAttempts=3, backoff=2s)`:

| Method | Channel | Library |
|--------|---------|---------|
| `sendEmail(NotificationRequest)` | Email | JavaMailSender + Thymeleaf templates |
| `sendSms(NotificationRequest)` | SMS | Twilio SDK |
| `sendWhatsapp(NotificationRequest)` | WhatsApp | Meta Graph API v19.0 |
| `sendTelegram(NotificationRequest)` | Telegram | Bot API via HTTP |

**NotificationRequest DTO**:
```
email: String
phoneNumber: String
chatId: String (Telegram)
subject: String
message: String
templateName: String (optional — Thymeleaf template)
variables: Map<String, Object> (template variables)
```

> **Note**: Fire-and-forget pattern. No persistent storage. No MongoDB collections in this service.

---

## 7. Database Schema (MongoDB)

### 7.1 Registration Service Collections (Database: `admin`)

#### `users` Collection
```
_id: ObjectId
username: String (unique)
email: String (unique, sparse)
phoneE164: String (unique, sparse) — E.164 format
phoneRaw: String
passwordHash: String (BCrypt)
roleIds: Set<String> — ["ROLE_MODERATOR", "ROLE_USER", etc.]
clientDetails: Set<ClientAndRoomOnBoardId> — embedded property details
aadharPhotoPath: String
active: Boolean (default: false, set true on admin approval)
forcePasswordChange: Boolean (default: false)
createdAt: Instant
updatedAt: Instant
lastLoginAt: Instant
ownerOfClient: String
rooms: Set<String>
```

#### `registrations` Collection
```
_id: ObjectId
fname, lname, mname: String
email: String
contactNo: String
gender: Enum (MALE, FEMALE, OTHER)
address: String
pincode: String
aadharPhotoPath: String
documentUploadPath: String
documentType: Enum (AADHAR, PAN, PASSPORT, VOTER_ID, LICENSE, etc.)
documentNumber: String
checkInDate: Date
checkOutDate: Date (null = currently active)
coliveName: String
coliveUserName: String
occupied: Enum (OCCUPIED, NOT_OCCUPIED, PARTIALLY_OCCUPIED)
roomForRegistration: {
  roomId: String
  roomNumber: String
  roomType: Enum (SINGLE, DOUBLE, TRIPLE, FOUR)
  roomCapacity: Integer
  houseNumber: String
  houseType: Enum (ONE_RK, ONE_BHK, TWO_BHK, THREE_BHK)
  occupied: String
}
parentName: String
parentContactNo: String
advanceAmount: Double
roomRent: Double
active: Boolean
transferStatus: String
entireRoomOccupied: Boolean

Indexes:
  - Compound unique (email + occupied) with partial filter {occupied: "OCCUPIED"}
  - Compound unique (contactNo + occupied) with partial filter {occupied: "OCCUPIED"}
  - Index on coliveName + coliveUserName
  - Index on checkOutDate
```

#### `roomOnBoard` Collection
```
_id: ObjectId
rooms: Set<Room> — [
  {
    roomType: Enum (SINGLE, DOUBLE, TRIPLE, FOUR)
    roomNumber: String
    roomCapacity: Integer
    houseNumber: String
    houseType: Enum (ONE_RK, ONE_BHK, TWO_BHK, THREE_BHK)
    occupied: String (OCCUPIED, NOT_OCCUPIED, PARTIALLY_OCCUPIED)
  }
]
```

#### `rentPaymentTransactions` Collection
```
_id: ObjectId
tenantId, tenantName, tenantEmail, tenantPhone: String
coliveName, coliveOwnerUsername, coliveOwnerEmail: String
roomId, roomNumber, propertyAddress: String
paymentType: Enum (ONLINE, CASH)
rentAmount, advanceAmount, paidAmount, remainingAmount: Double
rentMonth, dueDate, paidDate: LocalDate
status: Enum (PENDING, COMPLETED, PARTIAL, OVERDUE, CANCELLED, FAILED, PENDING_APPROVAL)
paymentMethod: String (UPI, CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, CASH)
razorpayOrderId, razorpayPaymentId, razorpaySignature: String
receiptNumber, remarks, rejectionReason: String
createdAt, updatedAt: LocalDateTime
createdBy, updatedBy: String
approvalStatus: Enum (PENDING_APPROVAL, APPROVED, REJECTED, NOT_REQUIRED)
approvedBy: String
approvedAt, collectionDateTime: LocalDateTime
approvalRemarks, moderatorUsername, collectionDetails: String
isProratedPayment: Boolean
proratedStartDate, proratedEndDate: LocalDate
proratedDaysCount: Integer
proratedAmount: Double
nextBillingCycleStart: LocalDate
previousTransactionId: String

Indexes:
  - tenantId
  - coliveName + coliveOwnerUsername
  - rentMonth
  - razorpayOrderId, razorpayPaymentId
```

#### `transferRequests` Collection
```
_id: ObjectId
tenantRegistrationId, tenantName, tenantContactNo, tenantEmail: String
fromColiveUserName, fromColiveName, fromRoomNumber, fromHouseNumber: String
toColiveUserName, toColiveName, toRoomNumber, toHouseNumber: String
requestedBy, requestedByRole: String
requestDate: Date
status: Enum (PENDING_SOURCE_APPROVAL, PENDING_DESTINATION_APPROVAL, COMPLETED, REJECTED)
sourceApprovedBy: String, sourceApprovalDate: Date
destinationApprovedBy: String, destinationApprovalDate: Date
rejectedBy, rejectionReason: String, rejectionDate: Date
newRegistrationId: String
completedDate: Date

Indexes:
  - tenantRegistrationId
  - status
```

#### `roles` Collection
```
_id: ObjectId
name: Enum (ROLE_USER, ROLE_MODERATOR, ROLE_GUEST, ROLE_ADMIN)
```

#### `schedulerJobLogs` Collection
```
_id: ObjectId
jobId, jobName: String (indexed)
rentMonth: LocalDate (indexed)
triggerType: Enum (SCHEDULED, MANUAL)
triggeredBy: String
startedAt, completedAt: LocalDateTime
durationMs: Long
status: Enum (SUCCESS, PARTIAL_FAILURE, FAILURE) (indexed)
errorMessage: String
totalActiveTenants, recordsCreated, recordsSkipped, recordsFailed: Integer
notificationsSent, notificationsFailed, ownersSummaryNotified: Integer
coliveDetails: List<ColiveJobDetail>
tenantDetails: List<TenantJobDetail>
```

### 7.2 Payment Service Collections (Database: `payments` or `admin`)

#### `linked_accounts` Collection
```
_id: ObjectId
ownerUsername: String (indexed)
coliveName: String
razorpayAccountId: String
contactName, email, phone: String
businessType, legalBusinessName: String
bankName, ifscCode, accountNumber, beneficiaryName, upiId: String
kycStatus: String (NOT_SUBMITTED, SUBMITTED, VERIFIED, FAILED)
productConfigStatus: String (NEEDS_CLARIFICATION, UNDER_REVIEW, ACTIVE, SUSPENDED)
stakeholderId, panNumber, gstNumber, businessAddress: String
activationStatus: String (NEW, ACTIVATED, SUSPENDED, UNDER_REVIEW, PENDING_SYNC, FUNDS_ON_HOLD)
uploadedDocuments: List<UploadedDocument>
razorpaySynced: Boolean                     ← NEW: true = real Razorpay account, false = pending retry
syncFailureReason: String                   ← NEW: error message if Razorpay API call failed
primary: Boolean
status: String (CREATED, ACTIVE, SUSPENDED, PENDING_SYNC)
createdAt, updatedAt: LocalDateTime

Index: Compound unique on (ownerUsername, coliveName, accountNumber)
```

#### `payment_transfers` Collection
```
_id: ObjectId
razorpayPaymentId: String (indexed)
razorpayOrderId, razorpayTransferId: String
linkedAccountId, ownerUsername, coliveName: String
registrationId, tenantName: String
totalAmount, platformFee, ownerAmount: Integer (paise)
status: String (CREATED, PROCESSED, SETTLED, FAILED)
failureReason: String
createdAt, processedAt, settledAt: LocalDateTime
```

#### `payment_refunds` Collection
```
_id: ObjectId
razorpayPaymentId: String (indexed)
razorpayRefundId, razorpayOrderId: String
ownerUsername, coliveName, tenantName, registrationId: String
originalAmount, refundAmount: Integer (paise)
reason: String
transferReversed: Boolean
razorpayReversalId: String
status: String (INITIATED, PROCESSED, FAILED)
failureReason: String
createdAt, processedAt: LocalDateTime
```

#### `webhook_events` Collection
```
_id: ObjectId
eventId: String (indexed — idempotency key)
eventType: String
payload: String (raw JSON)
status: String
receivedAt: LocalDateTime
```

---

## 8. DTOs & Request/Response Objects

### Registration & Auth

| DTO | Key Fields |
|-----|------------|
| `LoginRequest` | email, phoneNumber, password |
| `JwtResponse` | token, tokenType ("Bearer"), userId, username, email, forcePasswordChange, expiresIn |
| `UserCreateRequest` | username, password, email, phoneNumber, roleIds, coliveDetails, active, aadharPhotoPath |
| `ChangePasswordRequest` | currentPassword, newPassword |
| `Registration` | regId, fname, lname, mname, email, contactNo, gender, address, pincode, documentType, documentNumber, coliveName, coliveUserName, checkInDate, checkOutDate, advanceAmount, roomRent, parentName, parentContactNo, roomOccupied |
| `RegistrationWithRoomRequest` | registration + roomForRegistration |
| `RoomForRegistration` | roomId, roomNumber, roomType, roomCapacity, houseNumber, houseType, occupied |
| `Room` | roomType, roomNumber, roomCapacity, houseNumber, houseType, occupied |
| `ClientAndRoomOnBoardId` | coliveName, roomOnBoardId, bankDetailsList, licenseDocumentsPath, uploadedPhotos, categoryType |
| `BankDetails` | bankName, ifscCode, accountNumber, beneficiaryName |
| `ColiveNameAndRooms` | coliveName, roomIds (Set) |
| `UpdateUserForCheckOut` | id, checkOutDate |
| `GetUserInfoByClient` | coliveUserName, coliveName |
| `FileDetails` | fileName, filePath, fileContent (byte[]), contentType |

### Rent Payments

| DTO | Key Fields |
|-----|------------|
| `CashPaymentRequest` | tenantId, coliveName, rentMonth, amount, receiptNumber, cashReceivedDate, remarks, advanceAmount |
| `OnlinePaymentRequest` | tenantId, coliveName, rentMonth, amount, paymentMethod, remarks, advanceAmount, razorpayOrderId, razorpayPaymentId, razorpaySignature |
| `PaymentTransactionResponse` | transactionId, tenantName, tenantEmail, coliveName, paymentType, amount, status, createdAt |
| `TenantPaymentHistoryItem` | rentMonth, amount, status, paymentType, paymentDate, pending |
| `TenantPaymentSummary` | currentMonth, status, isFirstTime, dueDate, outstandingBalance, proratedInfo, lastSixMonths |
| `OwnerPaymentDashboard` | coliveName, currentMonth, totalCollected, totalPending, tenantsWithStatus |

### Transfers

| DTO | Key Fields |
|-----|------------|
| `TransferRequestDTO` | tenantRegistrationId, toColiveUserName, toColiveName, toRoomNumber, toHouseNumber |
| `TransferApprovalDTO` | approverComments |
| `TransferRejectDTO` | rejectionReason |

### Razorpay (Payment Service)

| DTO | Key Fields |
|-----|------------|
| `PaymentRequest` | amount, currency, email, phoneNumber, receipt, registrationId, tenantName, paymentFor, description, ownerUsername, coliveName, razorpayOrderId, razorpayPaymentId, razorpaySignature, telegramChatId |
| `PaymentResponse` | orderId, amount, currency, status, keyId, companyName, receipt, paymentId, verified, message |
| `LinkedAccountRequest` | ownerUsername, coliveName, contactName, email, phone, businessType, legalBusinessName, bankName, ifscCode, accountNumber, beneficiaryName, upiId, panNumber, gstNumber, businessAddress |
| `LinkedAccountResponse` | id, razorpayAccountId, ownerUsername, coliveName, bankName, ifscCode, accountNumber, beneficiaryName, primary, status, kycStatus, activationStatus, uploadedDocumentTypes |
| `KycSubmissionRequest` | panNumber, gstNumber, businessAddress, stakeholderType |
| `TransferRequest` | paymentId, linkedAccountId, amount, currency, registrationId, tenantName, coliveName, ownerUsername |
| `TransferResponse` | transferId, razorpayTransferId, paymentId, amount, platformFee, ownerAmount, status |
| `RefundRequest` | razorpayPaymentId, amount, reason, reverseTransfer |
| `RefundResponse` | refundId, razorpayRefundId, paymentId, amount, status |

---

## 9. Enums & Constants

```java
// Gender
MALE, FEMALE, OTHER

// DocumentType
AADHAR, PAN, PASSPORT, VOTER_ID, LICENSE

// roomOccupied
OCCUPIED, NOT_OCCUPIED, PARTIALLY_OCCUPIED

// RoomType
SINGLE, DOUBLE, TRIPLE, FOUR

// HouseType
ONE_RK, ONE_BHK, TWO_BHK, THREE_BHK

// EmployeeRole
ROLE_USER, ROLE_MODERATOR, ROLE_GUEST, ROLE_ADMIN

// PaymentType
ONLINE, CASH

// PaymentStatus
PENDING, COMPLETED, PARTIAL, OVERDUE, CANCELLED, FAILED, PENDING_APPROVAL

// ApprovalStatus
PENDING_APPROVAL, APPROVED, REJECTED, NOT_REQUIRED

// TransferStatus
PENDING_SOURCE_APPROVAL, PENDING_DESTINATION_APPROVAL, COMPLETED, REJECTED

// JobStatus
SUCCESS, PARTIAL_FAILURE, FAILURE

// TriggerType
SCHEDULED, MANUAL

// Platform fee
DEFAULT_PLATFORM_FEE = 4900 (paise = ₹49)
```

---

## 10. Security & Authentication

### JWT Configuration
- **Library**: JJWT 0.11.5
- **Algorithm**: HMAC-SHA256
- **Secret**: `${app.jwt.secret:change-me-secret-key-change-me-secret-key}`
- **Expiry**: `${app.jwt.expiration-ms:6000000}` (~1.67 hours)
- **Subject**: User's email or phone number (not username)
- **Token format**: `Bearer <JWT>`

### Auth Flow
```
1. POST /adminservices/login → {email/phone, password}
2. AuthService validates password (BCrypt)
3. JwtTokenProvider generates token (subject = email or phone)
4. Returns JwtResponse {token, tokenType, userId, username, expiresIn, forcePasswordChange}
5. Frontend stores in sessionStorage
6. All subsequent requests include: Authorization: Bearer <token>
7. JwtAuthenticationFilter extracts + validates token per request
8. Sets Spring SecurityContext with user authorities
```

### SecurityConfig
```java
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)

- CSRF: disabled (REST API)
- Session: STATELESS
- Public endpoints: /adminservices/signup, /adminservices/login, /adminservices/signup-with-files
- All others: authenticated
- JWT filter added before UsernamePasswordAuthenticationFilter
- Auth entry point: HTTP 401
- Password encoder: BCryptPasswordEncoder
```

### RBAC (Role-Based Access Control)
| Role | Access Level |
|------|-------------|
| `ROLE_ADMIN` | Full system access — approve/reject users, view all data |
| `ROLE_MODERATOR` | Property management — onboard tenants, manage rooms, approve payments |
| `ROLE_USER` | Tenant self-service — view profile, make payments, request transfers |
| `ROLE_GUEST` | Limited read-only access |

### Webhook Security (Razorpay)
- HMAC-SHA256 signature verification via `X-Razorpay-Signature` header
- Webhook secret: `${razorpay.webhookSecret}`
- Idempotency: `eventId` checked in `webhook_events` collection before processing
- Dev mode: Skips verification if secret not configured

---

## 11. Inter-Service Communication

All inter-service calls use **RestTemplate** (synchronous HTTP):

```
Registration Service (8082):
  → POST http://notification-services:8084/notifications/email
  → POST http://notification-services:8084/notifications/sms
  → POST http://notification-services:8084/notifications/whatsapp
  → POST http://notification-services:8084/notifications/telegram
  → POST http://payment-services:8083/payments/route/linked-accounts
  → POST http://payment-services:8083/payments/route/linked-accounts/{id}/submit-kyc
  → POST http://payment-services:8083/payments/route/linked-accounts/{id}/upload-document
  → GET  http://payment-services:8083/payments/route/linked-accounts/by-owner

Payment Service (8083):
  → POST http://notification-services:8084/notifications/email

Frontend Proxy (3000):
  → /api/payments/*         → http://localhost:8083/payments/*
  → /api/notifications/*    → http://localhost:8084/notifications/*
  → Direct calls            → http://localhost:8082/*
```

---

## 12. Scheduled Jobs

### Monthly Rent Generator

| Property | Value |
|----------|-------|
| **Cron** | `0 0 0 1 * *` (1st of every month, midnight) |
| **Service** | `RentSchedulerService.generateMonthlyRentRecords()` |
| **Manual trigger** | `processMonthlyRent(LocalDate)` |

**Workflow**:
1. Fetch all OCCUPIED tenants across all properties
2. Idempotency check — skip if record already exists for tenant + month
3. Create `RentPaymentTransaction` with status `PENDING` for each tenant
4. Calculate outstanding balance from previous months (PENDING, PARTIAL, OVERDUE)
5. Send multi-channel notifications to each tenant (email + SMS + WhatsApp)
6. Send summary notification to each property owner
7. Log execution in `schedulerJobLogs` with per-colive and per-tenant breakdown

### Razorpay Sync Retry Scheduler

| Property | Value |
|----------|-------|
| **Interval** | Every 30 minutes (configurable via `razorpay.sync.retry.interval` in ms) |
| **Class** | `RazorpaySyncRetryScheduler` (`payment-services/scheduler/`) |
| **Service Method** | `LinkedAccountService.retryUnsyncedAccounts()` |
| **Manual trigger** | `POST /payments/route/linked-accounts/retry-sync` |
| **Monitor** | `GET /payments/route/linked-accounts/unsynced` |

**Purpose**: When Razorpay API is unreachable during registration (network issues, invalid creds, Razorpay downtime), bank accounts are saved locally with `razorpaySynced=false` and a placeholder `acc_pending_*` ID. This scheduler picks up all unsynced accounts and retries:

**Workflow**:
1. Query `linked_accounts` where `razorpaySynced=false`
2. For each unsynced account:
   - Rebuild the full Razorpay `/v2/accounts` payload from stored fields
   - Call `POST https://api.razorpay.com/v2/accounts` with Basic Auth
   - On **success**: update `razorpayAccountId` (real ID), set `razorpaySynced=true`, `status=ACTIVE`, clear `syncFailureReason`, then configure route product
   - On **failure**: update `syncFailureReason` with latest error, leave `razorpaySynced=false` for next retry
3. Log summary: `"{synced}/{total} accounts synced successfully"`

**Razorpay API Payload (retry uses same as initial)**:
```json
{
  "email": "owner@example.com",
  "phone": "9876543210",
  "type": "route",
  "legal_business_name": "ABC Properties",
  "business_type": "individual",
  "contact_name": "John Doe",
  "legal_info": { "pan": "ABCDE1234F", "gst": "..." },
  "bank_account": {
    "ifsc_code": "HDFC0001234",
    "beneficiary_name": "John Doe",
    "account_number": "1234567890123",
    "account_type": "current"
  },
  "profile": {
    "category": "housing",
    "subcategory": "pg_and_hostels",
    "addresses": { "registered": { ... } }
  },
  "notes": { "ownerUsername": "...", "coliveName": "...", "platform": "CoLive Connect" }
}
```

**Config** (`application.properties`):
```properties
razorpay.sync.retry.interval=1800000  # 30 minutes in ms
```

---

## 13. Frontend: React Application

### 13.1 Tech Stack
- **React** 18.2.0 with **React Router DOM** 6.8.0
- **No UI framework** — custom CSS per component
- **No HTTP library** — native `fetch()` with custom interceptor
- **Express proxy** (port 3000) wraps production build

### 13.2 Environment Variables
| Variable | Default | Used In |
|----------|---------|---------|
| `REACT_APP_RAZORPAY_KEY` | `'rzp_test_key'` | Payment.js |
| `REACT_APP_PAYMENT_API_BASE_URL` | `'http://localhost:8083/payments'` | User.js |
| `REACT_APP_TENANT_DEFAULT_PASSWORD` | `'Tenant@123'` | MemberOnboard.js |

### 13.3 Routing Table (App.js)

| Path | Component | Protected | Role |
|------|-----------|-----------|------|
| `/` | Landing.js | No | — |
| `/login` | Login.js | No | — |
| `/signup` | Signup.js | No | — |
| `/registration` | Registration.js | No | — |
| `/registration-success` | RegistrationSuccess.js | No | — |
| `/dashboard` | Dashboard.js | Yes | MODERATOR, ADMIN |
| `/user` | User.js | Yes | USER |
| `/admin` | Admin.js | Yes | ADMIN |
| `/change-password` | ChangePassword.js | Yes | All |
| `/add-client` | AddClient.js | Yes | MODERATOR |
| `/member-onboard` | MemberOnboard.js | Yes | MODERATOR |
| `/tenant-edit` | TenantEdit.js | Yes | MODERATOR |
| `/payment` | Payment.js | Yes | USER |
| `/moderator-approvals` | ModeratorApprovalDashboard.js | Yes | MODERATOR |
| `/collection-report` | ModeratorCollectionReport.js | Yes | MODERATOR |
| `/admin-payments` | AdminPaymentDashboard.js | Yes | ADMIN |
| `/owner-payments` | OwnerPaymentDashboard.js | Yes | MODERATOR |

### 13.4 SessionStorage Keys
| Key | Value | Set When |
|-----|-------|----------|
| `authToken` | JWT string | After login |
| `tokenType` | `"Bearer"` | After login |
| `tokenExpiry` | Unix timestamp (ms) | After login (`Date.now() + expiresIn * 1000`) |
| `userId` | User ID | After login |
| `username` | Username | After login |
| `userDetails` | Full user JSON (roles, properties, clients) | After fetching user profile |
| `clientDetails` | Property list JSON | From userDetails |
| `clientNameAndRooms` | Alternative property list | From userDetails |
| `forcePasswordChange` | `"true"` string | If user must reset password |
| `onboardedUserData` | Tenant registration JSON | After onboarding |

### 13.5 Authentication Flow (Frontend)
```
1. User enters email/phone + password → Login.js
2. POST /adminservices/login → receives {token, tokenType, userId, username, expiresIn, forcePasswordChange}
3. Store in sessionStorage: authToken, tokenType, userId, username, tokenExpiry
4. Fetch user profile: GET /adminservices/admin/user/{username}
5. Store userDetails (includes roleNames, clientDetails)
6. Role-based redirect:
   - forcePasswordChange=true → /change-password
   - ROLE_ADMIN → /admin
   - ROLE_MODERATOR → /dashboard
   - ROLE_USER → /user (also fetches tenant registration data)

Global 401 Interceptor (apiClient.js):
- Wraps native fetch()
- Any 401 response → clearAuthData() + redirect to /login?session=expired
- Excludes /adminservices/login from interception

Token Expiry Check:
- Before every request: if (Date.now() > sessionStorage.tokenExpiry) → auto-logout
```

### 13.6 Styling
- **Approach**: Component-scoped CSS (one `.css` per `.js` file)
- **Design System**: CSS variables in App.css
  - Colors: `--brand` (#1f6b52), `--accent` (#c96f3b)
  - Fonts: Manrope (body), Space Grotesk (headings)
  - Radius: 14px (sm), 22px (md), 32px (lg)
- **Visual Language**: Glassmorphism, elevated shadows, earth-tone palette, rounded corners

---

## 14. Frontend: Component Reference

### Landing.js
Marketing home page. No API calls. Animated hero, scroll effects.

### Login.js
- **API**: `POST /adminservices/login`, `GET /adminservices/admin/user/{id}`, `GET /registrations/user/contact/{contactNo}`
- Auto-adds `+91` prefix to phone numbers (10 digits)
- Stores tokens in sessionStorage, role-based redirect

### Signup.js
3-step tenant self-registration:
1. Register account → `POST /adminservices/user`
2. Login → `POST /adminservices/login`
3. Member details → `POST /registrations` with file uploads
- Saves progress to localStorage (60s expiry)

### Registration.js
Property owner registration. Multi-step: account details → property + rooms → bank details → files (photos, licenses, KYC).
- `POST /adminservices/user`, `POST /clients`
- Supports PG/HOSTEL (rooms) and HOUSE/FLAT (houses)

### Dashboard.js
Moderator/owner property management hub (~40+ state variables):
- Room occupancy grid, tenant cards, drag-drop room reassignment
- Transfer request approval/rejection
- Tenant checkout (single + batch)
- Property photo/document gallery
- KYC linked account management
- Aadhar photo viewer
- **Tabs**: rooms, approvals, kyc, vacated

### Admin.js
Super-admin panel:
- **API**: `GET /adminservices/admin/pending-approvals`, `GET /adminservices/admin/active-users`
- Approve/reject/deactivate users
- Search by username/email/phone

### User.js
Tenant profile + payment + transfer hub:
- Payment summary, history, outstanding balance
- 3 payment modes: bank transfer (reference), Razorpay (online), cash
- Transfer request initiation
- **API**: `GET /rentpayments/tenant/{id}/payment-summary`, `POST /rentpayments/cash`, `POST /payments/order` (Razorpay)

### Payment.js
Standalone rent payment page:
- 3 tabs: bank details, Razorpay, cash
- Loads Razorpay SDK dynamically
- Creates order → opens checkout modal → verifies → records payment

### MemberOnboard.js
Moderator onboards tenant to room:
- Search existing tenant by phone (`GET /registrations/user/contact/{phone}`)
- Auto-populate form if found
- `POST /registrations/onboard` with files

### TenantEdit.js
Edit existing tenant details. Detects house vs room by registration ID prefix.

### ModeratorApprovalDashboard.js
5 tabs: approvals, bank-accounts, kyc, settlements, attachments.
- Photo gallery with CRUD
- Bank account management
- KYC document upload

### ModeratorCollectionReport.js
Monthly rent collection table by room. CSV export.
- `GET /rentpayments/owner/{username}/monthly-collection-status?month=&coliveName=`

### OwnerPaymentDashboard.js
Owner monthly overview: expected vs received rent, collection rate.
- `GET /rentpayments/owner/{username}/dashboard`

### AdminPaymentDashboard.js
Platform-wide financial metrics (ADMIN):
- Earnings, refunds, reconciliation tabs
- `GET /payments/route/reports/platform-earnings`, `POST /payments/route/refunds`

### ChangePassword.js
Forced password reset on first login. Clears session, redirects to `/login`.

### ProtectedRoute.js
Route guard. Checks `sessionStorage.authToken` + `tokenExpiry`. Redirects to `/login` if invalid.

### RegistrationSuccess.js
Post-registration confirmation. Shows login form for newly registered users.

### Notification.js
Empty placeholder for future notification UI.

---

## 15. Frontend: API Client & Auth Utilities

**File**: `client/src/utils/apiClient.js`

| Function | Purpose |
|----------|---------|
| `installGlobal401Interceptor()` | Wraps native `fetch()` to intercept 401 responses globally |
| `getAuthHeader()` | Returns `"Bearer {token}"` from sessionStorage |
| `isSessionExpired()` | Checks if `tokenExpiry < Date.now()` |
| `clearAuthData()` | Removes all auth keys from sessionStorage |
| `handle401(response)` | Manual 401 handler (alternative to global interceptor) |
| `authenticatedFetch(url, options)` | Wrapper that adds auth header, checks expiry, handles errors |

---

## 16. Key Business Flows (End-to-End)

### 16.1 Owner Registration
```
1. Owner fills Registration.js: username, email, phone, password
2. Adds property details: name, category (PG/HOSTEL/HOUSE/FLAT), rooms
3. Adds bank details: bank name, IFSC, account number, beneficiary, UPI
4. Uploads: Aadhar photo, property photos, license documents, KYC docs
5. POST /adminservices/signup-with-files → creates User (active=false)
6. Creates RoomOnBoardDocument → stores room inventory
7. If bank details present → POST /payments/route/linked-accounts → creates Razorpay linked account
8. Admin sees pending approval → GET /adminservices/admin/pending-approvals
9. Admin approves → POST /adminservices/admin/approve/{username} → user.active = true
10. Owner can now log in, manage rooms, onboard tenants
```

### 16.2 Tenant Onboarding
```
1. Tenant self-registers via Signup.js (ROLE_USER, active=true) OR moderator creates
2. Moderator opens MemberOnboard.js → searches tenant by phone
3. If found → auto-populate. If not → fill manually
4. Assigns: room/house, check-in date, rent amount, advance
5. Uploads: Aadhar photo, identity document
6. POST /registrations/onboardUser → creates RegistrationDocument
7. Updates room in RoomOnBoardDocument: occupied = "OCCUPIED"
8. Sends welcome notification (email + SMS)
9. Monthly rent records auto-generated on 1st of each month
```

### 16.3 Rent Payment (Razorpay Online)
```
1. Tenant opens User.js → selects "Pay Rent" → online
2. Frontend: POST /payments/order {amount in paise}
3. Backend creates Razorpay order → returns {orderId, amount, keyId}
4. Frontend loads Razorpay SDK → opens checkout modal
5. Tenant completes payment (UPI/card/netbanking)
6. Razorpay returns: {razorpay_payment_id, razorpay_order_id, razorpay_signature}
7. Frontend: POST /payments/verify → backend validates HMAC-SHA256 signature
8. Frontend: GET /registrations/user/email/{email} → gets tenantId
9. Frontend: POST /rentpayments/online {tenantId, rentMonth, amount, razorpay*}
10. Backend records payment → status: COMPLETED
11. If Razorpay Route enabled → initiates transfer to owner's linked bank account
    - Deducts platform fee (₹49)
    - Owner receives (amount - ₹49)
12. Webhook: POST /payments/webhook confirms settlement
```

### 16.4 Cash Payment
```
1. Moderator/tenant submits cash payment: POST /rentpayments/cash
2. Backend creates RentPaymentTransaction with approvalStatus: PENDING_APPROVAL
3. Owner sees pending approvals on Dashboard → approves/rejects
4. On approval → status: COMPLETED, approvalStatus: APPROVED
```

### 16.5 Tenant Transfer (2-Step Approval)
```
1. Tenant/moderator: POST /registrations/transfer/request
   {tenantRegistrationId, toColiveUserName, toColiveName, toRoomNumber}
2. Status: PENDING_SOURCE_APPROVAL
3. Source property owner approves: PUT /registrations/transfer/{id}/approve
4. Status: PENDING_DESTINATION_APPROVAL
5. Destination property owner approves: PUT /registrations/transfer/{id}/approve
6. System auto-executes:
   a. Checkout tenant from old room (checkOutDate = now, occupied = NOT_OCCUPIED)
   b. Create new registration at destination room
   c. Update room occupancy at both properties
7. Status: COMPLETED
```

### 16.6 Monthly Rent Generation (Scheduler)
```
1. Cron fires: 0 0 0 1 * * (midnight, 1st of month)
2. RentSchedulerService.generateMonthlyRentRecords()
3. Fetches all OCCUPIED tenants
4. For each tenant:
   a. Skip if record already exists (idempotency)
   b. Create PENDING RentPaymentTransaction
   c. Calculate outstanding from previous months
   d. Send reminder: email + SMS + WhatsApp
5. For each owner: send collection summary
6. Log execution to schedulerJobLogs
```

---

## 17. File Storage

### Configuration (application.properties)
```properties
file.storage.type=${FILE_STORAGE_TYPE:LOCAL}   # LOCAL | S3 | AZURE
```

### LOCAL Storage
```properties
file.storage.local.path=${FILE_STORAGE_LOCAL_PATH:C:\\ProjectSoftwares\\coLiveFiles\\}
# Docker: /app/colive-files/
```

### AWS S3 Storage
```properties
file.storage.s3.bucket=elite4-registrations
file.storage.s3.region=us-east-1
file.storage.s3.prefix=colive-files/
file.storage.s3.access-key=${AWS_ACCESS_KEY_ID:}
file.storage.s3.secret-key=${AWS_SECRET_ACCESS_KEY:}
```

### Azure Blob Storage
```properties
file.storage.azure.container=registrations
file.storage.azure.prefix=colive-files/
file.storage.azure.account-name=${AZURE_STORAGE_ACCOUNT:}
file.storage.azure.account-key=${AZURE_STORAGE_ACCOUNT_KEY:}
```

### File Types Stored
- Aadhar photos (tenant identity)
- Property photos (building/room images)
- License documents (property licenses)
- KYC documents (PAN, Aadhar front/back, passport, GST certificate)
- Identity documents (various document types per tenant)

---

## 18. Docker & Deployment

### docker-compose.yml (5 containers)
```yaml
services:
  mongo:                    # MongoDB 7
    image: mongo:7
    ports: 27017
    env: MONGO_INITDB_ROOT_USERNAME=root, MONGO_INITDB_ROOT_PASSWORD=${MONGO_ROOT_PASSWORD:-StrongPassword!123}
    volumes: mongo_data:/data/db
    healthcheck: mongosh --eval "db.adminCommand('ping')"

  registration-services:    # Java 17, port 8082
    build: ./elite4-main/registration-services/Dockerfile
    depends_on: mongo (healthy), notification-services
    volumes: colive_files:/app/colive-files/

  payment-services:         # Java 17, port 8083
    build: ./elite4-main/payment-services/Dockerfile
    depends_on: mongo (healthy), notification-services

  notification-services:    # Java 17, port 8084
    build: ./elite4-main/notification-services/Dockerfile

  frontend:                 # Nginx, port 80
    build: ./elite4UI-master/client
    depends_on: all services
```

### Dockerfile Pattern (all services)
```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17
COPY parent pom.xml + module pom.xml
RUN mvn dependency:go-offline       # Layer caching
COPY source code
RUN mvn clean package -pl <module> -am -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine  # ~31MB
COPY --from=build app.jar
EXPOSE <port>
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 19. Environment Variables Reference

### Registration Service
| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | 8082 | Service port |
| `MONGODB_URI` | `mongodb://root:StrongPassword!123@localhost:27017/admin` | MongoDB connection |
| `MONGODB_DATABASE` | admin | Database name |
| `NOTIFICATION_SERVICE_URL` | `http://localhost:8084` | Notification service base URL |
| `PAYMENT_SERVICE_URL` | `http://localhost:8083` | Payment service base URL |
| `FILE_STORAGE_TYPE` | LOCAL | LOCAL / S3 / AZURE |
| `FILE_STORAGE_LOCAL_PATH` | `C:\ProjectSoftwares\coLiveFiles\` | Local file storage path |
| `TENANT_DEFAULT_PASSWORD` | Tenant@123 | Default password for new tenants |
| `APP_JWT_SECRET` | change-me-secret-key... | JWT signing secret |
| `APP_JWT_EXPIRATION_MS` | 6000000 | JWT token expiry (ms) |
| `AWS_ACCESS_KEY_ID` | (empty) | S3 access key |
| `AWS_SECRET_ACCESS_KEY` | (empty) | S3 secret key |
| `AZURE_STORAGE_ACCOUNT` | (empty) | Azure storage account |
| `AZURE_STORAGE_ACCOUNT_KEY` | (empty) | Azure storage key |

### Payment Service
| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | 8083 | Service port |
| `RAZORPAY_KEY_ID` | rzp_test_SYwvJpnDcJEIvZ | Razorpay API key |
| `RAZORPAY_KEY_SECRET` | acXTG6sTD8L4eAgoKbxwHmzF | Razorpay secret |
| `RAZORPAY_COMPANY_NAME` | CoLive Connect | Display name in checkout |
| `RAZORPAY_PLATFORM_FEE` | 4900 | Platform fee in paise (₹49) |
| `RAZORPAY_ROUTE_ENABLED` | true | Enable money routing to owners |
| `RAZORPAY_WEBHOOK_SECRET` | (empty) | Webhook signature verification |
| `SSL_TRUSTSTORE_PATH` | cacerts.jks | SSL truststore path |
| `SSL_TRUSTSTORE_PASSWORD` | changeit | Truststore password |

### Notification Service
| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | 8084 | Service port |
| `MAIL_HOST` | smtp.gmail.com | SMTP host |
| `MAIL_PORT` | 587 | SMTP port |
| `MAIL_USERNAME` | (required) | Email account |
| `MAIL_PASSWORD` | (required) | Email password |
| `TWILIO_ACCOUNT_SID` | (required) | Twilio SID |
| `TWILIO_AUTH_TOKEN` | (required) | Twilio auth token |
| `TWILIO_FROM_NUMBER` | (required) | SMS sender number |
| `WHATSAPP_API_URL` | `https://graph.facebook.com/v19.0` | Meta API URL |
| `WHATSAPP_API_TOKEN` | (required) | Meta API token |
| `WHATSAPP_PHONE_NUMBER_ID` | (required) | WhatsApp business phone |
| `TELEGRAM_BOT_TOKEN` | (required) | Telegram bot token |

### Frontend
| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | 3000 | Express server port |
| `REACT_APP_RAZORPAY_KEY` | rzp_test_key | Razorpay public key |
| `REACT_APP_PAYMENT_API_BASE_URL` | `http://localhost:8083/payments` | Payment API URL |
| `REACT_APP_TENANT_DEFAULT_PASSWORD` | Tenant@123 | Default tenant password |

---

## 20. Dependencies

### Parent POM
```xml
<parent>spring-boot-starter-parent 3.3.0</parent>
<java.version>17</java.version>
<modules>registration-services, payment-services, notification-services</modules>
```

### Registration Service
```
spring-boot-starter, spring-boot-starter-web, spring-boot-starter-security,
spring-boot-starter-data-mongodb, spring-boot-starter-validation,
spring-security-crypto, jjwt-api/impl/jackson 0.11.5,
libphonenumber 8.13.40, spring-retry 2.0.6, spring-boot-starter-aop,
software.amazon.awssdk:s3 2.20.100, azure-storage-blob 12.20.2,
commons-lang3 3.13.0, lombok
```

### Payment Service
```
spring-boot-starter-web, spring-boot-starter-data-mongodb,
razorpay-java 1.4.6, spring-retry 2.0.6, spring-aspects,
httpclient5 5.2.3, lombok
```

### Notification Service
```
spring-boot-starter-web, spring-boot-starter-mail, spring-boot-starter-thymeleaf,
twilio 8.31.1, spring-retry 2.0.6, spring-aspects, lombok
```

### Frontend
```
Root: express 4.18.2, http-proxy-middleware 2.0.6
Client: react 18.2.0, react-dom 18.2.0, react-router-dom 6.8.0, react-scripts 5.0.1
```

---

## 21. Known Issues & Context

1. **Typo in application name**: `spring.application.name=resgistration-services` (should be `registration`)
2. **Duplicate MongoDB URI config**: Both `mongodb.uri` and `spring.data.mongodb.uri` set (only `spring.data.mongodb.*` is used by Spring Data)
3. **Dashboard fix**: `licenseDocumentsPath` and `uploadedPhotos` were not mapped from entity to DTO in AdminService.java — now fixed
4. **File storage**: Local path `C:\ProjectSoftwares\coLiveFiles\` is Windows-specific; Docker uses `/app/colive-files/`
5. **Phone format**: Frontend auto-adds `+91` (India); backend converts to E.164 via libphonenumber
6. **Default passwords in properties**: `StrongPassword!123` and `Tenant@123` are defaults — must be changed in production
7. **JWT secret**: Default `change-me-secret-key` must be replaced in production
8. **Razorpay test mode**: Current keys are test credentials (`rzp_test_*`)
9. **Payment service endpoints have no auth**: RouteController endpoints are unprotected — intended for internal service-to-service calls only
10. **Notification service is fire-and-forget**: No delivery guarantees or retry persistence

---

## 22. Razorpay Integration Fixes & Improvements (Applied)

The following critical bugs and improvements were identified and implemented:

### 22.1 — [P0] Missing `bank_account` in Razorpay API Payload (FIXED)

**Problem**: `LinkedAccountService.createLinkedAccount()` built a `bankAccount` JSON object but **never included it** in the `POST /v2/accounts` request body. The object was only passed to `configureProductForAccount()`. Without `bank_account` in the payload, Razorpay cannot set up settlements for the linked account.

**Fix**: Added `accountRequest.put("bank_account", bankAccount)` before the API call.

**File**: `payment-services/.../service/LinkedAccountService.java`

### 22.2 — [P0] Silent Test-Mode Fallback Replaced with Sync Tracking (FIXED)

**Problem**: When the Razorpay `/v2/accounts` API call failed (bad credentials, network issue, etc.), the code **silently** generated a fake `acc_<timestamp>` ID and saved it to MongoDB as if it succeeded. There was no way to distinguish real Razorpay accounts from placeholders. The same pattern existed for stakeholder creation (`sth_<timestamp>`) and document uploads (`doc_<timestamp>`).

**Fix**:
- Added `razorpaySynced` (boolean) and `syncFailureReason` (String) fields to `LinkedAccountDocument` and `LinkedAccountResponse`
- Failed accounts are saved with `razorpaySynced=false`, `status=PENDING_SYNC`, `activationStatus=PENDING_SYNC`, and placeholder ID `acc_pending_<timestamp>`
- Stakeholder and document upload fallbacks now **throw errors** instead of generating fake IDs — the calling code already has proper error handling
- `PaymentRouteClient` (registration-services) now checks the `razorpaySynced` response flag and:
  - Logs a warning when sync is pending
  - **Skips** KYC submission and document upload for unsynced accounts (can't submit KYC to a placeholder)

**Files**:
- `payment-services/.../document/LinkedAccountDocument.java` — new fields
- `payment-services/.../dto/LinkedAccountResponse.java` — new fields
- `payment-services/.../service/LinkedAccountService.java` — sync tracking logic, removed placeholder fallbacks from stakeholder/document
- `registration-services/.../service/PaymentRouteClient.java` — sync-aware response handling

### 22.3 — [P1] Automated Retry for Failed Razorpay Syncs (NEW)

**Problem**: If Razorpay was down during registration, the account was saved with a placeholder ID and **never retried**. The owner wouldn't receive payments until someone manually fixed it.

**Fix**: Created a scheduled retry mechanism:

| Component | File | Purpose |
|-----------|------|---------|
| `RazorpaySyncRetryScheduler` | `payment-services/.../scheduler/RazorpaySyncRetryScheduler.java` | `@Scheduled` job, runs every 30 min |
| `retryUnsyncedAccounts()` | `LinkedAccountService.java` | Rebuilds payload from stored fields, retries API call, updates on success/failure |
| `findByRazorpaySyncedFalse()` | `LinkedAccountRepository.java` | Query for all unsynced accounts |
| `@EnableScheduling` | `PaymentServiceApplication.java` | Enables Spring scheduling |

New API endpoints for manual control:
- `POST /payments/route/linked-accounts/retry-sync` — Manually trigger retry of all unsynced accounts
- `GET /payments/route/linked-accounts/unsynced` — View all accounts pending Razorpay sync (for monitoring/dashboard)

Retry interval configurable via `razorpay.sync.retry.interval` property (default: 1800000ms = 30 min).

### 22.4 — [P2] Sync Failures Surfaced to Caller (FIXED)

**Problem**: `PaymentRouteClient.syncBankAccounts()` is fire-and-forget — failures were caught and logged, but the registration response to the owner gave no hint that Razorpay onboarding failed.

**Fix**:
- `LinkedAccountResponse` now includes `razorpaySynced` and `syncFailureReason`
- `PaymentRouteClient` reads the `razorpaySynced` flag from the response and logs appropriately:
  - `✅ Step 1 complete: Bank account synced to Razorpay` (if synced)
  - `⚠️ Step 1 partial: Bank account saved locally but Razorpay sync pending` (if not synced)
- KYC and document upload steps are **skipped** for unsynced accounts (they'd fail anyway against a placeholder ID)
- The `createLinkedAccount()` response message now says `"Bank account saved locally. Razorpay sync pending — will retry automatically."` when sync fails

### 22.5 — [P3] Incorrect Razorpay Profile Subcategory (FIXED)

**Problem**: The code sent `subcategory: "property_management"` but the Razorpay docs and the project’s own `RAZORPAY_ROUTE_PHASES.md` specify `subcategory: "pg_and_hostels"`. This could cause Razorpay to miscategorize the account or reject it.

**Fix**: Changed from `"property_management"` to `"pg_and_hostels"` in both `createLinkedAccount()` and `retryUnsyncedAccounts()`.

### Summary of New/Modified Files

```
payment-services/
  src/main/java/com/elite4/anandan/paymentservices/
    PaymentServiceApplication.java           ← MODIFIED (@EnableScheduling added)
    document/
      LinkedAccountDocument.java             ← MODIFIED (razorpaySynced, syncFailureReason fields)
    dto/
      LinkedAccountResponse.java             ← MODIFIED (razorpaySynced, syncFailureReason fields)
    repository/
      LinkedAccountRepository.java           ← MODIFIED (findByRazorpaySyncedFalse)
    service/
      LinkedAccountService.java              ← MODIFIED (bank_account in payload, sync tracking,
                                                        retry logic, removed silent fallbacks,
                                                        fixed subcategory)
    controller/
      RouteController.java                   ← MODIFIED (retry-sync, unsynced endpoints)
    scheduler/
      RazorpaySyncRetryScheduler.java        ← NEW (30-min retry job)

registration-services/
  src/main/java/com/elite4/anandan/registrationservices/
    service/
      PaymentRouteClient.java                ← MODIFIED (sync-aware response handling,
                                                        skip KYC for unsynced)
```

---

*Generated for Elite4 CoLive Connect — Last updated: April 2026*
