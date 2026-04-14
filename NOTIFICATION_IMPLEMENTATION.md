# CoLive Connect — Notification Implementation Guide

## Overview

All business events in CoLive Connect trigger multi-channel notifications via **Email**, **SMS (Twilio)**, and **WhatsApp (Meta Cloud API)**. Telegram is supported where chat IDs are available. All notification calls are fire-and-forget (wrapped in try-catch) and never block the main business flow.

---

## Notification Channels

| Channel | Provider | Config Location |
|---------|----------|-----------------|
| Email | Spring Mail (SMTP) | `notification-services/src/main/resources/application.properties` |
| SMS | Twilio | `notification-services/src/main/resources/application.properties` |
| WhatsApp | Meta Cloud API (Graph API v19.0) | `notification-services/src/main/resources/application.properties` |
| Telegram | Telegram Bot API | `notification-services/src/main/resources/application.properties` |

---

## Notification Events

### 1. CoLive User Signup / Registration

| Detail | Value |
|--------|-------|
| **Trigger** | New colive owner signs up via `/adminservices/signup-with-files` |
| **File** | `registration-services/.../service/UserCreationService.java` |
| **Recipients** | Owner (confirmation email/SMS/WhatsApp) |
| **Message** | If pending approval: "Registration received, pending moderator approval" with property names. If active: "Welcome, account is now active" |

### 2. New Property Added

| Detail | Value |
|--------|-------|
| **Trigger** | Property added via `/adminservices/addClientToUser-with-files` |
| **File** | `registration-services/.../service/UserCreationService.java` |
| **Recipients** | Owner |
| **Message** | "New property '{name}' has been successfully added to your CoLive Connect account" |

### 3. Tenant Onboarded

| Detail | Value |
|--------|-------|
| **Trigger** | Tenant registered via `/registrations/onboardUser` |
| **File** | `registration-services/.../service/RegistrationService.java` |
| **Recipients** | **Tenant** + **CoLive Owner** |
| **Tenant Message** | "Welcome to CoLive Connect! You have been successfully onboarded" |
| **Owner Message** | "New tenant '{name}' onboarded to property '{colive}', Room: {room}. Contact: {phone}" |

### 4. Moderator Approves User

| Detail | Value |
|--------|-------|
| **Trigger** | Admin/moderator approves via `/adminservices/admin/approve/{username}` |
| **File** | `registration-services/.../service/AdminService.java` |
| **Recipients** | Approved user |
| **Message** | "Your account has been approved. Properties: {list}. You can now log in and manage your colive" |

### 5. Moderator Rejects User

| Detail | Value |
|--------|-------|
| **Trigger** | Admin/moderator rejects via `/adminservices/admin/reject/{username}` |
| **File** | `registration-services/.../service/AdminService.java` |
| **Recipients** | Rejected user |
| **Message** | "Your registration has been reviewed and rejected. Please contact support" |

### 6. User Deactivated

| Detail | Value |
|--------|-------|
| **Trigger** | Admin deactivates via `/adminservices/admin/deactivate/{username}` |
| **File** | `registration-services/.../service/AdminService.java` |
| **Recipients** | Deactivated user |
| **Message** | "Your account has been deactivated. Contact admin if you believe this is an error" |

### 7. Pending Approval Request (to Admins/Moderators)

| Detail | Value |
|--------|-------|
| **Trigger** | New colive owner registers with non-USER role (requires approval) |
| **File** | `registration-services/.../service/UserCreationService.java` |
| **Recipients** | All active admins and moderators |
| **Message** | "New CoLive owner '{username}' has registered and is awaiting your approval. Email: {email}, Phone: {phone}" |

### 8. Cash Payment Recorded

| Detail | Value |
|--------|-------|
| **Trigger** | Cash payment via `/rentpayments/cash` |
| **File** | `registration-services/.../service/RentPaymentService.java` |
| **Recipients** | **Tenant** + **CoLive Owner** |
| **Tenant Message** | "Your rent payment of ₹{amount} for {colive} (Room: {room}) has been recorded. Status: {status}. Method: CASH" |
| **Owner Message** | "Payment of ₹{amount} received from tenant {name} for {colive} (Room: {room}). Status: {status}. Method: CASH" |

### 9. Online Payment Recorded

| Detail | Value |
|--------|-------|
| **Trigger** | Online payment via `/rentpayments/online` |
| **File** | `registration-services/.../service/RentPaymentService.java` |
| **Recipients** | **Tenant** + **CoLive Owner** |
| **Message** | Same as cash payment but with the specific online payment method |

### 10. Prorated Payment Recorded

| Detail | Value |
|--------|-------|
| **Trigger** | Prorated/partial month payment via `/rentpayments/prorated-cash` |
| **File** | `registration-services/.../service/RentPaymentService.java` |
| **Recipients** | **Tenant** + **CoLive Owner** |
| **Message** | Payment recorded with "Pending Approval" status note |

### 11. Payment Approved / Rejected by Moderator

| Detail | Value |
|--------|-------|
| **Trigger** | Moderator action via `/rentpayments/approve-reject` |
| **File** | `registration-services/.../service/RentPaymentService.java` |
| **Recipients** | **Tenant** + **CoLive Owner** |
| **Tenant Message** | "Your payment of ₹{amount} has been {approved/rejected}. Remarks: {remarks}" |
| **Owner Message** | "Payment by tenant {name} has been {approved/rejected} by moderator {moderator}" |

### 12. Transfer Request Created

| Detail | Value |
|--------|-------|
| **Trigger** | Transfer request via `/registrations/transfer/request` |
| **File** | `registration-services/.../service/TransferService.java` |
| **Recipients** | **Tenant** + **Source CoLive Owner** |
| **Tenant Message** | "Transfer request created from {source} (Room: {from}) to {destination} (Room: {to}). Awaiting approval" |
| **Owner Message** | "Transfer requires your approval. Tenant {name} wants to transfer from your property. Please review" |

### 13. Transfer Source Approved

| Detail | Value |
|--------|-------|
| **Trigger** | Source owner approves via `/registrations/transfer/{id}/approve` |
| **File** | `registration-services/.../service/TransferService.java` |
| **Recipients** | **Tenant** + **Destination CoLive Owner** |
| **Tenant Message** | "Source property has approved. Awaiting destination property approval" |
| **Dest Owner Message** | "Transfer requires your approval. Source property has already approved" |

### 14. Transfer Completed

| Detail | Value |
|--------|-------|
| **Trigger** | Destination owner approves via `/registrations/transfer/{id}/approve` (step 2) |
| **File** | `registration-services/.../service/TransferService.java` |
| **Recipients** | **Tenant** + **Source Owner** + **Destination Owner** |
| **Tenant Message** | "Transfer completed. Welcome to your new room!" |
| **Source Owner** | "Tenant transferred out. Room is now available" |
| **Dest Owner** | "Tenant successfully transferred to your property" |

### 15. Transfer Rejected

| Detail | Value |
|--------|-------|
| **Trigger** | Owner rejects via `/registrations/transfer/{id}/reject` |
| **File** | `registration-services/.../service/TransferService.java` |
| **Recipients** | **Tenant** + **Both CoLive Owners** |
| **Message** | "Transfer request rejected. Reason: {reason}" |

---

## Architecture

```
┌──────────────────────┐     REST calls      ┌──────────────────────────┐
│  registration-services│ ──────────────────► │  notification-services   │
│  (Port 8082)          │                     │  (Port 8084)             │
│                       │                     │                          │
│  NotificationClient   │  POST /notifications│  NotificationController  │
│  .sendEmail()         │  /email             │  NotificationService     │
│  .sendSms()           │  /sms               │   ├─ sendEmail()  (SMTP) │
│  .sendWhatsapp()      │  /whatsapp          │   ├─ sendSms()   (Twilio)│
│  .sendTelegram()      │  /telegram          │   ├─ sendWhatsapp()(Meta)│
│                       │                     │   └─ sendTelegram()(Bot) │
└──────────────────────┘                     └──────────────────────────┘
```

### Files Modified

| File | Changes |
|------|---------|
| `UserCreationService.java` | Added NotificationClient + signup, property add, pending approval notifications |
| `RegistrationService.java` | Added owner notification on tenant onboarding |
| `AdminService.java` | Enhanced approval notifications, added rejection + deactivation notifications |
| `RentPaymentService.java` | Added NotificationClient + all payment event notifications |
| `TransferService.java` | Added NotificationClient + UserRepository + all transfer event notifications |
| `NotificationService.java` | Implemented WhatsApp (Meta Cloud API) + Telegram (Bot API) |
| `NotificationController.java` | Wired WhatsApp + Telegram endpoints (202 ACCEPTED) |
| `NotificationRequest.java` | Added `chatId` field for Telegram |
| `application.properties` | Updated WhatsApp + Telegram config keys |

---

## Configuration Required

### Email (SMTP)
```properties
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your-email@gmail.com
spring.mail.password=your-app-password
```

### SMS (Twilio)
```properties
twilio.sid=your-twilio-account-sid
twilio.token=your-twilio-auth-token
twilio.from=+1234567890
```

### WhatsApp (Meta Cloud API)
```properties
whatsapp.api.url=https://graph.facebook.com/v19.0
whatsapp.api.token=YOUR_PERMANENT_ACCESS_TOKEN
whatsapp.phone.number.id=YOUR_PHONE_NUMBER_ID
```
Setup: https://developers.facebook.com → WhatsApp → API Setup

### Telegram (Bot API)
```properties
telegram.bot.token=YOUR_BOT_TOKEN
```
Setup: Message @BotFather on Telegram to create a bot and get the token.
