# Visual Architecture & Flow Diagrams

## Database Relationship Diagram

```
                           ELITE4 SYSTEM ARCHITECTURE

┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                               │
│  ╔═══════════════════════════════════════════════════════════════════════╗  │
│  ║                    users (MongoDB Collection)                         ║  │
│  ║                                                                       ║  │
│  ║  {                                                                    ║  │
│  ║    _id: ObjectId("user_123"),                                        ║  │
│  ║    username: "anupama",                                              ║  │
│  ║    email: "anupama@gmail.com",                                       ║  │
│  ║    roleIds: ["role_id_1"],                                           ║  │
│  ║    clientDetails: [                                                  ║  │
│  ║      {                                                               ║  │
│  ║        coliveName: "elite4CoLiving",                                 ║  │
│  ║        roomOnBoardId: "room_doc_456" ──────────────┐                ║  │
│  ║        clientCategory: "PG",                        │                ║  │
│  ║        uploadedPhotos: [...],                       │                ║  │
│  ║        licenseDocumentsPath: [...]                  │                ║  │
│  ║      }                                              │                ║  │
│  ║    ]                                                │                ║  │
│  ║  }                                                  │                ║  │
│  ╚═══════════════════════════════════════════════════════════════════════╝  │
│                                                                 │             │
│                                                                 │ FK Reference│
│                                                                 ▼             │
│  ╔═══════════════════════════════════════════════════════════════════════╗  │
│  ║              roomOnBoard (MongoDB Collection)                         ║  │
│  ║                                                                       ║  │
│  ║  {                                                                    ║  │
│  ║    _id: ObjectId("room_doc_456"),                                    ║  │
│  ║    rooms: [                                                          ║  │
│  ║      {                                                               ║  │
│  ║        roomType: "SINGLE",                                           ║  │
│  ║        roomNumber: "101",                  ◄─────────────┐           ║  │
│  ║        roomCapacity: 3,                    │             │           ║  │
│  ║        houseNumber: null,                  │             │           ║  │
│  ║        houseType: null,                    │             │           ║  │
│  ║        occupied: "PARTIALLY_OCCUPIED"      │             │           ║  │
│  ║      },                                    │             │           ║  │
│  ║      {                                     │             │           ║  │
│  ║        roomType: "SINGLE",                 │             │           ║  │
│  ║        roomNumber: "102",                  │             │           ║  │
│  ║        roomCapacity: 1,                    │             │           ║  │
│  ║        occupied: "OCCUPIED"                │             │           ║  │
│  ║      }                                     │             │           ║  │
│  ║    ]                                       │             │           ║  │
│  ║  }                                         │             │           ║  │
│  ╚═══════════════════════════════════════════════════════════════════════╝  │
│                                                │             │                 │
│                                                │             │ Used by         │
│                                                │             ▼                 │
│  ╔═══════════════════════════════════════════════════════════════════════╗  │
│  ║           registrations (MongoDB Collection)                          ║  │
│  ║                                                                       ║  │
│  ║  [                                                                    ║  │
│  ║    {                                                                  ║  │
│  ║      _id: "R-uuid1",                                                 ║  │
│  ║      fname: "John",                                                  ║  │
│  ║      lname: "Doe",                                                   ║  │
│  ║      email: "john@example.com",                                      ║  │
│  ║      contactNo: "9876543210",                                        ║  │
│  ║      coliveUserName: "anupama",                                      ║  │
│  ║      coliveName: "elite4CoLiving",                                   ║  │
│  ║      room: {                                                         ║  │
│  ║        roomNumber: "101" ◄─────────────────────────┘                 ║  │
│  ║        roomCapacity: 3,                                              ║  │
│  ║        occupied: "OCCUPIED"                                          ║  │
│  ║      },                                                              ║  │
│  ║      checkInDate: ISODate("2026-03-31"),                             ║  │
│  ║      checkOutDate: null,                                             ║  │
│  ║      occupied: "OCCUPIED",                                           ║  │
│  ║      aadharPhotoPath: "...",                                         ║  │
│  ║      documentUploadPath: "..."                                       ║  │
│  ║    },                                                                ║  │
│  ║    {                                                                  ║  │
│  ║      _id: "R-uuid2",                                                 ║  │
│  ║      fname: "Jane",                                                  ║  │
│  ║      room: {                                                         ║  │
│  ║        roomNumber: "101",                                            ║  │
│  ║        roomCapacity: 3                                               ║  │
│  ║      },                                                              ║  │
│  ║      checkInDate: ISODate("2026-04-01"),                             ║  │
│  ║      checkOutDate: null                                              ║  │
│  ║    }                                                                  ║  │
│  ║  ]                                                                    ║  │
│  ║                                                                       ║  │
│  ║  Room 101 Status:                                                    ║  │
│  ║  - Active registrations: 2 (John, Jane)                              ║  │
│  ║  - Capacity: 3                                                       ║  │
│  ║  - Status: PARTIALLY_OCCUPIED                                        ║  │
│  ║  - Percentage: 66.67%                                                ║  │
│  ╚═══════════════════════════════════════════════════════════════════════╝  │
│                                                                               │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Complete API Flow Sequence Diagram

```
CREATE USER WORKFLOW:

┌──────────────┐
│   Moderator  │
│  (UI Client) │
└───────┬──────┘
        │
        │ POST /signup
        │ {username, coliveDetails with rooms}
        ▼
┌────────────────────────────────┐
│  RegistrationController         │
│  createUser()                   │
└────────┬───────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────┐
│ UserCreationService.createUser()            │
│                                              │
│ 1. Validate username/email/phone            │
│ 2. For each colive:                         │
│    • Create RoomOnBoardDocument             │
│    • Save to MongoDB                        │
│    • Get roomOnBoardId                      │
│ 3. Create ClientAndRoomOnBoardId            │
│    with roomOnBoardId reference             │
│ 4. Create User with clientDetails           │
│ 5. Save User to MongoDB                     │
│                                              │
└────────┬────────────────────────────────────┘
         │
         ▼
┌─────────────────────┐
│  MongoDB: users     │
│  MongoDB: roomOnBoard
└─────────────────────┘
         │
         ▼
┌──────────────────────────────┐
│ UserResponse                 │
│ - id                         │
│ - username                   │
│ - clientNameAndRooms[]       │
│   └─ coliveName              │
│   └─ rooms[]                 │
│   └─ categoryType            │
└──────────────────────────────┘
         │
         ▼
┌──────────────┐
│   Moderator  │
│  (UI Client) │
│  Receives    │
│  Response    │
└──────────────┘

═══════════════════════════════════════════════════════════════

ONBOARD USER WORKFLOW:

┌──────────────┐
│    Member    │
│  (UI Client) │
└───────┬──────┘
        │
        │ POST /registrations/onboardUser
        │ {fname, email, contactNo, 
        │  coliveUserName, room{roomNumber}}
        ▼
┌──────────────────────────────────┐
│  RegistrationController          │
│  createWithFiles()               │
└────────┬───────────────────────┘
         │
         ▼
┌───────────────────────────────────────────────────┐
│ RegistrationService.createWithFiles()             │
│                                                    │
│ 1. Call create(registration, room)                │
│    a. Validate coliveUserName exists              │
│    b. Validate room exists for that coLive        │
│    c. Create RegistrationDocument                 │
│                                                    │
│ 2. Update RoomOnBoardDocument                     │
│    a. Find by roomOnBoardId                       │
│    b. Find room by roomNumber                     │
│    c. Set room.occupied = "OCCUPIED"              │
│    d. Save updated RoomOnBoardDocument            │
│                                                    │
│ 3. Upload files (aadhar, document)                │
│    if provided                                    │
│                                                    │
│ 4. Save file paths to RegistrationDocument        │
│                                                    │
└────────┬──────────────────────────────────────────┘
         │
         ▼
┌────────────────────────────┐
│  MongoDB: registrations    │
│  MongoDB: roomOnBoard      │
│  (UPDATED with occupied)   │
└────────┬───────────────────┘
         │
         ▼
┌──────────────────────────────────┐
│ RegistrationWithRoomRequest      │
│ - id                             │
│ - registration details           │
│ - room details                   │
│ - aadhar/document paths          │
└──────────────────────────────────┘
         │
         ▼
┌──────────────┐
│    Member    │
│  Registered  │
│  Successfully │
└──────────────┘

═══════════════════════════════════════════════════════════════

ROOM AVAILABILITY CHECK WORKFLOW:

┌──────────────┐
│   Dashboard  │
│  (UI Client) │
└───────┬──────┘
        │
        │ GET /registrations/rooms/availability/
        │     anupama/elite4CoLiving
        ▼
┌────────────────────────────────────┐
│  RegistrationController            │
│  getAllRoomsAvailability()          │
└────────┬──────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────┐
│ RoomAvailabilityService.                     │
│ getAllRoomsAvailability()                    │
│                                               │
│ 1. Get User by username                      │
│ 2. Find clientDetails for coliveName         │
│ 3. Get RoomOnBoardDocument                   │
│ 4. For each room:                            │
│    • Call calculateRoomAvailability()        │
│                                               │
└────────┬─────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────┐
│ RoomAvailabilityService.                     │
│ calculateRoomAvailability()                  │
│                                               │
│ 1. Query registrations for this room:        │
│    find({                                    │
│      coliveName: "elite4CoLiving",          │
│      roomNumber: "101",                     │
│      occupied: "OCCUPIED"                   │
│    })                                        │
│                                               │
│ 2. Filter active members:                    │
│    checkOutDate == null OR                  │
│    checkOutDate > today                     │
│                                               │
│ 3. Count: activeMembers.size()              │
│                                               │
│ 4. Determine status:                         │
│    current = 2, capacity = 3                │
│    if (current == 0) → "AVAILABLE"          │
│    else if (current >= capacity)            │
│      → "OCCUPIED"                           │
│    else → "PARTIALLY_OCCUPIED"              │
│                                               │
│ 5. Calculate percentage:                     │
│    (2 / 3) * 100 = 66.67%                   │
│                                               │
│ 6. Return RoomAvailabilityDTO                │
│                                               │
└────────┬─────────────────────────────────────┘
         │
         ▼
┌────────────────────────────────────────────────┐
│ RoomAvailabilityDTO[] Response                 │
│ [                                               │
│   {                                             │
│     coliveName: "elite4CoLiving",              │
│     roomNumber: "101",                         │
│     roomCapacity: 3,                           │
│     currentOccupants: 2,                       │
│     availabilityStatus: "PARTIALLY_OCCUPIED",  │
│     occupancyPercentage: 66.67,                │
│     roomType: "SINGLE"                         │
│   },                                            │
│   {                                             │
│     coliveName: "elite4CoLiving",              │
│     roomNumber: "102",                         │
│     roomCapacity: 1,                           │
│     currentOccupants: 1,                       │
│     availabilityStatus: "OCCUPIED",            │
│     occupancyPercentage: 100.0,                │
│     roomType: "SINGLE"                         │
│   }                                             │
│ ]                                              │
└────────┬───────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────┐
│   Dashboard UI                   │
│                                  │
│  Room 101: 🟡 Partial (2/3)     │
│  ████████░░ 66%                 │
│                                  │
│  Room 102: 🔴 Occupied (1/1)    │
│  ██████████ 100%                │
│                                  │
│  Room 103: 🟢 Available (0/2)   │
│  ░░░░░░░░░░ 0%                  │
└──────────────────────────────────┘

═══════════════════════════════════════════════════════════════

CHECKOUT WORKFLOW:

┌──────────────┐
│    Admin     │
│  (UI Client) │
└───────┬──────┘
        │
        │ PUT /registrations/checkout
        │ {registrationId, checkOutDate}
        ▼
┌───────────────────────────────────┐
│  RegistrationController           │
│  updateCheckOutDateById()         │
└────────┬────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────┐
│ RegistrationService.checkout()               │
│                                               │
│ 1. Find RegistrationDocument by ID           │
│ 2. Set checkOutDate = provided date          │
│ 3. Save updated RegistrationDocument         │
│ 4. Call updateRoomOccupancyAfterCheckout()   │
│                                               │
└────────┬───────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────┐
│ updateRoomOccupancyAfterCheckout()            │
│                                               │
│ 1. Find RoomOnBoardDocument                  │
│ 2. Find the room by roomNumber               │
│ 3. Query active registrations again:         │
│    (excluding members with checkOutDate)    │
│    Result: 1 active member left             │
│                                               │
│ 4. Recalculate status:                       │
│    current = 1, capacity = 3                │
│    1 < 3 → "PARTIALLY_OCCUPIED"             │
│                                               │
│ 5. Update room.occupied                      │
│ 6. Save RoomOnBoardDocument                  │
│                                               │
└────────┬──────────────────────────────────────┘
         │
         ▼
┌────────────────────────────┐
│  MongoDB: registrations    │
│  MongoDB: roomOnBoard      │
│  (UPDATED)                 │
└────────┬───────────────────┘
         │
         ▼
┌──────────────┐
│   Dashboard  │
│  Auto-Update │
│              │
│ Room 101:    │
│ 🟡 Partial   │
│ (1/3 now)    │
└──────────────┘
```

---

## Room Status State Machine

```
┌─────────────────────────────────────────────────────────────┐
│                  ROOM OCCUPANCY STATE MACHINE                │
└─────────────────────────────────────────────────────────────┘

                         START
                           │
                           ▼
                ┌──────────────────────┐
                │    NOT_OCCUPIED      │
                │   (0 / capacity)     │
                └──────────┬───────────┘
                           │
                    First member joins
                    (registerOnBoard)
                           │
                           ▼
                ┌──────────────────────────────┐
                │  PARTIALLY_OCCUPIED          │
                │  (1 to capacity-1 / capacity)│
                └──────┬──────────────┬────────┘
                       │              │
         Member joins  │              │  Member checkout
         (< capacity)  │              │  (still members left)
                       │              │
                       └──┬───────────┘
                          │
           ╔══════════════╩══════════════╗
           ║                             ║
      All members                   Last member
      join up to              joins or final
      capacity -1            member joins
           │                        │
           ▼                        ▼
    ┌──────────────────┐    ┌──────────────────┐
    │     OCCUPIED     │    │     OCCUPIED     │
    │ (>= capacity)    │    │ (>= capacity)    │
    └────────┬─────────┘    └──────┬───────────┘
             │                     │
      Any member              Final member
      checkout               checkout
             │                     │
             ▼                     ▼
    ┌──────────────────┐    ┌──────────────────┐
    │ PARTIALLY_OCCUPIED
    │(members left     │    │  NOT_OCCUPIED    │
    │but < capacity)   │    │   (0 / capacity) │
    └─────────────────┘    └──────────────────┘
             │                     │
             └──────────┬──────────┘
                        │
                      END

TRANSITIONS:
┌─────────────────────────────────────────────┐
│ NOT_OCCUPIED → PARTIALLY_OCCUPIED           │
│   Trigger: registerOnBoard()                │
│   New current = 1                           │
├─────────────────────────────────────────────┤
│ PARTIALLY_OCCUPIED → OCCUPIED               │
│   Trigger: registerOnBoard()                │
│   New current >= capacity                   │
├─────────────────────────────────────────────┤
│ OCCUPIED → PARTIALLY_OCCUPIED               │
│   Trigger: checkout()                       │
│   Remaining = capacity - 1                  │
├─────────────────────────────────────────────┤
│ PARTIALLY_OCCUPIED → NOT_OCCUPIED           │
│   Trigger: checkout()                       │
│   Remaining = 0                             │
├─────────────────────────────────────────────┤
│ OCCUPIED → OCCUPIED                         │
│   Trigger: checkout() or registerOnBoard()  │
│   Always >= capacity                        │
└─────────────────────────────────────────────┘
```

---

## Component Interaction Diagram

```
                    ELITE4 SYSTEM COMPONENTS

┌────────────────────────────────────────────────────────────────┐
│                                                                  │
│                      Frontend (React)                           │
│                                                                  │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │
│   │ Create User  │  │ Onboard Page │  │  Dashboard   │        │
│   │   Form       │  │   Form       │  │              │        │
│   └──────┬───────┘  └──────┬───────┘  └──────┬───────┘        │
│          │                 │                  │                 │
│          │ POST /signup    │ POST /onboard    │ GET /rooms/...  │
│          │                 │                  │                 │
└──────────┼─────────────────┼──────────────────┼────────────────┘
           │                 │                  │
           ▼                 ▼                  ▼
┌────────────────────────────────────────────────────────────────┐
│                    Spring Boot APIs                             │
│                                                                  │
│   ┌──────────────────────────┐  ┌────────────────────────────┐ │
│   │ UserController           │  │ RegistrationController     │ │
│   │ - @PostMapping /signup   │  │ - @PostMapping /onboardUser│ │
│   │ - @GetMapping /admin/... │  │ - @GetMapping /rooms/...   │ │
│   └────────┬─────────────────┘  │ - @PutMapping /checkout    │ │
│            │                    └────────┬──────────────────┘  │
│            └──────────┬────────────────┘                        │
│                       │                                          │
│   ┌───────────────────┴─────────────────────────────────────┐  │
│   │                                                           │  │
│   ▼                   ▼                   ▼                   │  │
│ ┌──────────────┐ ┌─────────────┐ ┌──────────────────────┐   │  │
│ │ UserCreation │ │ Registration│ │ RoomAvailability     │   │  │
│ │ Service      │ │ Service     │ │ Service              │   │  │
│ │              │ │             │ │                      │   │  │
│ │ Methods:     │ │ Methods:    │ │ Methods:             │   │  │
│ │ createUser() │ │ create()    │ │ calculateRoom...()   │   │  │
│ │ addClient()  │ │ createWith  │ │ getAllRooms...()     │   │  │
│ │ updateRoom() │ │ Files()     │ │ getPropertySummary() │   │  │
│ └──────┬───────┘ │ checkout()  │ └──────┬────────────────┘   │  │
│        │         └──────┬──────┘        │                     │  │
│        │                │               │                     │  │
│   ┌────┴────────────────┴───────────────┘                     │  │
│   │                                                            │  │
│   │ Repositories                                              │  │
│   │ ┌──────────────────┐  ┌────────────────┐                │  │
│   │ │ UserRepository   │  │ RegistrationRep│                │  │
│   │ │ - findByUsername │  │ - findBy...()  │                │  │
│   │ │ - save()         │  │ - save()       │                │  │
│   │ └──────┬───────────┘  └────────┬────────┘                │  │
│   │        │                       │                         │  │
│   │        └───────┬───────────────┘                         │  │
│   │                │                                         │  │
│   │                │  ┌─────────────────────────────────┐   │  │
│   │                │  │ RoomsOrHouseRepository          │   │  │
│   │                │  │ - findById()                    │   │  │
│   │                │  │ - save()                        │   │  │
│   │                └→ │ (RoomOnBoardDocument queries)   │   │  │
│   │                   └────────┬──────────────────────────┤  │
│   │                            │                         │  │
│   └────────────────────────────┼─────────────────────────┘  │
│                                │                              │
└────────────────────────────────┼──────────────────────────────┘
                                 │
                                 ▼
┌────────────────────────────────────────────────────────────────┐
│                    MongoDB Database                            │
│                                                                  │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │
│   │ users        │  │ registrations │  │ roomOnBoard  │        │
│   │ collection   │  │ collection   │  │ collection   │        │
│   └──────────────┘  └──────────────┘  └──────────────┘        │
│                                                                  │
└────────────────────────────────────────────────────────────────┘
           │                       │               │
           └───────────────┬───────┴───────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────────────┐
│               File Storage Service                             │
│         (LOCAL / S3 / AZURE BLOB)                              │
│                                                                  │
│   Stores: Aadhar photos, Documents, Property photos            │
│                                                                  │
└────────────────────────────────────────────────────────────────┘
```

---

## Query Performance Optimization

```
BOTTLENECK 1: Room Availability Calculation
Current:
  Query all registrations for room
  Filter in memory

Optimized:
  @Query({
    "coliveName": "...",
    "room.roomNumber": "...",
    "occupied": "OCCUPIED",
    "$or": [
      { "checkOutDate": null },
      { "checkOutDate": { "$gt": new Date() }}
    ]
  })
  
Index recommendation:
  db.registrations.createIndex({
    "coliveName": 1,
    "room.roomNumber": 1,
    "occupied": 1,
    "checkOutDate": 1
  })

─────────────────────────────────────────────────────

BOTTLENECK 2: User Lookup by Username
Current:
  userRepository.findByUsername()

Optimization:
  Already has unique index on username
  No further optimization needed

─────────────────────────────────────────────────────

BOTTLENECK 3: Room History (Checkout Updates)
Current:
  Recalculate from scratch

Future optimization:
  Add room_history collection to track changes
  Query last 30 days of occupancy trends
  
  db.room_history.insertOne({
    roomId: "...",
    date: ISODate(),
    status: "OCCUPIED",
    occupants: 2,
    capacity: 3
  })
```

---

## Error Handling Flow

```
ROOM AVAILABILITY ERROR SCENARIOS:

┌─────────────────────────────────────┐
│ User not found                      │
├─────────────────────────────────────┤
│ Error: "User with username 'xxx'   │
│        not found"                   │
│ HTTP: 404 Not Found                 │
│ Action: Return empty list           │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│ CoLive not found for user           │
├─────────────────────────────────────┤
│ Error: "CoLive 'xxx' not found"    │
│ HTTP: 404 Not Found                 │
│ Action: Return empty list           │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│ RoomOnBoard document missing        │
├─────────────────────────────────────┤
│ Error: "Room data not found"        │
│ HTTP: 404 Not Found                 │
│ Action: Skip this property          │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│ Room capacity <= 0                  │
├─────────────────────────────────────┤
│ Warning: Default to 1               │
│ Action: Calculate with capacity = 1 │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│ Database connection error           │
├─────────────────────────────────────┤
│ Error: "Failed to calculate...error"│
│ HTTP: 500 Internal Server Error     │
│ Action: Return ERROR status in DTO  │
└─────────────────────────────────────┘
```

---

All diagrams visually represent:
1. How data flows through the system
2. How room availability is calculated
3. How components interact
4. Performance optimization points
5. Error handling scenarios

