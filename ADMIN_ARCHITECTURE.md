# Admin Architecture — Change Summary

## Overview
This document captures every backend and frontend change made to implement:
- Admin Groups (app-wide ROLE_ADMIN via group membership)
- RSA RS256 JWT upgrade with JWKS public endpoint
- New `ROLE_CARETAKER` role
- Bulk property-access assignment (user → many properties)
- Admin-initiated user creation (reuses existing Registration page + approve endpoint)
- Admin "Users" tab in the dashboard

**Key design constraint**: Maximum reuse of existing codebase, APIs, and DB documents.

---

## Database Changes

### New collection: `admin_groups`
Only **one** new MongoDB collection was added.

| Field | Type | Notes |
|---|---|---|
| `_id` | ObjectId | auto |
| `groupName` | String | unique index |
| `description` | String | optional |
| `superAdmin` | boolean | true = system group, cannot be deleted |
| `memberUsernames` | Set\<String\> | references `users.username` |
| `createdBy` | String | username of creator |
| `createdAt` / `updatedAt` | Instant | auto-managed |

The `users` and `roles` collections are **unchanged**.

---

## Backend Changes (`registration-services`)

### New Files

| File | Purpose |
|---|---|
| `model/AdminGroup.java` | MongoDB document for the `admin_groups` collection |
| `repository/AdminGroupRepository.java` | Spring Data Mongo repo; key methods: `findByGroupName`, `existsByMemberUsernamesContaining`, `findByMemberUsernamesContaining` |
| `dto/AdminGroupResponse.java` | API response DTO (id, groupName, description, superAdmin, memberUsernames, memberCount, createdBy, timestamps) |
| `dto/BulkPropertyAccessRequest.java` | Request DTO: `targetUsername` + `List<PropertyAccessAssignmentRequest> assignments` |

### Modified Files

#### `model/EmployeeRole.java`
- Added `ROLE_CARETAKER` to the enum.

#### `security/JwtTokenProvider.java` — **full rewrite**
- Migrated from HMAC HS256 to **RSA RS256** (asymmetric signing).
- Reads PEM keys from `app.jwt.rsa.private-key` / `app.jwt.rsa.public-key` (env: `JWT_RSA_PRIVATE_KEY` / `JWT_RSA_PUBLIC_KEY`).
- **If keys are absent** (blank), auto-generates an ephemeral 2048-bit key pair at startup — safe for local dev, logs a warning. Tokens are invalidated on restart in this mode.
- Exposes `getPublicKey() → RSAPublicKey` used by the JWKS endpoint.
- PEM parsing: strips headers, Base64-decodes, uses `PKCS8EncodedKeySpec` (private) / `X509EncodedKeySpec` (public).

#### `security/JwtAuthenticationFilter.java`
- Constructor now receives `AdminGroupRepository`.
- After resolving a user's DB roles, checks `adminGroupRepository.existsByMemberUsernamesContaining(username)`.
- If the user is in **any** admin group, `ROLE_ADMIN` is added to their Spring Security authorities — no change to the `users` document needed.

#### `config/SecurityConfig.java`
- Added `/adminservices/.well-known/jwks.json` to `permitAll()` so consumers can fetch the public key without authentication.

#### `service/AdminBootstrap.java` — **full rewrite**
- Seeds all roles from `EmployeeRole.values()` (including `ROLE_CARETAKER`).
- Seeds the `super-admin` AdminGroup on first startup with `admin` as the first member.
- Constructor now receives `AdminGroupRepository`.

#### `service/UserCreationService.java`
- Removed auto-activation block: `if (request.getRoleIds().contains("ROLE_USER")) user.setActive(true)` was deleted.
- All self-registered users now start inactive and require explicit admin approval. Only admin-initiated creation (via Registration.js admin-mode flag + approve endpoint) bypasses this.

#### `service/AdminService.java`
- Constructor now receives `AdminGroupRepository`.
- **New methods** (all reuse existing `UserRepository` / `RoleRepository` patterns):

| Method | Description |
|---|---|
| `listAdminGroups()` | Returns all groups as `AdminGroupResponse` |
| `createAdminGroup(name, description, createdBy)` | Creates group; rejects duplicate names |
| `deleteAdminGroup(groupId)` | Deletes group; blocks super-admin group deletion |
| `updateGroupMember(groupId, username, action)` | `action = "add"` or `"remove"`; validates user exists; prevents removing the last super-admin member |
| `bulkUpsertPropertyAccess(BulkPropertyAccessRequest)` | Loops and delegates to existing `upsertPropertyAccess()` for each entry — zero duplication of assignment logic |

#### `controller/UserController.java`
- Constructor now receives `JwtTokenProvider`.
- **New endpoints**:

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/.well-known/jwks.json` | Public | JWKS with RSA public key (kty, use, alg, kid, n, e) |
| `GET` | `/admin/groups` | `ROLE_ADMIN` | List all admin groups |
| `POST` | `/admin/groups` | `ROLE_ADMIN` | Create group — body: `{groupName, description}` |
| `DELETE` | `/admin/groups/{groupId}` | `ROLE_ADMIN` | Delete group (blocked for super-admin) |
| `PUT` | `/admin/groups/{groupId}/members/{username}` | `ROLE_ADMIN` | Add or remove member — `?action=add\|remove` |
| `POST` | `/admin/bulk-property-access` | `ROLE_ADMIN` | Assign one user to many properties at once |

#### `src/main/resources/application.properties`
```properties
# RSA JWT keys (leave blank for ephemeral dev key pair)
app.jwt.rsa.private-key=${JWT_RSA_PRIVATE_KEY:}
app.jwt.rsa.public-key=${JWT_RSA_PUBLIC_KEY:}
app.jwt.expiration-ms=${JWT_EXPIRATION_MS:86400000}
```
Old `app.jwt.secret` property is no longer read by `JwtTokenProvider` and can be removed from environment.

---

## Frontend Changes (`elite4UI`)

### `client/src/Admin.js`

#### New "Users" nav tab
Button added to the navbar: `activePage === 'users'`.

#### New state
```
adminGroups, adminGroupsLoading       — admin groups list
newGroupName, newGroupDesc            — create-group form
groupActionLoading                    — per-action busy flags
addMemberInputs                       — per-group add-member input values
usersPageSearch                       — search filter for users table
```
> All user-list data reuses the existing `accessUsers` state fetched by `fetchAccessUsers()`.

#### New `fetchAdminGroups` callback
Calls `GET /adminservices/admin/groups`. Wrapped in `useCallback([getAuth, showToast])` to be safe as a useEffect dependency.

#### New `useEffect` for users page
```js
useEffect(() => {
  if (activePage !== 'users') return;
  fetchAccessUsers();   // reused — fetches /admin/active-users
  fetchAdminGroups();
}, [activePage, fetchAccessUsers, fetchAdminGroups]);
```

#### New `filteredUsersPageUsers` useMemo
Filters `accessUsers` by `usersPageSearch` (username / email / phone).

#### Users page render (`activePage === 'users'`)
Two-column layout reusing existing `adm-access__layout` / `adm-sched-card` CSS classes:

**Left panel — Active Users**
- Search input
- **"+ Create User"** button → stores `adminCreatingUser=true` in sessionStorage → navigates to `/registration` (existing page, zero duplication)
- Table of all active users with "Assign Property →" button that pre-sets `selectedAccessUser` and switches to the existing Property Access tab

**Right panel — Admin Groups**
- Refresh button
- Create group form (name + description + create button)
- For each group: name badge, description, member chips with ✕ remove button, add-member input, delete group button (disabled for super-admin group)

### `client/src/Registration.js`

Added **admin-mode handling** in both the *staff* and *owner* success paths:

```js
if (sessionStorage.getItem('adminCreatingUser') === 'true') {
  sessionStorage.removeItem('adminCreatingUser');
  // Auto-approve using admin's existing session token
  await fetch(`.../admin/approve/${username}`, { headers: { Authorization: `${adminTokenType} ${adminToken}` } });
  window.location.href = '/admin';
  return;
}
```

This reuses:
- Existing `/signup-with-files` endpoint for creation
- Existing `/admin/approve/{username}` endpoint for activation
- No new backend endpoint required

---

## Existing APIs Reused (No Changes)

| API | Reused By |
|---|---|
| `POST /adminservices/signup-with-files` | Admin "Create User" flow (via Registration.js) |
| `POST /adminservices/admin/approve/{username}` | Auto-approve after admin-initiated signup |
| `GET /adminservices/admin/active-users` | Users tab user list |
| `GET /adminservices/admin/colives?search=` | Property search in Property Access tab |
| `PUT /adminservices/admin/property-access` | Assignment (called per-entry inside `bulkUpsertPropertyAccess`) |
| `DELETE /adminservices/admin/property-access` | Remove assignment |
| `GET /adminservices/admin/pending-approvals` | Approvals tab |

---

## ownerUsername / coliveUserName

The `ownerUsername` variable is **unchanged throughout all APIs and DB documents**.  
It maps to `coliveUserName` in the frontend and identifies the property owner in every property-access request.

---

## Production Deployment Notes

1. **Generate RSA key pair** (PKCS#8 + X.509):
   ```bash
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
   openssl rsa -pubout -in private.pem -out public.pem
   # Strip headers and newlines for the env var:
   grep -v "^-" private.pem | tr -d '\n'  # → JWT_RSA_PRIVATE_KEY
   grep -v "^-" public.pem  | tr -d '\n'  # → JWT_RSA_PUBLIC_KEY
   ```
2. Set env vars `JWT_RSA_PRIVATE_KEY` and `JWT_RSA_PUBLIC_KEY` on all instances sharing the same key pair.
3. Existing HMAC-signed tokens (`app.jwt.secret`) will be **rejected** after upgrade — users will need to log in again.
4. JWKS endpoint: `GET /adminservices/.well-known/jwks.json` — no auth required.

---

## Super-Admin Bootstrap

On first startup, `AdminBootstrap` creates:
- A user `admin` (password from env `ADMIN_PASSWORD`, default `Admin@123`)
- All roles from `EmployeeRole` enum
- An `admin_groups` document `{ groupName: "super-admin", superAdmin: true, memberUsernames: ["admin"] }`

The `admin` user automatically receives `ROLE_ADMIN` on every request via the filter's group-membership check.
