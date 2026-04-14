# Issue Resolution & Implementation Roadmap

## QUICK REFERENCE FOR YOUR QUESTIONS

### Question 1: Why is `clientNameAndRooms` appearing twice in `/admin/user/{username}` API?

**Root Cause:** The response object has both properties being set from the same data.

**Location:** `UserCreationService.java` line ~862 (in the `toUserResponse()` method)

**Current Code:**
```java
private UserResponse toUserResponse(User user) {
    UserResponse response = new UserResponse();
    // ... other fields ...
    
    Set<ColiveNameAndRooms> coliveNameAndRoomsSet = new HashSet<>();
    // ... populate coliveNameAndRoomsSet ...
    
    response.setClientNameAndRooms(coliveNameAndRoomsSet);  // ← ISSUE HERE
    return response;
}
```

**Solution:** Check the `UserResponse` DTO to see if it has both `clientNameAndRooms` and `coliveNameAndRooms` properties. If yes, remove one or map one instead of both.

**Suggested Fix:**
```java
// Option 1: Only use one property name
response.setClientNameAndRooms(coliveNameAndRoomsSet);  // Keep this
// Don't set coliveNameAndRooms separately

// Option 2: If frontend uses coliveNameAndRooms
response.setColiveNameAndRooms(coliveNameAndRoomsSet);  // Use this name instead
```

---

## IMPLEMENTATION ROADMAP

### Phase 1: Fix Duplicate Issue (5 minutes)
```
File: registration-services/src/main/java/com/elite4/anandan/registrationservices/dto/UserResponse.java
Action: Remove duplicate property or keep only one name
```

### Phase 2: Verify Room Structure (10 minutes)
Check `Room.java` DTO:
```
Ensure it has:
- roomCapacity: int
- occupied: roomOccupied enum with PARTIALLY_OCCUPIED
- roomNumber: String
- houseNumber: String
```

### Phase 3: Create New Classes (15 minutes)
1. Create: `RoomAvailabilityDTO.java`
2. Create: `RoomAvailabilityService.java`

### Phase 4: Update Existing Classes (20 minutes)
1. Update: `RegistrationRepository.java` - Add query methods
2. Update: `RegistrationController.java` - Add 3 endpoints
3. Update: `RegistrationService.java` - Add checkout update logic

### Phase 5: Testing (30 minutes)
1. Test room availability calculation
2. Test checkout flow with occupancy update
3. Test API endpoints

---

## API ENDPOINTS TO ADD

### Endpoint 1: Get All Rooms Availability for a Property
```
GET /registrations/rooms/availability/{coliveUserName}/{coliveName}

Path Variables:
- coliveUserName: String (Property owner's username)
- coliveName: String (Property name)

Response: List<RoomAvailabilityDTO>
[
  {
    "coliveName": "elite4CoLiving",
    "roomNumber": "101",
    "roomCapacity": 3,
    "currentOccupants": 2,
    "availabilityStatus": "PARTIALLY_OCCUPIED",
    "occupancyPercentage": 66.67,
    "roomType": "SINGLE",
    "categoryType": "PG"
  },
  {
    "coliveName": "elite4CoLiving",
    "roomNumber": "102",
    "roomCapacity": 1,
    "currentOccupants": 1,
    "availabilityStatus": "OCCUPIED",
    "occupancyPercentage": 100.0,
    "roomType": "SINGLE",
    "categoryType": "PG"
  },
  {
    "coliveName": "elite4CoLiving",
    "roomNumber": "103",
    "roomCapacity": 2,
    "currentOccupants": 0,
    "availabilityStatus": "AVAILABLE",
    "occupancyPercentage": 0.0,
    "roomType": "SINGLE",
    "categoryType": "PG"
  }
]

Example Curl:
curl -X GET "http://localhost:8080/registrations/rooms/availability/anupama/elite4CoLiving" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json"
```

### Endpoint 2: Get Single Room Availability
```
GET /registrations/rooms/availability/{coliveUserName}/{coliveName}/room/{roomNumber}

Query Parameters:
- roomCapacity: int (optional, default: 1)

Response: RoomAvailabilityDTO
{
  "coliveName": "elite4CoLiving",
  "roomNumber": "101",
  "roomCapacity": 3,
  "currentOccupants": 2,
  "availabilityStatus": "PARTIALLY_OCCUPIED",
  "occupancyPercentage": 66.67,
  "roomType": "SINGLE"
}

Example Curl:
curl -X GET "http://localhost:8080/registrations/rooms/availability/anupama/elite4CoLiving/room/101?roomCapacity=3" \
  -H "Authorization: Bearer <token>"
```

### Endpoint 3: Get Property Occupancy Summary
```
GET /registrations/rooms/occupancy-summary/{coliveUserName}

Path Variables:
- coliveUserName: String (Property owner's username)

Response: List<PropertyOccupancySummaryDTO>
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
  },
  {
    "coliveName": "nandans residency",
    "categoryType": "HOUSE",
    "totalRooms": 5,
    "totalCapacity": 10,
    "totalOccupants": 8,
    "fullyOccupiedCount": 4,
    "partiallyOccupiedCount": 0,
    "availableCount": 1,
    "overallOccupancyPercentage": 80.0
  }
]

Example Curl:
curl -X GET "http://localhost:8080/registrations/rooms/occupancy-summary/anupama" \
  -H "Authorization: Bearer <token>"
```

---

## DATA FLOW DIAGRAM

```
USER CREATION FLOW:
┌─────────────┐
│   Moderator │
└──────┬──────┘
       │
       ▼
   CreateUser API
   POST /signup
   {
     username: "anupama",
     coliveDetails: [{
       coliveName: "elite4CoLiving",
       rooms: [{
         roomNumber: "101",
         roomCapacity: 3    ← IMPORTANT: Set capacity here
       }]
     }]
   }
       │
       ▼
   ┌─────────────────────────────┐
   │ Create RoomOnBoardDocument  │
   │ Save in MongoDB             │
   └─────────────────────────────┘
       │
       ▼
   ┌─────────────────────────────┐
   │ Create User Document        │
   │ Reference RoomOnBoardId     │
   └─────────────────────────────┘

MEMBER ONBOARDING FLOW:
┌─────────────┐
│   Member    │
└──────┬──────┘
       │
       ▼
   OnboardUser API
   POST /registrations/onboardUser
   {
     fname: "John",
     coliveUserName: "anupama",
     room: {
       roomNumber: "101"
     }
   }
       │
       ▼
   ┌──────────────────────────────────────┐
   │ Create RegistrationDocument          │
   │ room.occupied = "OCCUPIED"           │
   │ Save in MongoDB                      │
   └──────────────────────────────────────┘
       │
       ▼
   ┌──────────────────────────────────────┐
   │ Update RoomOnBoardDocument           │
   │ Set room 101 occupied = OCCUPIED     │
   │ Save updated document                │
   └──────────────────────────────────────┘

ROOM AVAILABILITY CHECK (DASHBOARD):
┌───────────┐
│ Dashboard │
└─────┬─────┘
      │
      ▼
  Call GET /registrations/rooms/availability/
  anupama/elite4CoLiving
      │
      ▼
  ┌─────────────────────────────────────┐
  │ RoomAvailabilityService             │
  │ For each room:                       │
  │ 1. Query registrations for room 101 │
  │ 2. Count active members (not       │
  │    checked out)                     │
  │ 3. current = 2, capacity = 3       │
  │ 4. status = "PARTIALLY_OCCUPIED"   │
  └─────────────────────────────────────┘
      │
      ▼
  Return to UI:
  {
    availabilityStatus: "PARTIALLY_OCCUPIED",
    occupancyPercentage: 66.67
  }
      │
      ▼
  ┌─────────────────────────────────────┐
  │ UI displays badge:                  │
  │ 🟡 Partial (2/3)                    │
  └─────────────────────────────────────┘

MEMBER CHECKOUT FLOW:
┌────────┐
│ Admin  │
└───┬────┘
    │
    ▼
Checkout API
PUT /registrations/checkout
{
  registrationId: "R-xxx",
  checkOutDate: "2026-04-15"
}
    │
    ▼
┌─────────────────────────────────┐
│ Update RegistrationDocument     │
│ Set checkOutDate = "2026-04-15" │
└─────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────┐
│ updateRoomOccupancyAfterCheckout│
│ 1. Query active registrations   │
│    for room 101                 │
│ 2. Count = 1 (one member left)  │
│ 3. 1 < 3, so set PARTIALLY_OCC. │
│ 4. Update RoomOnBoardDocument   │
└─────────────────────────────────┘
    │
    ▼
Dashboard refreshes → 
Shows "PARTIALLY_OCCUPIED" (1/3)
```

---

## DATABASE SCHEMA

### Room Collection Update Required

**Current:**
```json
{
  "_id": ObjectId(),
  "rooms": [
    {
      "roomType": "SINGLE",
      "roomNumber": "101",
      "occupied": "NOT_OCCUPIED"
    }
  ]
}
```

**Updated (Add roomCapacity):**
```json
{
  "_id": ObjectId(),
  "rooms": [
    {
      "roomType": "SINGLE",
      "roomNumber": "101",
      "roomCapacity": 3,  ← NEW FIELD
      "occupied": "NOT_OCCUPIED"
    }
  ]
}
```

### Registration Document (Already Correct)
```json
{
  "_id": "R-xxx",
  "fname": "John",
  "coliveName": "elite4CoLiving",
  "coliveUserName": "anupama",
  "room": {
    "roomNumber": "101",
    "roomCapacity": 3,
    "occupied": "OCCUPIED"
  },
  "checkInDate": ISODate("2026-03-31"),
  "checkOutDate": null,  ← null until member leaves
  "occupied": "OCCUPIED"
}
```

---

## TESTING CHECKLIST

### Unit Tests to Add
```
1. RoomAvailabilityService.calculateRoomAvailability()
   - Test with 0 occupants → "AVAILABLE"
   - Test with 1-2 occupants (capacity 3) → "PARTIALLY_OCCUPIED"
   - Test with 3+ occupants (capacity 3) → "OCCUPIED"

2. RoomAvailabilityService.updateRoomOccupancyAfterCheckout()
   - Test checkout updates room status
   - Test all members checkout → "NOT_OCCUPIED"
   - Test partial checkout → "PARTIALLY_OCCUPIED"

3. RegistrationService.createWithFiles()
   - Verify room occupancy updated after onboarding
```

### Integration Tests to Add
```
1. Test full flow:
   - Create user with room capacity 3
   - Onboard first member → room should be "PARTIALLY_OCCUPIED"
   - Onboard second member → room should be "PARTIALLY_OCCUPIED"
   - Onboard third member → room should be "OCCUPIED"
   - Checkout one member → room should be "PARTIALLY_OCCUPIED"
   - Checkout all members → room should be "NOT_OCCUPIED"

2. Test API endpoints:
   - GET /registrations/rooms/availability/{username}/{coliveName}
   - GET /registrations/rooms/occupancy-summary/{username}
```

### Manual Testing
```
1. Open Postman/Insomnia
2. Create user with room capacity
3. Onboard members one by one
4. Call availability endpoint after each onboarding
5. Verify status changes correctly
6. Checkout members
7. Verify status updates in real-time
```

---

## MIGRATION GUIDE FOR EXISTING DATA

If you have existing rooms without roomCapacity:

```javascript
// MongoDB Migration Script
db.roomOnBoard.updateMany(
  {
    "rooms": { $exists: true }
  },
  [
    {
      $set: {
        "rooms": {
          $map: {
            input: "$rooms",
            as: "room",
            in: {
              $mergeObjects: [
                "$$room",
                {
                  roomCapacity: 1  // Default to 1
                }
              ]
            }
          }
        }
      }
    }
  ]
)

// Or for houses with different capacity
db.roomOnBoard.updateMany(
  {
    "rooms.houseNumber": { $exists: true }
  },
  [
    {
      $set: {
        "rooms": {
          $map: {
            input: "$rooms",
            as: "room",
            in: {
              $mergeObjects: [
                "$$room",
                {
                  roomCapacity: {
                    $cond: [
                      { $eq: ["$$room.houseType", "ONE_RK"] },
                      1,
                      { $cond: [
                          { $eq: ["$$room.houseType", "TWO_RK"] },
                          2,
                          3
                        ]
                      }
                    ]
                  }
                }
              ]
            }
          }
        }
      }
    }
  ]
)
```

---

## CONFIGURATION

### Add to application.properties if needed:
```properties
# Room availability caching (optional)
room.availability.cache.enabled=true
room.availability.cache.ttl.minutes=5
```

---

## TROUBLESHOOTING

### Issue: Room status not updating after checkout
**Solution:** 
1. Check if `updateRoomOccupancyAfterCheckout()` is being called
2. Verify MongoDB has the updated room record
3. Check if active members count is correct

### Issue: API returns ERROR status
**Solution:**
1. Check logs for detailed error message
2. Verify user and property exist
3. Verify room capacity is set correctly

### Issue: Occupancy percentage incorrect
**Solution:**
1. Verify room capacity is > 0
2. Check active members count
3. Formula: (currentOccupants / roomCapacity) * 100

---

## NEXT STEPS

1. ✅ Review this document
2. ✅ Create RoomAvailabilityDTO.java
3. ✅ Create RoomAvailabilityService.java
4. ✅ Update Room.java (add roomCapacity and PARTIALLY_OCCUPIED)
5. ✅ Update RegistrationRepository.java
6. ✅ Update RegistrationController.java
7. ✅ Update RegistrationService.java checkout logic
8. ✅ Run tests
9. ✅ Deploy and verify in UI

---

## SUMMARY

**What You'll Get:**
- ✅ Room availability status (AVAILABLE/PARTIALLY_OCCUPIED/OCCUPIED)
- ✅ Real-time occupancy tracking
- ✅ Dashboard-friendly API endpoints
- ✅ Property-level occupancy summary
- ✅ Automatic status updates on member checkout
- ✅ Support for both rooms and houses
- ✅ Occupancy percentage for progress bars

**Key Metrics:**
- Capacity management per room
- Active member count tracking
- Occupancy percentage calculation
- Property-wide statistics
- Quick availability lookup

