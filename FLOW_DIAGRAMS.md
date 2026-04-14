# Elite4 — Registration, Add Client & Payment Flow Diagrams

> **Last Updated**: April 14, 2026

---

## Table of Contents

1. [Registration Flow (New CoLive Owner)](#1-registration-flow-new-colive-owner)
2. [Add Client Flow (Add Property to Existing User)](#2-add-client-flow-add-property-to-existing-user)
3. [Razorpay Route Onboarding Flow](#3-razorpay-route-onboarding-flow)
4. [File Naming & Data Mapping](#4-file-naming--data-mapping)
5. [MongoDB Document Model](#5-mongodb-document-model)
6. [Dashboard KYC Management Flow](#6-dashboard-kyc-management-flow)

---

## 1. Registration Flow (New CoLive Owner)

### High-Level Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          REGISTRATION FLOW                                  │
│                                                                             │
│  Registration.js ──► UserController ──► UserCreationService ──► MongoDB     │
│       (React)          (REST API)          (Business Logic)     (Storage)   │
│                                                │                            │
│                                                ├──► S3 (File Upload)        │
│                                                │                            │
│                                                └──► PaymentRouteClient      │
│                                                        │                    │
│                                                        ▼                    │
│                                                  Payment Service            │
│                                                        │                    │
│                                                        ▼                    │
│                                                  Razorpay API               │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Detailed Sequence Diagram

```
  ┌──────────────┐    ┌──────────────────┐    ┌────────────────────┐    ┌──────────────────┐    ┌──────────────┐
  │Registration.js│    │  UserController   │    │UserCreationService │    │PaymentRouteClient│    │Payment Service│
  └──────┬───────┘    └────────┬─────────┘    └─────────┬──────────┘    └────────┬─────────┘    └──────┬───────┘
         │                     │                        │                        │                     │
         │ POST /adminservices/│                        │                        │                     │
         │ signup-with-files   │                        │                        │                     │
         │ (multipart/form)    │                        │                        │                     │
         │────────────────────►│                        │                        │                     │
         │                     │                        │                        │                     │
         │                     │ Parse multipart:       │                        │                     │
         │                     │ • request (JSON)       │                        │                     │
         │                     │ • userAadhar           │                        │                     │
         │                     │ • property_X_photo_Y   │                        │                     │
         │                     │ • property_X_license_Y │                        │                     │
         │                     │ • kyc_<docType>        │                        │                     │
         │                     │                        │                        │                     │
         │                     │ createUserWithFiles    │                        │                     │
         │                     │ AndPhotos()            │                        │                     │
         │                     │───────────────────────►│                        │                     │
         │                     │                        │                        │                     │
         │                     │                        │ ① Validate uniqueness  │                     │
         │                     │                        │   (username/email/phone)│                     │
         │                     │                        │                        │                     │
         │                     │                        │ ② Create rooms doc     │                     │
         │                     │                        │   (RoomOnBoardDocument) │                     │
         │                     │                        │                        │                     │
         │                     │                        │ ③ Upload photos → S3   │                     │
         │                     │                        │                        │                     │
         │                     │                        │ ④ Upload licenses → S3 │                     │
         │                     │                        │                        │                     │
         │                     │                        │ ⑤ Save User → MongoDB  │                     │
         │                     │                        │                        │                     │
         │                     │                        │ ⑥ syncBankAccounts()   │                     │
         │                     │                        │   (per bank account)   │                     │
         │                     │                        │───────────────────────►│                     │
         │                     │                        │                        │                     │
         │                     │                        │                        │  STEP 1: Create     │
         │                     │                        │                        │  Linked Account     │
         │                     │                        │                        │─────────────────────►
         │                     │                        │                        │                     │
         │                     │                        │                        │  STEP 2: Submit KYC │
         │                     │                        │                        │─────────────────────►
         │                     │                        │                        │                     │
         │                     │                        │                        │  STEP 3: Upload     │
         │                     │                        │                        │  Documents (×N)     │
         │                     │                        │                        │─────────────────────►
         │                     │                        │                        │                     │
         │                     │                        │◄───────────────────────│                     │
         │                     │                        │                        │                     │
         │                     │                        │ ⑦ Upload aadhar → S3   │                     │
         │                     │                        │                        │                     │
         │                     │                        │ ⑧ Update user with     │                     │
         │                     │                        │   file paths           │                     │
         │                     │                        │                        │                     │
         │                     │◄───────────────────────│                        │                     │
         │                     │                        │                        │                     │
         │  HTTP 201           │                        │                        │                     │
         │  UserResponse       │                        │                        │                     │
         │  {id, token,        │                        │                        │                     │
         │   clientDetails}    │                        │                        │                     │
         │◄────────────────────│                        │                        │                     │
         │                     │                        │                        │                     │
         │ Store in session    │                        │                        │                     │
         │ Redirect to         │                        │                        │                     │
         │ /registration-      │                        │                        │                     │
         │  success            │                        │                        │                     │
         │                     │                        │                        │                     │
```

### FormData Structure (Registration.js)

```
FormData {
  request:                  JSON blob (UserCreateRequest)
  ├── username              "coliveowner1"
  ├── password              "Password@123"
  ├── email                 "owner@example.com"
  ├── phoneNumber           "+919876543210"
  └── coliveDetails[] ──────┐
      ├── [0]               │
      │   ├── coliveName    "Sunshine Residency"
      │   ├── categoryType  "COLIVE"
      │   ├── panNumber     "ABCDE1234F"
      │   ├── gstNumber     "18AABCT1234H1Z0"
      │   ├── rooms[]       [{houseNumber, houseType, occupied}]
      │   └── bankDetailsList[]
      │       └── {bankName, accountNumber, ifscCode, beneficiaryName}
      └── [1] ...           (additional properties)

  userAadhar:               File (user's Aadhaar image)

  property_0_photo_0:       File (property 0, photo 0)
  property_0_photo_1:       File (property 0, photo 1)
  property_1_photo_0:       File (property 1, photo 0)

  property_0_license_0:     File (property 0, license doc 0)
  property_0_license_1:     File (property 0, license doc 1)

  kyc_0_pan:                  File (property 0: PAN card)
  kyc_0_aadhar_front:         File (property 0: Aadhaar front side)
  kyc_0_aadhar_back:          File (property 0: Aadhaar back side)
  kyc_0_cancelled_cheque:     File (property 0: Cancelled cheque)
  kyc_0_gst_certificate:      File (property 0: GST certificate)
  kyc_1_pan:                  File (property 1: PAN card)
  kyc_1_aadhar_front:         File (property 1: Aadhaar front side)
  ...                         (per property)
}
```

---

## 2. Add Client Flow (Add Property to Existing User)

### High-Level Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         ADD CLIENT FLOW                                     │
│                                                                             │
│  AddClient.js ──► UserController ──► UserCreationService ──► MongoDB        │
│     (React)        (REST API)          (Business Logic)     (Update User)  │
│                    @PreAuthorize            │                                │
│                   (ADMIN/MODERATOR)         ├──► S3 (File Upload)           │
│                                             │                               │
│                                             └──► PaymentRouteClient         │
│                                                      │                      │
│                                                      ▼                      │
│                                                Payment Service              │
│                                                      │                      │
│                                                      ▼                      │
│                                                Razorpay API                 │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Detailed Sequence Diagram

```
  ┌────────────┐    ┌──────────────────┐    ┌────────────────────┐    ┌──────────────────┐    ┌──────────────┐
  │AddClient.js│    │  UserController   │    │UserCreationService │    │PaymentRouteClient│    │Payment Service│
  └─────┬──────┘    └────────┬─────────┘    └─────────┬──────────┘    └────────┬─────────┘    └──────┬───────┘
        │                    │                        │                        │                     │
        │ POST /adminservices│                        │                        │                     │
        │ /addClientToUser-  │                        │                        │                     │
        │  with-files        │                        │                        │                     │
        │ (JWT + multipart)  │                        │                        │                     │
        │───────────────────►│                        │                        │                     │
        │                    │                        │                        │                     │
        │                    │ @PreAuthorize           │                        │                     │
        │                    │ (ADMIN/MODERATOR)       │                        │                     │
        │                    │                        │                        │                     │
        │                    │ Parse multipart:       │                        │                     │
        │                    │ • request (JSON)       │                        │                     │
        │                    │ • property_0_photo_Y   │                        │                     │
        │                    │ • property_0_license_Y │                        │                     │
        │                    │ • kyc_<docType>        │                        │                     │
        │                    │                        │                        │                     │
        │                    │ addClientToUserWith    │                        │                     │
        │                    │ FilesAndPhotos()       │                        │                     │
        │                    │───────────────────────►│                        │                     │
        │                    │                        │                        │                     │
        │                    │                        │ ① Validate user exists │                     │
        │                    │                        │   & coliveName unique  │                     │
        │                    │                        │                        │                     │
        │                    │                        │ ② Create rooms doc     │                     │
        │                    │                        │   (RoomOnBoardDocument) │                     │
        │                    │                        │                        │                     │
        │                    │                        │ ③ Upload photos → S3   │                     │
        │                    │                        │                        │                     │
        │                    │                        │ ④ Upload licenses → S3 │                     │
        │                    │                        │                        │                     │
        │                    │                        │ ⑤ Append new client to │                     │
        │                    │                        │   user.clientDetails[] │                     │
        │                    │                        │                        │                     │
        │                    │                        │ ⑥ Update user email/   │                     │
        │                    │                        │   phone if provided    │                     │
        │                    │                        │                        │                     │
        │                    │                        │ ⑦ Save User → MongoDB  │                     │
        │                    │                        │                        │                     │
        │                    │                        │ ⑧ syncBankAccounts()   │                     │
        │                    │                        │───────────────────────►│                     │
        │                    │                        │                        │                     │
        │                    │                        │                        │  STEP 1: Create     │
        │                    │                        │                        │  Linked Account     │
        │                    │                        │                        │─────────────────────►
        │                    │                        │                        │                     │
        │                    │                        │                        │  STEP 2: Submit KYC │
        │                    │                        │                        │─────────────────────►
        │                    │                        │                        │                     │
        │                    │                        │                        │  STEP 3: Upload     │
        │                    │                        │                        │  Documents (×N)     │
        │                    │                        │                        │─────────────────────►
        │                    │                        │                        │                     │
        │                    │                        │◄───────────────────────│                     │
        │                    │◄───────────────────────│                        │                     │
        │                    │                        │                        │                     │
        │  HTTP 200          │                        │                        │                     │
        │  UserResponse      │                        │                        │                     │
        │  {id, updated      │                        │                        │                     │
        │   clientDetails[]} │                        │                        │                     │
        │◄───────────────────│                        │                        │                     │
        │                    │                        │                        │                     │
        │ Show success toast │                        │                        │                     │
        │ Navigate to        │                        │                        │                     │
        │ /dashboard         │                        │                        │                     │
        │                    │                        │                        │                     │
```

### FormData Structure (AddClient.js)

```
FormData {
  request:                  JSON blob (AddClientToUser)
  ├── username              "coliveowner1"       ← existing user
  ├── coliveName            "New Property"
  ├── categoryType          "COLIVE"
  ├── panNumber             "XYZAB5678C"
  ├── gstNumber             "27XYZAB5678C1Z5"
  ├── rooms[]               [{houseNumber, houseType, occupied}]
  └── bankDetailsList[]
      └── {bankName, accountNumber, ifscCode, beneficiaryName}

  property_0_photo_0:       File (property photo 0)
  property_0_photo_1:       File (property photo 1)

  property_0_license_0:     File (license doc 0)

  kyc_0_pan:                  File (PAN card)
  kyc_0_aadhar_front:         File (Aadhaar front)
  kyc_0_aadhar_back:          File (Aadhaar back)
  kyc_0_cancelled_cheque:     File (Cancelled cheque)
  kyc_0_gst_certificate:      File (GST certificate)
}
```

### Key Differences from Registration

| Aspect                 | Registration                    | Add Client                        |
|------------------------|---------------------------------|-----------------------------------|
| **Endpoint**           | `/signup-with-files`            | `/addClientToUser-with-files`     |
| **Auth Required**      | No (public)                     | Yes (ADMIN/MODERATOR role)        |
| **User Creation**      | Creates new user                | Appends to existing user          |
| **Aadhaar Upload**     | Uploads user aadhar             | Not applicable                    |
| **Multiple Properties**| Supports multiple properties    | Single property per call          |
| **Response Code**      | 201 Created                     | 200 OK                            |
| **Post-Action**        | Redirect to success page        | Navigate to dashboard             |

---

## 3. Razorpay Route Onboarding Flow

### 3-Step Razorpay Onboarding (PaymentRouteClient → Payment Service → Razorpay)

```
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│                          RAZORPAY ROUTE ONBOARDING (3 Steps)                             │
│                                                                                          │
│  ┌───────────────────┐     ┌─────────────────┐     ┌───────────────────────────────────┐ │
│  │PaymentRouteClient │     │ Payment Service  │     │          Razorpay API             │ │
│  │(registration-svc) │     │   (port 8083)    │     │                                   │ │
│  └────────┬──────────┘     └────────┬────────┘     └────────────────┬──────────────────┘ │
│           │                         │                               │                     │
│   ══════════════════════════════════════════════════════════════════════════════════       │
│   ║ STEP 1: CREATE LINKED ACCOUNT                                              ║       │
│   ══════════════════════════════════════════════════════════════════════════════════       │
│           │                         │                               │                     │
│           │  POST /payments/route/  │                               │                     │
│           │  linked-accounts        │                               │                     │
│           │────────────────────────►│                               │                     │
│           │  {ownerUsername,        │  POST /v2/accounts            │                     │
│           │   coliveName,          │──────────────────────────────►│                     │
│           │   bankName,            │                               │                     │
│           │   accountNumber,       │     {email, profile,          │                     │
│           │   ifscCode,            │      legal_info,              │                     │
│           │   beneficiaryName,     │      legal_business_name}     │                     │
│           │   email, phone,        │                               │                     │
│           │   panNumber,           │  ◄── acc_XXXXXXXXXXXX         │                     │
│           │   gstNumber,           │                               │                     │
│           │   legalBusinessName,   │  POST /v2/accounts/{id}/      │                     │
│           │   businessType}        │  products                     │                     │
│           │                        │──────────────────────────────►│                     │
│           │                        │                               │                     │
│           │                        │  ◄── product config ID        │                     │
│           │                        │                               │                     │
│           │                        │  Save LinkedAccountDocument   │                     │
│           │                        │  to MongoDB                   │                     │
│           │  ◄─── HTTP 201         │                               │                     │
│           │  {id: "acc_XXX",       │                               │                     │
│           │   status: "ACTIVE"}    │                               │                     │
│           │                         │                               │                     │
│   ══════════════════════════════════════════════════════════════════════════════════       │
│   ║ STEP 2: SUBMIT KYC (Create Stakeholder)                                   ║       │
│   ══════════════════════════════════════════════════════════════════════════════════       │
│           │                         │                               │                     │
│           │  POST /payments/route/  │                               │                     │
│           │  linked-accounts/       │                               │                     │
│           │  {id}/submit-kyc        │                               │                     │
│           │────────────────────────►│                               │                     │
│           │  {panNumber,           │  POST /v2/accounts/{id}/      │                     │
│           │   gstNumber,           │  stakeholders                 │                     │
│           │   stakeholderName,     │──────────────────────────────►│                     │
│           │   stakeholderEmail,    │                               │                     │
│           │   stakeholderPhone}    │  {name, phone, email,         │                     │
│           │                        │   kyc: {pan},                 │                     │
│           │                        │   notes: {gstNumber}}         │                     │
│           │                        │                               │                     │
│           │                        │  ◄── sth_XXXXXXXXXXXX         │                     │
│           │                        │                               │                     │
│           │                        │  Update LinkedAccountDocument │                     │
│           │                        │  (kycStatus → SUBMITTED,      │                     │
│           │                        │   stakeholderId → sth_XXX)    │                     │
│           │  ◄─── HTTP 200         │                               │                     │
│           │  {kycStatus:           │                               │                     │
│           │   "SUBMITTED",         │                               │                     │
│           │   stakeholderId:       │                               │                     │
│           │   "sth_XXX"}           │                               │                     │
│           │                         │                               │                     │
│   ══════════════════════════════════════════════════════════════════════════════════       │
│   ║ STEP 3: UPLOAD DOCUMENTS (per KYC document)                                ║       │
│   ══════════════════════════════════════════════════════════════════════════════════       │
│           │                         │                               │                     │
│           │  POST /payments/route/  │                               │ ┌──── Loop ────┐   │
│           │  linked-accounts/       │                               │ │ per document  │   │
│           │  {id}/upload-document   │                               │ │               │   │
│           │  (multipart:           │  POST /v2/accounts/{id}/      │ │               │   │
│           │   documentType + file) │  stakeholders/{sth_id}/       │ │               │   │
│           │────────────────────────►│  documents                   │ │               │   │
│           │                        │──────────────────────────────►│ │               │   │
│           │                        │                               │ │  • pan        │   │
│           │                        │  ◄── doc_XXXXXXXXXXXX         │ │  • aadhar_f   │   │
│           │                        │                               │ │  • aadhar_b   │   │
│           │                        │  Append to                    │ │  • cheque     │   │
│           │                        │  uploadedDocuments[]           │ │  • gst_cert   │   │
│           │  ◄─── HTTP 200         │                               │ └───────────────┘   │
│           │  {doc uploaded}        │                               │                     │
│           │                         │                               │                     │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

### Razorpay API Endpoints Used

| Step | Internal Endpoint                              | Razorpay API                                                   | Purpose                      |
|------|------------------------------------------------|----------------------------------------------------------------|------------------------------|
| 1a   | `POST /payments/route/linked-accounts`         | `POST /v2/accounts`                                            | Create Razorpay account      |
| 1b   | (same call)                                    | `POST /v2/accounts/{id}/products`                              | Configure route product      |
| 2    | `POST /payments/route/linked-accounts/{id}/submit-kyc` | `POST /v2/accounts/{id}/stakeholders`                  | Create stakeholder with KYC  |
| 3    | `POST /payments/route/linked-accounts/{id}/upload-document` | `POST /v2/accounts/{id}/stakeholders/{sth_id}/documents` | Upload KYC documents      |

### Supported KYC Document Types

| Document Type               | Key in FormData          | Razorpay Type              |
|-----------------------------|--------------------------|----------------------------|
| PAN Card                    | `kyc_pan`                | `pan`                      |
| Aadhaar Front               | `kyc_aadhar_front`       | `aadhar_front`             |
| Aadhaar Back                | `kyc_aadhar_back`        | `aadhar_back`              |
| Cancelled Cheque            | `kyc_cancelled_cheque`   | `cancelled_cheque`         |
| GST Certificate             | `kyc_gst_certificate`    | `gst_certificate`          |
| Passport Front              | —                        | `passport_front`           |
| Passport Back               | —                        | `passport_back`            |
| Voter ID Front              | —                        | `voter_id_front`           |
| Voter ID Back               | —                        | `voter_id_back`            |
| Driving License Front       | —                        | `driving_license_front`    |
| Driving License Back        | —                        | `driving_license_back`     |
| Shop Establishment Cert     | —                        | `shop_establishment_certificate` |

---

## 4. File Naming & Data Mapping

### Frontend → Backend File Key Convention

```
┌─────────────────────────────────────┐     ┌────────────────────────────────────┐
│       Frontend (FormData Keys)      │     │     Backend (Controller Parsing)    │
├─────────────────────────────────────┤     ├────────────────────────────────────┤
│                                     │     │                                    │
│  property_{X}_photo_{Y}             │────►│  Map<coliveName, List<byte[]>>     │
│  property_0_photo_0                 │     │  propertyPhotosMap                 │
│  property_0_photo_1                 │     │                                    │
│  property_1_photo_0                 │     │  X = client index → coliveName     │
│                                     │     │  Y = photo index within property   │
│                                     │     │                                    │
│  property_{X}_license_{Y}           │────►│  Map<coliveName, List<byte[]>>     │
│  property_0_license_0               │     │  licenseDocumentsMap               │
│  property_0_license_1               │     │                                    │
│                                     │     │                                    │
│  kyc_{index}_{documentType}         │────►│  Map<coliveName,                   │
│  kyc_0_pan                          │     │    Map<docType, byte[]>>           │
│  kyc_0_aadhar_front                 │     │  kycDocumentsMap                   │
│  kyc_1_aadhar_back                  │     │                                    │
│  kyc_0_cancelled_cheque             │     │  Parse index → coliveName,         │
│  kyc_1_gst_certificate              │     │  strip prefix → docType            │
│                                     │     │  Each property gets its own         │
│                                     │     │  KYC docs for Razorpay             │                │     │                                    │
│                                     │     │                                    │
│  userAadhar                         │────►│  MultipartFile userAadhar          │
│                                     │     │  (Registration only)               │
└─────────────────────────────────────┘     └────────────────────────────────────┘
```

### Service-to-Service Data Flow

```
registration-services (8082)                     payment-services (8083)
┌──────────────────────────────┐                ┌──────────────────────────┐
│                              │                │                          │
│  PaymentRouteClient          │                │  RouteController         │
│  ├── syncBankAccounts()      │   REST calls   │  ├── /linked-accounts    │
│  │   ├── Step 1: POST  ─────│───────────────►│  │   createLinkedAccount()│
│  │   ├── Step 2: POST  ─────│───────────────►│  │   submitKyc()         │
│  │   └── Step 3: POST  ─────│───────────────►│  │   uploadDocument()    │
│  │       (multipart)        │                │  │                        │
│  │                          │                │  └──► LinkedAccountService│
│  │   Bank details:          │                │       ├── Razorpay API    │
│  │   • ownerUsername        │                │       ├── MongoDB save    │
│  │   • coliveName           │                │       └── S3 (if needed)  │
│  │   • bankName             │                │                          │
│  │   • accountNumber        │                └──────────────────────────┘
│  │   • ifscCode             │
│  │   • beneficiaryName      │
│  │   • email/phone          │
│  │   • panNumber/gstNumber  │
│  │   • kycDocuments (bytes) │
│  │                          │
│  └── submitKyc()            │
│  └── uploadDocument()       │
│                              │
└──────────────────────────────┘
```

---

## 5. MongoDB Document Model

### Collections & Relationships

```
┌─────────────────────────────────────────────┐
│  Collection: users                           │
│  ┌─────────────────────────────────────────┐ │
│  │ User                                    │ │
│  │ ├── id                                  │ │
│  │ ├── username                            │ │
│  │ ├── email                               │ │
│  │ ├── phoneE164                           │ │
│  │ ├── passwordHash                        │ │
│  │ ├── roleIds[]                           │ │
│  │ ├── active                              │ │
│  │ ├── userAadharPath                      │ │
│  │ ├── createdAt / updatedAt               │ │
│  │ └── clientDetails[]  ◄──────────────┐   │ │
│  │     ├── [0] ClientAndRoomOnBoardId  │   │ │
│  │     │   ├── coliveName              │   │ │
│  │     │   ├── categoryType            │   │ │
│  │     │   ├── panNumber               │   │ │
│  │     │   ├── gstNumber               │   │ │
│  │     │   ├── bankDetailsList[]       │   │ │
│  │     │   │   └── {bankName,          │   │ │
│  │     │   │       accountNumber,      │   │ │
│  │     │   │       ifscCode,           │   │ │
│  │     │   │       beneficiaryName}    │   │ │
│  │     │   ├── uploadedPhotos[]        │   │ │
│  │     │   ├── licenseDocumentsPath[]  │   │ │
│  │     │   └── roomOnBoardId ──────────┼───┤ │
│  │     └── [1] ...                     │   │ │
│  └─────────────────────────────────────────┘ │
│                                              │
└──────────────────────────────────────────────┘
          │ roomOnBoardId (reference)
          ▼
┌─────────────────────────────────────────────┐
│  Collection: rooms_on_board                  │
│  ┌─────────────────────────────────────────┐ │
│  │ RoomOnBoardDocument                     │ │
│  │ ├── id                                  │ │
│  │ └── rooms[]                             │ │
│  │     └── {houseNumber/roomNumber,        │ │
│  │          houseType/roomType,            │ │
│  │          occupied (boolean)}            │ │
│  └─────────────────────────────────────────┘ │
└──────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│  Collection: linked_accounts                 │
│  (payment-services DB)                       │
│  ┌─────────────────────────────────────────┐ │
│  │ LinkedAccountDocument                   │ │
│  │ ├── id                                  │ │
│  │ ├── razorpayAccountId   (acc_XXX)       │ │
│  │ ├── stakeholderId       (sth_XXX)       │ │
│  │ ├── ownerUsername                       │ │
│  │ ├── coliveName                          │ │
│  │ ├── bankName                            │ │
│  │ ├── accountNumber                       │ │
│  │ ├── ifscCode                            │ │
│  │ ├── beneficiaryName                     │ │
│  │ ├── isPrimary                           │ │
│  │ ├── kycStatus                           │ │
│  │ │   (NOT_SUBMITTED → SUBMITTED →        │ │
│  │ │    VERIFIED / REJECTED)               │ │
│  │ ├── activationStatus                    │ │
│  │ └── uploadedDocuments[]                 │ │
│  │     └── UploadedDocument                │ │
│  │         ├── documentType                │ │
│  │         ├── razorpayDocId  (doc_XXX)    │ │
│  │         ├── originalFileName            │ │
│  │         └── uploadedAt                  │ │
│  └─────────────────────────────────────────┘ │
└──────────────────────────────────────────────┘
```

---

## Service Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│                        ┌──────────────────┐                                 │
│                        │   React Frontend  │                                │
│                        │  (elite4UI-master) │                                │
│                        └────────┬─────────┘                                 │
│                                 │                                           │
│              ┌──────────────────┼──────────────────┐                        │
│              │                  │                   │                        │
│              ▼                  ▼                   ▼                        │
│  ┌───────────────────┐ ┌──────────────┐ ┌──────────────────┐               │
│  │  registration-svc  │ │ payment-svc  │ │ notification-svc │               │
│  │    (port 8082)     │ │ (port 8083)  │ │   (port 8084)    │               │
│  │                    │ │              │ │                  │               │
│  │ • User signup      │ │ • Razorpay   │ │ • Email (SMTP)   │               │
│  │ • Add client       │ │   Route API  │ │ • SMS (Twilio)   │               │
│  │ • Auth (JWT)       │ │ • Linked     │ │ • WhatsApp       │               │
│  │ • File upload      │ │   accounts   │ │ • Telegram       │               │
│  │ • Room management  │ │ • KYC docs   │ │                  │               │
│  │                    │ │ • Payments   │ │                  │               │
│  └────────┬───────────┘ └──────┬───────┘ └──────────────────┘               │
│           │                    │                                             │
│           │  REST calls        │  REST calls                                │
│           │                    │                                             │
│           ▼                    ▼                                             │
│  ┌─────────────┐      ┌───────────────┐                                     │
│  │   MongoDB    │      │  Razorpay API  │                                    │
│  │  (users,     │      │  (v2/accounts, │                                    │
│  │   rooms,     │      │   stakeholders,│                                    │
│  │   linked_    │      │   documents)   │                                    │
│  │   accounts)  │      └───────────────┘                                     │
│  └─────────────┘                                                            │
│                                                                             │
│  ┌─────────────┐                                                            │
│  │  AWS S3      │                                                            │
│  │  (photos,    │                                                            │
│  │   documents) │                                                            │
│  └─────────────┘                                                            │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Quick Reference: Endpoints

| Method | Endpoint                                           | Service       | Auth     | Description                        |
|--------|-----------------------------------------------------|---------------|----------|------------------------------------|
| POST   | `/adminservices/signup-with-files`                  | registration  | Public   | Register new colive owner          |
| POST   | `/adminservices/addClientToUser-with-files`         | registration  | JWT+Role | Add property to existing user      |
| GET    | `/adminservices/kyc/linked-accounts?ownerUsername=X`| registration  | JWT+Role | Get linked accounts for moderator  |
| GET    | `/adminservices/kyc/document-types`                 | registration  | JWT+Role | Get supported document types       |
| POST   | `/adminservices/kyc/linked-accounts/{id}/upload-document` | registration | JWT+Role | Upload KYC doc (proxy → payment) |
| GET    | `/adminservices/kyc/linked-accounts/{id}/kyc-status`| registration  | JWT+Role | Get KYC status for account         |
| POST   | `/payments/route/linked-accounts`                   | payment       | Internal | Create Razorpay linked account     |
| POST   | `/payments/route/linked-accounts/{id}/submit-kyc`   | payment       | Internal | Submit KYC to Razorpay             |
| POST   | `/payments/route/linked-accounts/{id}/upload-document` | payment    | Internal | Upload KYC document to Razorpay    |
| GET    | `/payments/route/linked-accounts/document-types`    | payment       | Internal | Get supported document types       |
| GET    | `/payments/route/linked-accounts/by-owner?ownerUsername=X` | payment | Internal | Get accounts by owner username   |

---

## 6. Dashboard KYC Management Flow

### Overview

Moderators can view and update KYC documents per property (coliveName) from the Dashboard.
Each upload is forwarded through registration-services → payment-services → Razorpay API.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    DASHBOARD KYC MANAGEMENT FLOW                            │
│                                                                             │
│  Dashboard.js ──► KycController ──► RouteController ──► Razorpay API       │
│   (KYC Tab)      (registration)       (payment)          (Document Upload) │
│                                                                             │
│  1. Moderator clicks "KYC Documents" tab                                    │
│  2. Frontend fetches linked accounts grouped by coliveName                  │
│  3. Each property shows its bank accounts with KYC status                   │
│  4. Moderator uploads/re-uploads documents per account per document type    │
│  5. Upload is proxied through to Razorpay via payment-services              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Detailed Sequence Diagram

```
  ┌──────────────┐    ┌──────────────────┐    ┌──────────────────┐    ┌──────────────┐
  │  Dashboard.js │    │  KycController    │    │RouteController   │    │ Razorpay API │
  │  (KYC Tab)    │    │  (reg-services)   │    │(payment-services)│    │              │
  └──────┬───────┘    └────────┬─────────┘    └────────┬─────────┘    └──────┬───────┘
         │                     │                        │                     │
         │ GET /adminservices/ │                        │                     │
         │ kyc/linked-accounts │                        │                     │
         │ ?ownerUsername=X    │                        │                     │
         │────────────────────►│                        │                     │
         │                     │ GET /payments/route/   │                     │
         │                     │ linked-accounts/       │                     │
         │                     │ by-owner?ownerUsername=X│                     │
         │                     │───────────────────────►│                     │
         │                     │                        │ Query MongoDB       │
         │                     │                        │ (LinkedAccount      │
         │                     │                        │  Collection)        │
         │                     │◄───────────────────────│                     │
         │◄────────────────────│                        │                     │
         │                     │                        │                     │
         │ [List of linked     │                        │                     │
         │  accounts grouped   │                        │                     │
         │  by coliveName]     │                        │                     │
         │                     │                        │                     │
         │  ─── User selects a document to upload ───   │                     │
         │                     │                        │                     │
         │ POST /adminservices/│                        │                     │
         │ kyc/linked-accounts/│                        │                     │
         │ {accountId}/        │                        │                     │
         │ upload-document     │                        │                     │
         │ (multipart: file +  │                        │                     │
         │  documentType)      │                        │                     │
         │────────────────────►│                        │                     │
         │                     │ POST /payments/route/  │                     │
         │                     │ linked-accounts/       │                     │
         │                     │ {accountId}/           │                     │
         │                     │ upload-document        │                     │
         │                     │───────────────────────►│                     │
         │                     │                        │ POST Razorpay API   │
         │                     │                        │ /v2/accounts/{id}/  │
         │                     │                        │ documents           │
         │                     │                        │────────────────────►│
         │                     │                        │                     │
         │                     │                        │◄────────────────────│
         │                     │                        │ (docId, status)     │
         │                     │                        │                     │
         │                     │                        │ Save to MongoDB     │
         │                     │                        │ (uploadedDocuments) │
         │                     │◄───────────────────────│                     │
         │◄────────────────────│                        │                     │
         │                     │                        │                     │
         │ [Success: refresh   │                        │                     │
         │  linked accounts]   │                        │                     │
         │                     │                        │                     │
  ──────────────────────────────────────────────────────────────────────────────
```

### Dashboard KYC Tab — UI Structure

```
┌─────────────────────────────────────────────────────────────────────────┐
│  🔐 KYC Documents — Razorpay Verification              [🔄 Refresh]   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │ 🏢 ColiveName Property 1                    (1 linked account)   │  │
│  ├───────────────────────────────────────────────────────────────────┤  │
│  │  Bank: HDFC — ****1234 (John Doe)                                │  │
│  │  [KYC: NOT_SUBMITTED] [NEW] [★ Primary]                         │  │
│  │                                                                   │  │
│  │  ✅ Uploaded: PAN Card, Aadhar Front                             │  │
│  │                                                                   │  │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐             │  │
│  │  │ ✅ PAN Card  │ │ ✅ Aadhar    │ │ 📄 Aadhar    │  ...        │  │
│  │  │ 04/14/2026   │ │ Front        │ │ Back         │             │  │
│  │  │ [🔄 Re-upload│ │ [🔄 Re-upload│ │ [📎 Upload]  │             │  │
│  │  └──────────────┘ └──────────────┘ └──────────────┘             │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │ 🏢 ColiveName Property 2                    (1 linked account)   │  │
│  ├───────────────────────────────────────────────────────────────────┤  │
│  │  Bank: ICICI — ****5678 (Jane Doe)                               │  │
│  │  [KYC: SUBMITTED] [ACTIVATED]                                    │  │
│  │  ...                                                              │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Supported Document Types

| Key | Label | Description |
|-----|-------|-------------|
| `pan` | PAN Card | Permanent Account Number card |
| `aadhar_front` | Aadhar Front | Aadhaar card front side |
| `aadhar_back` | Aadhar Back | Aadhaar card back side |
| `cancelled_cheque` | Cancelled Cheque | For bank verification |
| `gst_certificate` | GST Certificate | GST registration certificate |
| `passport_front` | Passport Front | Passport front page |
| `passport_back` | Passport Back | Passport back page |
| `voter_id_front` | Voter ID Front | Voter ID card front |
| `voter_id_back` | Voter ID Back | Voter ID card back |
| `driving_license_front` | Driving License Front | DL front side |
| `driving_license_back` | Driving License Back | DL back side |
| `business_proof_url` | Business Proof | Business proof document |
| `shop_establishment_certificate` | Shop Certificate | Shop & Establishment cert |
