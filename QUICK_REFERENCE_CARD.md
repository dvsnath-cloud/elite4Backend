# QUICK REFERENCE CARD

## Your 3 Questions - Direct Answers

### Q1: How does CreateUser API work and how is room data stored?

**API Endpoint:** `POST /signup` (UserController)

**Flow:**
```
1. Validate username, email, phone (must be unique)
2. For each coLive in request:
   ├─ Create RoomOnBoardDocument with rooms list
   ├─ Save to MongoDB → get room_doc_id
   └─ Store room_doc_id in user.clientDetails.roomOnBoardId
3. Create User document
4. Save User and return UserResponse
```

**Database After:**
```
users collection:
{
  _id: user_id,
  username: "anupama",
  clientDetails: [{
    coliveName: "elite4CoLiving",
    roomOnBoardId: "room_doc_456"  ← Reference
  }]
}

roomOnBoard collection:
{
  _id: "room_doc_456",
  rooms: [{
    roomNumber: "101",
    roomCapacity: 3,           ← IMPORTANT
    occupied: "NOT_OCCUPIED"
  }]
}
```

---

### Q2: How does OnboardUser API work and how is room data stored?

**API Endpoint:** `POST /registrations/onboardUser` (RegistrationController)

**Flow:**
```
1. Validate:
   ├─ coliveUserName exists in users
   ├─ coliveName assigned to that user
   ├─ room exists for that coLive
   └─ Only room OR house (not both)

2. Create RegistrationDocument

3. UPDATE RoomOnBoardDocument:
   ├─ Find room by roomNumber
   ├─ Set occupied = "OCCUPIED"
   └─ Save updated document

4. Upload files (aadhar, document)

5. Return RegistrationWithRoomRequest
```

**Database After:**
```
registrations collection:
{
  _id: "R-xxx",
  fname: "John",
  coliveUserName: "anupama",
  room: {
    roomNumber: "101",
    roomCapacity: 3,
    occupied: "OCCUPIED"
  },
  checkInDate: "2026-03-31",
  checkOutDate: null
}

roomOnBoard collection (UPDATED):
{
  _id: "room_doc_456",
  rooms: [{
    roomNumber: "101",
    roomCapacity: 3,
    occupied: "OCCUPIED"  ← Changed!
  }]
}
```

---

### Q3: How to show room availability (Available/Partial/Occupied)?

**Solution:** Create RoomAvailabilityService

**Logic:**
```
1. Query all registrations for room 101
2. Filter active members (checkOutDate = null or future)
3. Count = 2 members
4. roomCapacity = 3

if (count == 0) → "AVAILABLE"
else if (count >= 3) → "OCCUPIED"  
else → "PARTIALLY_OCCUPIED"

percentage = (2 / 3) * 100 = 66.67%
```

**API Endpoints (3 new):**

```
1. GET /registrations/rooms/availability/{username}/{coliveName}
   Returns: List of all rooms with status
   
2. GET /registrations/rooms/availability/{username}/{coliveName}/room/{roomNumber}
   Returns: Single room availability
   
3. GET /registrations/rooms/occupancy-summary/{username}
   Returns: Property-level summary
```

**Response Example:**
```json
{
  "coliveName": "elite4CoLiving",
  "roomNumber": "101",
  "roomCapacity": 3,
  "currentOccupants": 2,
  "availabilityStatus": "PARTIALLY_OCCUPIED",
  "occupancyPercentage": 66.67
}
```

---

## The Duplicate Issue

**Problem:** UserResponse has `clientNameAndRooms` appearing twice

**Root Cause:** Line ~862 in UserCreationService.toUserResponse()

**Fix:**
```
Check UserResponse DTO - remove one duplicate property
OR
Only call: response.setClientNameAndRooms(data)
NOT both: setClientNameAndRooms() and setColiveNameAndRooms()
```

---

## Implementation Checklist (105 minutes)

```
☐ Phase 1: Fix Duplicate (5 min)
  └─ UserResponse.java

☐ Phase 2: Add Fields (5 min)
  └─ Room.java: add roomCapacity

☐ Phase 3: Create Classes (20 min)
  ├─ RoomAvailabilityDTO.java
  └─ RoomAvailabilityService.java

☐ Phase 4: Update Existing (25 min)
  ├─ RegistrationRepository.java
  ├─ RegistrationController.java
  └─ RegistrationService.java

☐ Phase 5: Test (30 min)
  ├─ Unit tests
  ├─ Integration tests
  └─ Manual tests

☐ Phase 6: Verify (15 min)
  └─ All working
```

---

## 3 New Code Methods

### Method 1: RoomAvailabilityService.calculateRoomAvailability()
```java
public RoomAvailabilityDTO calculateRoomAvailability(
    String coliveName, String coliveUserName, 
    String roomNumber, Integer roomCapacity) {
    
    // Query registrations for this room
    List<RegistrationDocument> activeMembers = 
        registrationRepository.findActive(coliveName, coliveUserName, roomNumber);
    
    int current = activeMembers.size();
    String status = current == 0 ? "AVAILABLE" 
                  : current >= roomCapacity ? "OCCUPIED"
                  : "PARTIALLY_OCCUPIED";
    
    return RoomAvailabilityDTO.builder()
        .coliveName(coliveName)
        .roomNumber(roomNumber)
        .roomCapacity(roomCapacity)
        .currentOccupants(current)
        .availabilityStatus(status)
        .occupancyPercentage((double) current / roomCapacity * 100)
        .build();
}
```

### Method 2: RoomAvailabilityService.getAllRoomsAvailability()
```java
public List<RoomAvailabilityDTO> getAllRoomsAvailability(
    String coliveUserName, String coliveName) {
    
    // Get user and their properties
    User user = userRepository.findByUsername(coliveUserName).get();
    Set<ClientAndRoomOnBoardId> clients = user.getClientDetails();
    
    List<RoomAvailabilityDTO> result = new ArrayList<>();
    
    for (ClientAndRoomOnBoardId client : clients) {
        if (client.getColiveName().equals(coliveName)) {
            RoomOnBoardDocument roomDoc = 
                roomsOrHouseRepository.findById(client.getRoomOnBoardId()).get();
            
            for (Room room : roomDoc.getRooms()) {
                RoomAvailabilityDTO dto = calculateRoomAvailability(
                    coliveName, coliveUserName,
                    room.getRoomNumber(), room.getRoomCapacity());
                result.add(dto);
            }
        }
    }
    
    return result;
}
```

### Method 3: RegistrationService.updateRoomOccupancyAfterCheckout()
```java
private void updateRoomOccupancyAfterCheckout(
    RegistrationDocument registrationDoc) {
    
    // Get RoomOnBoardDocument
    RoomOnBoardDocument roomDoc = 
        roomsOrHouseRepository.findById(roomId).get();
    
    // Query active members again (post-checkout)
    List<RegistrationDocument> activeMembers = 
        registrationRepository.findByRoomNumberAndNotCheckedOut(
            registrationDoc.getRoom().getRoomNumber());
    
    int remaining = activeMembers.size();
    String newStatus = remaining == 0 ? "NOT_OCCUPIED"
                     : remaining < capacity ? "PARTIALLY_OCCUPIED"
                     : "OCCUPIED";
    
    // Update room and save
    room.setOccupied(newStatus);
    roomsOrHouseRepository.save(roomDoc);
}
```

---

## 3 New API Endpoints

### Endpoint 1: Get All Rooms Availability
```
GET /registrations/rooms/availability/anupama/elite4CoLiving

Response:
[
  {
    "coliveName": "elite4CoLiving",
    "roomNumber": "101",
    "roomCapacity": 3,
    "currentOccupants": 2,
    "availabilityStatus": "PARTIALLY_OCCUPIED",
    "occupancyPercentage": 66.67
  },
  {
    "coliveName": "elite4CoLiving",
    "roomNumber": "102",
    "roomCapacity": 1,
    "currentOccupants": 1,
    "availabilityStatus": "OCCUPIED",
    "occupancyPercentage": 100.0
  },
  {
    "coliveName": "elite4CoLiving",
    "roomNumber": "103",
    "roomCapacity": 2,
    "currentOccupants": 0,
    "availabilityStatus": "AVAILABLE",
    "occupancyPercentage": 0.0
  }
]
```

### Endpoint 2: Get Single Room Availability
```
GET /registrations/rooms/availability/anupama/elite4CoLiving/room/101?roomCapacity=3

Response:
{
  "coliveName": "elite4CoLiving",
  "roomNumber": "101",
  "roomCapacity": 3,
  "currentOccupants": 2,
  "availabilityStatus": "PARTIALLY_OCCUPIED",
  "occupancyPercentage": 66.67
}
```

### Endpoint 3: Get Property Summary
```
GET /registrations/rooms/occupancy-summary/anupama

Response:
[
  {
    "coliveName": "elite4CoLiving",
    "categoryType": "PG",
    "totalRooms": 10,
    "totalCapacity": 15,
    "totalOccupants": 10,
    "fullyOccupiedCount": 7,
    "partiallyOccupiedCount": 1,
    "availableCount": 2,
    "overallOccupancyPercentage": 66.67
  }
]
```

---

## Room Status Transitions

```
NOT_OCCUPIED ─[member joins]─> PARTIALLY_OCCUPIED
                                       │
                                       │
           ┌───────────────────────────┼──────────────────────┐
           │                           │                      │
      [more join]              [member leaves]        [final member leaves]
           │                           │                      │
           ▼                           ▼                      ▼
        OCCUPIED           PARTIALLY_OCCUPIED           NOT_OCCUPIED
```

---

## MongoDB Query Patterns

### Get Active Members in Room
```javascript
db.registrations.find({
  "coliveName": "elite4CoLiving",
  "room.roomNumber": "101",
  "$or": [
    { "checkOutDate": null },
    { "checkOutDate": { "$gt": new Date() } }
  ]
})
```

### Update Room Status
```javascript
db.roomOnBoard.updateOne(
  { "_id": ObjectId("room_doc_456"), "rooms.roomNumber": "101" },
  { "$set": { "rooms.$.occupied": "PARTIALLY_OCCUPIED" } }
)
```

### Count Occupants
```javascript
db.registrations.countDocuments({
  "coliveName": "elite4CoLiving",
  "room.roomNumber": "101",
  "occupied": "OCCUPIED",
  "$or": [
    { "checkOutDate": null },
    { "checkOutDate": { "$gt": new Date() } }
  ]
})
```

---

## Files to Create/Modify

### Create (NEW)
```
RoomAvailabilityDTO.java          (DTO)
RoomAvailabilityService.java      (Service)
```

### Update
```
Room.java                         (Add roomCapacity)
RegistrationRepository.java       (Add queries)
RegistrationController.java       (Add endpoints)
RegistrationService.java          (Add checkout logic)
UserCreationService.java          (Fix duplicate)
```

---

## Test Cases

```
✓ Room with 0 members → AVAILABLE
✓ Room with 1-2 members (capacity 3) → PARTIALLY_OCCUPIED
✓ Room with 3+ members (capacity 3) → OCCUPIED
✓ Member checkout updates room status
✓ Last member checkout → NOT_OCCUPIED
✓ Query returns correct occupancy percentage
✓ API endpoints work correctly
✓ Error handling on missing data
```

---

## Dependencies to Add (if needed)

Already in your project:
- Spring Boot
- MongoDB
- Lombok
- Jackson (JSON)

No new dependencies needed!

---

## Performance Notes

```
Query optimization:
  db.registrations.createIndex({
    "coliveName": 1,
    "room.roomNumber": 1,
    "occupied": 1,
    "checkOutDate": 1
  })

Caching opportunity:
  Cache availability for 5 minutes
  Recalculate on register/checkout events
```

---

## Next Steps (In Order)

1. Read README_DOCUMENTATION_INDEX.md
2. Read COMPLETE_SUMMARY.md
3. Create RoomAvailabilityDTO.java
4. Create RoomAvailabilityService.java
5. Update Room.java
6. Update RegistrationRepository.java
7. Update RegistrationController.java
8. Update RegistrationService.java
9. Update UserCreationService.java (fix duplicate)
10. Run tests
11. Deploy

---

**Total Time to Read All Docs: 40 minutes**
**Total Time to Implement: 105 minutes**
**Total Time: 145 minutes (~2.5 hours)**

Start with README_DOCUMENTATION_INDEX.md → Choose your reading path 🚀

