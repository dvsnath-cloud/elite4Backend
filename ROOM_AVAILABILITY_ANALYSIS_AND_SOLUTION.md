# Elite4 Room Availability Feature - Complete Analysis & Implementation Guide

## TABLE OF CONTENTS
1. [Current API Flows](#current-api-flows)
2. [Data Storage Structure](#data-storage-structure)
3. [Issue Analysis](#issue-analysis)
4. [Room Availability Solution](#room-availability-solution)
5. [Implementation Steps](#implementation-steps)
6. [Code Examples](#code-examples)

---

## CURRENT API FLOWS

### 1. CREATE USER API FLOW (UserCreationService.createUser())

```
REQUEST:
POST /signup
{
  "username": "anupama",
  "email": "anupama@gmail.com",
  "password": "***",
  "phoneNumber": "+918904108571",
  "roleIds": ["ROLE_MODERATOR"],
  "coliveDetails": [
    {
      "coliveName": "elite4CoLiving",
      "categoryType": "PG",
      "rooms": [
        {
          "roomType": "SINGLE",
          "roomNumber": "101",
          "roomCapacity": 1,
          "occupied": "NOT_OCCUPIED"
        }
      ]
    }
  ]
}

FLOW:
┌─────────────────────────────────────────────────────────────────┐
│ 1. VALIDATION PHASE                                               │
│    - Check username uniqueness                                    │
│    - Check email uniqueness                                       │
│    - Check phone uniqueness (convert to E.164 format)             │
└─────────────────────────────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────────────────────────────┐
│ 2. ROOM ONBOARD DOCUMENT CREATION                                │
│    - For each coliveDetail:                                      │
│      • Create RoomOnBoardDocument with rooms list               │
│      • Save to MongoDB collection: "roomOnBoard"                │
│      • Get roomOnBoardId (MongoDB _id)                          │
└─────────────────────────────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────────────────────────────┐
│ 3. FILE UPLOAD PHASE (OPTIONAL)                                  │
│    - Upload property photos to file storage                      │
│    - Upload license documents to file storage                    │
│    - Store file paths in ClientAndRoomOnBoardId                 │
└─────────────────────────────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────────────────────────────┐
│ 4. CLIENT DETAILS MAPPING                                         │
│    - Create ClientAndRoomOnBoardId object:                      │
│      {                                                            │
│        coliveName: "elite4CoLiving",                            │
│        roomOnBoardId: "<MongoDB_ID>",                            │
│        clientCategory: "PG",                                     │
│        uploadedPhotos: [...],                                    │
│        licenseDocumentsPath: [...]                               │
│      }                                                            │
│    - Add to user.clientDetails Set                              │
└─────────────────────────────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────────────────────────────┐
│ 5. USER CREATION                                                  │
│    - Create User document:                                       │
│      {                                                            │
│        _id: ObjectId,                                            │
│        username: "anupama",                                      │
│        email: "anupama@gmail.com",                               │
│        roleIds: ["role_id_1"],                                   │
│        clientDetails: [ClientAndRoomOnBoardId],                 │
│        ...                                                        │
│      }                                                            │
│    - Save to MongoDB collection: "users"                         │
└─────────────────────────────────────────────────────────────────┘
           ↓
RESPONSE: UserResponse with clientNameAndRooms data
```

**Database AFTER CreateUser:**

```
Collection: users
{
  "_id": "user_id_123",
  "username": "anupama",
  "email": "anupama@gmail.com",
  "clientDetails": [
    {
      "coliveName": "elite4CoLiving",
      "roomOnBoardId": "room_doc_id_456",  // Reference to roomOnBoard collection
      "clientCategory": "PG",
      "uploadedPhotos": [...],
      "licenseDocumentsPath": [...]
    }
  ]
}

Collection: roomOnBoard
{
  "_id": "room_doc_id_456",
  "rooms": [
    {
      "roomType": "SINGLE",
      "roomNumber": "101",
      "roomCapacity": 1,
      "occupied": "NOT_OCCUPIED"
    }
  ]
}
```

---

### 2. ONBOARD USER API FLOW (RegistrationService.createWithFiles())

```
REQUEST:
POST /registrations/onboardUser
{
  "registration": {
    "fname": "John",
    "lname": "Doe",
    "email": "john@example.com",
    "contactNo": "9876543210",
    "coliveUserName": "anupama",        // Reference to User who owns the colive
    "coliveName": "elite4CoLiving",
    "gender": "MALE",
    "checkInDate": "2026-03-31",
    "address": "...",
    "pincode": "..."
  },
  "room": {
    "roomNumber": "101"
  },
  "aadharPhoto": <file>,
  "documentUpload": <file>
}

FLOW:
┌─────────────────────────────────────────────────────────────────┐
│ 1. VALIDATION PHASE                                               │
│    - Check coliveUserName exists in users collection             │
│    - Check coliveName is assigned to that user                   │
│    - Check room number exists for that coLive                    │
│    - Validate only room OR house number provided (not both)     │
└─────────────────────────────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────────────────────────────┐
│ 2. REGISTRATION CREATION                                          │
│    - Create RegistrationDocument with:                           │
│      • fname, lname, email, contactNo                            │
│      • coliveUserName, coliveName                                │
│      • room details (roomNumber or houseNumber)                  │
│      • occupied: "OCCUPIED"                                      │
│    - Save to MongoDB collection: "registrations"                 │
└─────────────────────────────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────────────────────────────┐
│ 3. UPDATE ROOM OCCUPANCY IN ROOMONBOARDDOCUMENT                 │
│    - Fetch RoomOnBoardDocument by roomOnBoardId                 │
│    - Find matching room (by roomNumber or houseNumber)          │
│    - Set room.occupied = "OCCUPIED"                             │
│    - Save updated RoomOnBoardDocument                            │
└─────────────────────────────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────────────────────────────┐
│ 4. FILE UPLOAD PHASE (OPTIONAL)                                  │
│    - Upload aadhar photo to file storage                         │
│    - Upload document to file storage                             │
│    - Store file paths in RegistrationDocument                   │
│    - Save to MongoDB                                             │
└─────────────────────────────────────────────────────────────────┘
           ↓
RESPONSE: RegistrationWithRoomRequest with registration ID and room details
```

**Database AFTER OnboardUser:**

```
Collection: registrations
{
  "_id": "R-<uuid>",
  "fname": "John",
  "lname": "Doe",
  "email": "john@example.com",
  "contactNo": "9876543210",
  "coliveUserName": "anupama",
  "coliveName": "elite4CoLiving",
  "room": {
    "roomNumber": "101",
    "roomCapacity": 1,
    "occupied": "OCCUPIED"
  },
  "checkInDate": "2026-03-31",
  "occupied": "OCCUPIED",
  "aadharPhotoPath": "...",
  "documentUploadPath": "..."
}

Collection: roomOnBoard (UPDATED)
{
  "_id": "room_doc_id_456",
  "rooms": [
    {
      "roomType": "SINGLE",
      "roomNumber": "101",
      "roomCapacity": 1,
      "occupied": "OCCUPIED"  // ← Changed from NOT_OCCUPIED
    }
  ]
}
```

---

## DATA STORAGE STRUCTURE

### MongoDB Collections Relationship

```
┌──────────────────────────┐
│   users (Collection)     │
├──────────────────────────┤
│ _id: ObjectId            │
│ username: String         │
│ email: String            │
│ clientDetails: [         │  ─────┐
│   {                      │       │
│     coliveName: String   │       │ One-to-Many
│     roomOnBoardId: Ref ──┼─────┐│
│     clientCategory: ...  │     ││
│   }                      │     ││
│ ]                        │     ││
└──────────────────────────┘     ││
                                 ││
                    ┌────────────┘│
                    │             │
                    ↓             │
┌──────────────────────────┐      │
│ roomOnBoard (Collection) │      │
├──────────────────────────┤      │
│ _id: ObjectId            │ ←────┘
│ rooms: [                 │
│   {                      │
│     roomNumber: String   │
│     roomCapacity: Int    │
│     occupied: String     │
│     roomType: Enum       │
│   }                      │
│ ]                        │
└──────────────────────────┘
            ↑
            │ (One-to-Many)
            │ Find by roomNumber
            │
┌──────────────────────────┐
│ registrations (Collection)
├──────────────────────────┤
│ _id: ObjectId            │
│ fname, lname: String     │
│ coliveName: String       │
│ coliveUserName: String   │
│ room: Object             │
│   - roomNumber: String   │
│   - roomCapacity: Int    │
│   - occupied: String     │
│ checkInDate: Date        │
│ checkOutDate: Date       │
│ occupied: String         │
│ aadharPhotoPath: String  │
│ documentUploadPath: String
└──────────────────────────┘
```

---

## ISSUE ANALYSIS

### Problem Identified: `/admin/user/{username}` API Returns Duplicate `clientNameAndRooms`

**Current Issue in UserCreationService.toUserResponse():**

```java
response.setClientNameAndRooms(coliveNameAndRoomsSet);
```

The response contains `clientNameAndRooms` which is a duplicate of `coliveNameAndRooms` (which is already in the response).

**From the API Response:**
```json
{
  "clientNameAndRooms": [...],  // Duplicate set
  "roleNames": [...],
  ...
}
```

**Root Cause:** The response builder is creating both properties. Check if there's a `coliveNameAndRooms` property in the response DTO that needs to be removed.

---

## ROOM AVAILABILITY SOLUTION

### Problem Statement
**UI Requirement:**
- Show room availability status based on room capacity and current occupants
- Logic:
  - **AVAILABLE**: 0 members in room (roomCapacity = 3, members = 0)
  - **PARTIALLY_OCCUPIED**: 1 or more but less than capacity (roomCapacity = 3, members = 1-2)
  - **OCCUPIED**: Room is at or exceeds capacity (roomCapacity = 3, members ≥ 3)

### Solution Architecture

#### Step 1: Add RoomCapacity Field to Room Model (if not exists)
```java
@Data
public class Room {
    private String roomNumber;
    private String houseNumber;
    private int roomCapacity;        // ← NEW FIELD (how many can stay)
    private RoomType roomType;
    private HouseType houseType;
    private roomOccupied occupied;   // ← Current status: OCCUPIED, NOT_OCCUPIED
    
    public enum roomOccupied {
        NOT_OCCUPIED, OCCUPIED, PARTIALLY_OCCUPIED, VACATED
    }
}
```

#### Step 2: Create New DTO for Room Availability Status
```java
@Data
@Builder
public class RoomAvailabilityDTO {
    private String coliveName;
    private String roomNumber;
    private String houseNumber;
    private Integer roomCapacity;
    private Integer currentOccupants;
    private String availabilityStatus;  // AVAILABLE, PARTIALLY_OCCUPIED, OCCUPIED
    private Double occupancyPercentage;
    private String roomType;
    private String houseType;
}
```

#### Step 3: Query to Count Current Occupants
In `RegistrationRepository`, add method:
```java
// Count how many active (non-checked-out) members in a room
@Query(value = "{ 'coliveName': ?0, 'coliveUserName': ?1, 'room.roomNumber': ?2, " +
               "'occupied': 'OCCUPIED', '$or': [{'checkOutDate': null}, {'checkOutDate': {$gt: new Date()}}]}", 
       count = true)
long countActiveOccupantsInRoom(String coliveName, String coliveUserName, String roomNumber);

// House version
@Query(value = "{ 'coliveName': ?0, 'coliveUserName': ?1, 'room.houseNumber': ?2, " +
               "'occupied': 'OCCUPIED', '$or': [{'checkOutDate': null}, {'checkOutDate': {$gt: new Date()}}]}", 
       count = true)
long countActiveOccupantsInHouse(String coliveName, String coliveUserName, String houseNumber);

// Find all registrations for a room
List<RegistrationDocument> findByColiveNameAndColiveUserNameAndRoomRoomNumberAndOccupied(
    String coliveName, String coliveUserName, String roomNumber, Registration.roomOccupied occupied);
```

#### Step 4: Create Service Method to Calculate Availability
In `RegistrationService`:
```java
public RoomAvailabilityDTO calculateRoomAvailability(String coliveUserName, String coliveName, 
                                                      String roomNumber, Integer roomCapacity) {
    // Count current occupants
    List<RegistrationDocument> activeMembers = 
        registrationRepository.findByColiveNameAndColiveUserNameAndRoomRoomNumberAndOccupied(
            coliveName, coliveUserName, roomNumber, Registration.roomOccupied.OCCUPIED);
    
    // Filter out checked-out members
    List<RegistrationDocument> actualOccupants = activeMembers.stream()
        .filter(reg -> reg.getCheckOutDate() == null || reg.getCheckOutDate().after(new Date()))
        .collect(Collectors.toList());
    
    int currentOccupants = actualOccupants.size();
    
    // Calculate availability status
    String status = "AVAILABLE";
    if (currentOccupants > 0 && currentOccupants < roomCapacity) {
        status = "PARTIALLY_OCCUPIED";
    } else if (currentOccupants >= roomCapacity) {
        status = "OCCUPIED";
    }
    
    double occupancyPercentage = (double) currentOccupants / roomCapacity * 100;
    
    return RoomAvailabilityDTO.builder()
        .coliveName(coliveName)
        .roomNumber(roomNumber)
        .roomCapacity(roomCapacity)
        .currentOccupants(currentOccupants)
        .availabilityStatus(status)
        .occupancyPercentage(occupancyPercentage)
        .build();
}

// Get all rooms availability for a colive
public List<RoomAvailabilityDTO> getAllRoomsAvailability(String coliveUserName, String coliveName) {
    // Fetch user and their room details
    Optional<User> userOpt = userRepository.findByUsername(coliveUserName);
    if (userOpt.isEmpty()) {
        return Collections.emptyList();
    }
    
    User user = userOpt.get();
    Set<ClientAndRoomOnBoardId> clientDetails = user.getClientDetails();
    
    List<RoomAvailabilityDTO> availabilityList = new ArrayList<>();
    
    for (ClientAndRoomOnBoardId client : clientDetails) {
        if (client.getColiveName().equals(coliveName)) {
            Optional<RoomOnBoardDocument> roomDocOpt = 
                roomsOrHouseRepository.findById(client.getRoomOnBoardId());
            
            if (roomDocOpt.isPresent()) {
                Set<Room> rooms = roomDocOpt.get().getRooms();
                for (Room room : rooms) {
                    if (room.getRoomNumber() != null) {
                        RoomAvailabilityDTO availability = 
                            calculateRoomAvailability(coliveUserName, coliveName, 
                                                      room.getRoomNumber(), room.getRoomCapacity());
                        availability.setRoomType(room.getRoomType().toString());
                        availabilityList.add(availability);
                    } else if (room.getHouseNumber() != null) {
                        RoomAvailabilityDTO availability = 
                            calculateRoomAvailability(coliveUserName, coliveName, 
                                                      room.getHouseNumber(), room.getRoomCapacity());
                        availability.setHouseType(room.getHouseType().toString());
                        availabilityList.add(availability);
                    }
                }
            }
        }
    }
    
    return availabilityList;
}
```

#### Step 5: Create REST Controller Endpoint
```java
@GetMapping("/rooms/availability/{coliveUserName}/{coliveName}")
@PreAuthorize("hasAnyRole('ADMIN','MODERATOR','USER')")
public ResponseEntity<List<RoomAvailabilityDTO>> getRoomsAvailability(
        @PathVariable String coliveUserName,
        @PathVariable String coliveName) {
    List<RoomAvailabilityDTO> availability = 
        registrationService.getAllRoomsAvailability(coliveUserName, coliveName);
    return ResponseEntity.ok(availability);
}

@GetMapping("/rooms/availability/{coliveUserName}/{coliveName}/{roomNumber}")
@PreAuthorize("hasAnyRole('ADMIN','MODERATOR','USER')")
public ResponseEntity<RoomAvailabilityDTO> getRoomAvailability(
        @PathVariable String coliveUserName,
        @PathVariable String coliveName,
        @PathVariable String roomNumber,
        @RequestParam Integer roomCapacity) {
    RoomAvailabilityDTO availability = 
        registrationService.calculateRoomAvailability(coliveUserName, coliveName, 
                                                      roomNumber, roomCapacity);
    return ResponseEntity.ok(availability);
}
```

#### Step 6: Update Room Status on Checkout
In `RegistrationService.checkout()`, after checking out a member, recalculate room status:

```java
// After updating member checkout date
Optional<RoomOnBoardDocument> roomDoc = 
    roomsOrHouseRepository.findById(clientDetail.getRoomOnBoardId());

if (roomDoc.isPresent()) {
    Set<Room> rooms = roomDoc.get().getRooms();
    for (Room room : rooms) {
        if (room.getRoomNumber().equals(registrationDoc.getRoomForRegistration().getRoomNumber())) {
            // Recalculate occupancy
            List<RegistrationDocument> remainingOccupants = 
                registrationRepository.findByColiveNameAndColiveUserNameAndRoomRoomNumberAndOccupied(
                    registrationDoc.getColiveName(),
                    registrationDoc.getColiveUserName(),
                    room.getRoomNumber(),
                    Registration.roomOccupied.OCCUPIED);
            
            int count = (int) remainingOccupants.stream()
                .filter(r -> r.getCheckOutDate() == null || r.getCheckOutDate().after(new Date()))
                .count();
            
            if (count == 0) {
                room.setOccupied(Room.roomOccupied.NOT_OCCUPIED);
            } else if (count < room.getRoomCapacity()) {
                room.setOccupied(Room.roomOccupied.PARTIALLY_OCCUPIED);
            } else {
                room.setOccupied(Room.roomOccupied.OCCUPIED);
            }
            
            break;
        }
    }
    roomsOrHouseRepository.save(roomDoc.get());
}
```

---

## IMPLEMENTATION STEPS

### Phase 1: Database & Model Updates
1. ✅ Ensure `Room` class has `roomCapacity` field
2. ✅ Add `PARTIALLY_OCCUPIED` status to `Room.roomOccupied` enum
3. ✅ Create `RoomAvailabilityDTO` class

### Phase 2: Repository Enhancements
1. Add query methods to `RegistrationRepository` for counting active occupants
2. Add custom MongoDB queries for filtering by checkout date

### Phase 3: Service Layer Implementation
1. Implement `calculateRoomAvailability()` method
2. Implement `getAllRoomsAvailability()` method
3. Update existing checkout logic to recalculate room status

### Phase 4: Controller & API Endpoints
1. Create new endpoints for room availability
2. Add proper authorization checks
3. Add API documentation

### Phase 5: Testing
1. Unit tests for availability calculation logic
2. Integration tests for API endpoints
3. Test edge cases (null checkout dates, multiple members, etc.)

---

## CODE EXAMPLES

### Example 1: Database Query for Active Occupants
```java
// MongoDB Query
db.registrations.find({
    "coliveName": "elite4CoLiving",
    "coliveUserName": "anupama",
    "room.roomNumber": "101",
    "occupied": "OCCUPIED",
    "$or": [
        { "checkOutDate": null },
        { "checkOutDate": { "$gt": new Date() } }
    ]
})
```

### Example 2: API Response Structure
```json
// Request: GET /registrations/rooms/availability/anupama/elite4CoLiving
{
  "status": 200,
  "data": [
    {
      "coliveName": "elite4CoLiving",
      "roomNumber": "101",
      "roomCapacity": 3,
      "currentOccupants": 2,
      "availabilityStatus": "PARTIALLY_OCCUPIED",
      "occupancyPercentage": 66.67,
      "roomType": "SINGLE"
    },
    {
      "coliveName": "elite4CoLiving",
      "roomNumber": "102",
      "roomCapacity": 3,
      "currentOccupants": 3,
      "availabilityStatus": "OCCUPIED",
      "occupancyPercentage": 100.0,
      "roomType": "SINGLE"
    },
    {
      "coliveName": "elite4CoLiving",
      "roomNumber": "103",
      "roomCapacity": 2,
      "currentOccupants": 0,
      "availabilityStatus": "AVAILABLE",
      "occupancyPercentage": 0.0,
      "roomType": "SINGLE"
    }
  ]
}
```

### Example 3: UI Dashboard Display Logic
```javascript
// Frontend React Component
function RoomAvailabilityCard({ room }) {
  const getStatusColor = (status) => {
    switch(status) {
      case 'AVAILABLE': return 'green';
      case 'PARTIALLY_OCCUPIED': return 'orange';
      case 'OCCUPIED': return 'red';
      default: return 'gray';
    }
  };

  const getStatusIcon = (status) => {
    switch(status) {
      case 'AVAILABLE': return '✓ Available';
      case 'PARTIALLY_OCCUPIED': return '⚠ Partial';
      case 'OCCUPIED': return '✗ Full';
      default: return '?';
    }
  };

  return (
    <div style={{ borderColor: getStatusColor(room.availabilityStatus) }}>
      <h3>Room {room.roomNumber}</h3>
      <p>Capacity: {room.roomCapacity}</p>
      <p>Current Occupants: {room.currentOccupants}</p>
      <p>Status: {getStatusIcon(room.availabilityStatus)}</p>
      <div className="progress-bar">
        <div 
          className="fill" 
          style={{ width: room.occupancyPercentage + '%' }}
        >
          {room.occupancyPercentage.toFixed(1)}%
        </div>
      </div>
    </div>
  );
}
```

---

## SUMMARY

### Data Flow for Room Availability
```
1. CREATE USER (Moderator)
   └─→ Define rooms with roomCapacity in RoomOnBoardDocument
   
2. ONBOARD USER (Registration)
   └─→ Member joins room
   └─→ RegistrationDocument created with room details
   └─→ Room status updated based on current occupants

3. GET ROOM AVAILABILITY (Dashboard)
   └─→ Query registrations for this room (active, not checked out)
   └─→ Count current occupants
   └─→ Calculate status: AVAILABLE / PARTIALLY_OCCUPIED / OCCUPIED
   └─→ Return to UI for display

4. CHECKOUT MEMBER (Admin)
   └─→ Set checkOutDate on RegistrationDocument
   └─→ Recalculate room occupancy
   └─→ Update room status if necessary
   └─→ Update UI automatically
```

This architecture ensures:
- ✅ Scalable room availability tracking
- ✅ Real-time occupancy updates
- ✅ Accurate room status for dashboard
- ✅ Easy capacity management
- ✅ Support for multiple property types (PG, House, Flat, Hostel)

