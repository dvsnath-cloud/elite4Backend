package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.document.RegistrationDocument;
import com.elite4.anandan.registrationservices.document.RoomOnBoardDocument;
import com.elite4.anandan.registrationservices.dto.ClientAndRoomOnBoardId;
import com.elite4.anandan.registrationservices.dto.Registration;
import com.elite4.anandan.registrationservices.dto.RegistrationWithRoomRequest;
import com.elite4.anandan.registrationservices.dto.Room;
import com.elite4.anandan.registrationservices.model.User;
import com.elite4.anandan.registrationservices.repository.RegistrationRepository;
import com.elite4.anandan.registrationservices.repository.RoomsOrHouseRepository;
import com.elite4.anandan.registrationservices.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Service;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RegistrationService {
    private final RegistrationRepository registrationRepository;
    private final NotificationClient notificationClient;
    private final UserRepository userRepository;
    private final RoomsOrHouseRepository  roomsOrHouseRepository;

    public RegistrationWithRoomRequest create(Registration dto, Room room) {
        // Validate that clientUserName is provided (required field)
        if (dto.getClientUserName() == null || dto.getClientUserName().trim().isEmpty()) {
            throw new IllegalArgumentException("Client username must be provided for registration");
        }

        // Validate that fname, clientName, and contactNo combination doesn't already exist
        List<RegistrationDocument> existingRegistrations = registrationRepository
                .findByFnameAndClientNameAndContactNo(dto.getFname(), dto.getClientName(), dto.getContactNo());
        if (!existingRegistrations.isEmpty()) {
            throw new IllegalArgumentException(
                    "Registration already exists with fname '" + dto.getFname() +
                            "', clientName '" + dto.getClientName() +
                            "', and contactNo '" + dto.getContactNo() + "'"
            );
        }

        // Validate that only one of room number or house number is provided
        if ((room.getRoomNumber() != null && !room.getRoomNumber().trim().isEmpty()) &&
            (room.getHouseNumber() != null && !room.getHouseNumber().trim().isEmpty())) {
            throw new IllegalArgumentException("Both room number and house number cannot be provided together");
        } else if ((room.getRoomNumber() == null || room.getRoomNumber().trim().isEmpty()) &&
                   (room.getHouseNumber() == null || room.getHouseNumber().trim().isEmpty())) {
            throw new IllegalArgumentException("Either room number or house number must be provided");
        }

        // Validate that clientUsername exists in UserRepository
        Optional<User> clientUser = userRepository.findByUsername(dto.getClientUserName());
        if (clientUser.isEmpty()) {
            throw new IllegalArgumentException(
                "Client username '" + dto.getClientUserName() + "' does not exist in the system. Please create a user with this client name first."
            );
        }

        // Validate that the provided room number/house number exists for this client
        User user = clientUser.get();
        Set<ClientAndRoomOnBoardId> clientAndRoomOnBoardIds = user.getClientDetails();

        if (clientAndRoomOnBoardIds == null || clientAndRoomOnBoardIds.isEmpty()) {
            throw new IllegalArgumentException(
                "Client username '" + dto.getClientUserName() + "' does not have any rooms assigned"
            );
        }

        boolean roomFound = false;
        StringBuilder availableRooms = new StringBuilder();
        String id = null;
        for (ClientAndRoomOnBoardId clientDetail : clientAndRoomOnBoardIds) {
            if(clientDetail.getClientName().equals(dto.getClientName())) {
                if (clientDetail.getRoomOnBoardId() == null) {
                    continue;
                }

                Optional<RoomOnBoardDocument> roomOnBoardDocument = roomsOrHouseRepository.findById(clientDetail.getRoomOnBoardId());

                if (roomOnBoardDocument.isPresent()) {
                    Set<Room> rooms = roomOnBoardDocument.get().getRooms();

                    // Debug logging to check if rooms are loaded
                    log.info("Found RoomOnBoardDocument with ID: {}, rooms count: {}",
                             clientDetail.getRoomOnBoardId(),
                             rooms != null ? rooms.size() : 0);

                    if (rooms == null || rooms.isEmpty()) {
                        log.warn("RoomOnBoardDocument {} has no rooms assigned", clientDetail.getRoomOnBoardId());
                        continue;
                    }

                    for (Room availableRoom : rooms) {
                        // Check if the provided room number matches
                        if (room.getRoomNumber() != null &&
                                !room.getRoomNumber().isBlank() &&
                                availableRoom.getRoomNumber()!=null&&
                                availableRoom.getRoomNumber().equals(room.getRoomNumber().trim().toString())) {
                            availableRooms.append(availableRoom.getRoomNumber()).append(" ");
                            log.info("Provided room number '{}' exists with client '{}' - proceeding with registration",
                                    room.getRoomNumber(), dto.getClientUserName());
                            roomFound = true;
                            id = "R" + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
                            room.setOccupied(Room.roomOccupied.OCCUPIED);
                            availableRoom.setOccupied(Room.roomOccupied.OCCUPIED);
                            rooms.add(availableRoom);
                            break;
                        }
                        // Check if the provided house number matches
                        else if (room.getHouseNumber() != null &&
                                !room.getHouseNumber().isBlank() &&
                                availableRoom.getHouseNumber()!=null &&
                                availableRoom.getHouseNumber().equals(room.getHouseNumber())) {
                            availableRooms.append(availableRoom.getHouseNumber()).append(" ");
                            log.info("Provided house number '{}' exists with client '{}' - proceeding with registration",
                                    room.getHouseNumber(), dto.getClientUserName());
                            roomFound = true;
                            id = "H" + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
                            room.setOccupied(Room.roomOccupied.OCCUPIED);
                            availableRoom.setOccupied(Room.roomOccupied.OCCUPIED);
                            rooms.add(availableRoom);
                            break;
                        }
                    }

                    if (roomFound) {
                        roomOnBoardDocument.get().setRooms(rooms);
                        roomsOrHouseRepository.save(roomOnBoardDocument.get());
                        break;
                    }
                }
            }
        }

        if (!roomFound) {
            String roomIdentifier = room.getRoomNumber() != null ? room.getRoomNumber() : room.getHouseNumber();
            throw new IllegalArgumentException(
                "Room/House number '" + roomIdentifier + "' is not associated with client '" + dto.getClientUserName() +
                "'. Available rooms: " + (availableRooms.length() > 0 ? availableRooms.toString().trim() : "None")
            );
        }

        // Proceed with registration
        RegistrationDocument doc = toDocumentWithId(id, dto, room);
        doc = registrationRepository.save(doc);

        // send notification after successful registration
        //sendRegistrationNotifications(dto, room);

        return toDto(doc);
    }

    public Optional<RegistrationWithRoomRequest> update(String id, Registration dto, Room room,Boolean changingRoom ) {
        return registrationRepository.findById(id)
                .map(existing -> {
                    RegistrationDocument doc = toDocument(dto, room);
                    doc.setId(id);
                    if (existing.getRoom() != null && !changingRoom) doc.setRoom(existing.getRoom());
                    else {doc.setRoom(room);doc.setId("U"+ "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 14));};
                    if (existing.getOccupied() != null) doc.setOccupied(existing.getOccupied());
                    doc = registrationRepository.save(doc);
                    registrationRepository.deleteById(id);
                    return toDto(doc);
                });
    }

    public List<RegistrationWithRoomRequest> findByClientUserNameAndClientName(String clientUserName, String clientName) {
        return registrationRepository.findByClientNameAndClientUserName(clientName, clientUserName).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public Optional<RegistrationWithRoomRequest> findById(String id) {
        return registrationRepository.findById(id).map(this::toDto);
    }

    public Optional<RegistrationWithRoomRequest> findByEmail(String email) {
        return registrationRepository.findByEmail(email).map(this::toDto);
    }

    public Optional<RegistrationWithRoomRequest> findByContactNo(String contactNo) {
        return registrationRepository.findByContactNo(contactNo).map(this::toDto);
    }

    public List<RegistrationWithRoomRequest> findAll() {
        return registrationRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findAllByRoomNumber(String clientUserName,String clientName,String roomNumber) {
         return registrationRepository.findByClientUserNameAndClientNameAndRoomRoomNumber(clientUserName,clientName,roomNumber).stream()
                .filter(doc -> doc.getCheckOutDate() == null || doc.getCheckOutDate().toString().isBlank())
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findAllByHouseNumber(String clientUserName,String clientName,String roomNumber) {
        return registrationRepository.findByClientUserNameAndClientNameAndRoomHouseNumber(clientUserName,clientName,roomNumber).stream()
                .filter(doc -> doc.getCheckOutDate() == null || doc.getCheckOutDate().toString().isBlank())
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findAllByRoomType(String clientUserName,String clientName,String roomType) {
        return registrationRepository.findAllByClientUserNameAndClientNameAndRoomRoomType(clientUserName,clientName,roomType).stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findAllByHouseType(String clientUserName,String clientName,String houseType) {
        return registrationRepository.findAllByClientUserNameAndClientNameAndRoomHouseType(clientUserName,clientName,houseType).stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findAllByGender(Registration.Gender gender) {
        return registrationRepository.findAllByGender(gender).stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findByFname(String fname) {
        return registrationRepository.findByfname(fname).stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findByLname(String lname) {
        return registrationRepository.findBylname(lname).stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findByMname(String mname) {
        return registrationRepository.findBymname(mname).stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findByAddress(String address) {
        return registrationRepository.findByaddress(address).stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findByPincode(String pincode) {
        return registrationRepository.findBypincode(pincode).stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findByCheckInDate(String checkInDate) {
        return registrationRepository.findBycheckInDate(checkInDate).stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findByCheckOutDate(String checkOutDate) {
        return registrationRepository.findBycheckOutDate(checkOutDate).stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Updates the checkout date for the first registration matching the given first name and room number.
     *
     * @param checkOutDate new checkout date to set
     * @return the updated registration if found, empty otherwise
     */
    public Optional<RegistrationWithRoomRequest> updateCheckOutDateByID(String id, Date checkOutDate,Registration.roomOccupied occupied) {
        return registrationRepository.findById(id)
                .map(doc -> {
                    doc.setCheckOutDate(checkOutDate);
                    doc.setOccupied(occupied);
                    if(occupied == Registration.roomOccupied.NOT_OCCUPIED) {
                        // If the room is being vacated, also update the room's occupied status
                        Optional<User> clientUser = userRepository.findByclientDetailsClientName(doc.getClientName());
                        if (clientUser.isPresent()) {
                            User user = clientUser.get();
                            Set<ClientAndRoomOnBoardId> clientAndRoomOnBoardIds = user.getClientDetails();
                            for (ClientAndRoomOnBoardId clientDetail : clientAndRoomOnBoardIds) {
                                if(clientDetail.getClientName().equals(doc.getClientName())) {
                                    Optional<RoomOnBoardDocument> roomOnBoardDocument = roomsOrHouseRepository.findById(clientDetail.getRoomOnBoardId());
                                    if (roomOnBoardDocument.isPresent()) {
                                        Set<Room> rooms = roomOnBoardDocument.get().getRooms();
                                        for (Room room : rooms) {
                                            if ((room.getRoomNumber() != null && room.getRoomNumber().equals(doc.getRoom().getRoomNumber())) ||
                                                (room.getHouseNumber() != null && room.getHouseNumber().equals(doc.getRoom().getHouseNumber()))) {
                                                room.setOccupied(Room.roomOccupied.NOT_OCCUPIED);
                                            }
                                        }
                                        roomOnBoardDocument.get().setRooms(rooms);
                                        roomsOrHouseRepository.save(roomOnBoardDocument.get());
                                    }
                                }
                            }
                        }
                    }
                    RegistrationDocument saved = registrationRepository.save(doc);
                    return toDto(saved);
                });
    }

    /**
     * Returns all registrations (with room info) where occupied status is NOT_OCCUPIED.
     */
    public List<RegistrationWithRoomRequest> findRoomsWithNotOccupiedStatus() {
        return registrationRepository.findByOccupied(Registration.roomOccupied.NOT_OCCUPIED)
                .stream()
                .map(doc -> toDto(doc))
                .collect(Collectors.toList());
    }

    /**
     * Returns all registrations (with room info) where occupied status is OCCUPIED.
     */
    public List<RegistrationWithRoomRequest> findRoomsWithOccupiedStatus() {
        return registrationRepository.findByOccupied(Registration.roomOccupied.OCCUPIED)
                .stream()
                .map(doc -> toDto(doc))
                .collect(Collectors.toList());
    }

    public boolean existsByEmail(String email) {
        return registrationRepository.findByEmail(email).isPresent();
    }

    public boolean existsByContactNo(String contactNo) {
        return registrationRepository.findByContactNo(contactNo).isPresent();
    }

    public void deleteById(String id) {
        registrationRepository.deleteById(id);
    }


    public boolean existsById(String id) {
        return registrationRepository.existsById(id);
    }

    private RegistrationDocument toDocumentWithId(String id,Registration dto, Room room) {
        RegistrationDocument registrationDocument =  toDocument(dto, room);
        registrationDocument.setId(id);
        registrationDocument.setOccupied(Registration.roomOccupied.OCCUPIED);
        return registrationDocument;
    }

    private RegistrationDocument toDocument(Registration dto, Room room) {
        return RegistrationDocument.builder()
                .fname(dto.getFname())
                .lname(dto.getLname())
                .email(dto.getEmail())
                .mname(dto.getMname())
                .contactNo(dto.getContactNo())
                .gender(dto.getGender())
                .address(dto.getAddress())
                .pincode(dto.getPincode())
                .uploadDocuments(dto.getUploadDocuments())
                .photo(dto.getPhoto())
                .documentType(dto.getDocumentType())
                .documentNumber(dto.getDocumentNumber())
                .checkInDate(dto.getCheckInDate())
                .checkOutDate(dto.getCheckOutDate())
                .occupied(dto.getRoomOccupied())
                .clientUserName(dto.getClientUserName())
                .clientName(dto.getClientName())
                .roomRent(dto.getRoomRent())
                .room(room)
                .build();
    }

    private Registration toRegistration(RegistrationDocument doc) {
        Registration dto = new Registration();
        dto.setFname(doc.getFname());
        dto.setLname(doc.getLname());
        dto.setEmail(doc.getEmail());
        dto.setMname(doc.getMname());
        dto.setContactNo(doc.getContactNo());
        dto.setGender(doc.getGender());
        dto.setAddress(doc.getAddress());
        dto.setPincode(doc.getPincode());
        dto.setUploadDocuments(doc.getUploadDocuments());
        dto.setPhoto(doc.getPhoto());
        dto.setDocumentType(doc.getDocumentType());
        dto.setDocumentNumber(doc.getDocumentNumber());
        dto.setCheckInDate(doc.getCheckInDate());
        dto.setCheckOutDate(doc.getCheckOutDate());
        dto.setAdvanceAmount(doc.getAdvanceAmount());
        dto.setRoomOccupied(doc.getOccupied());
        dto.setClientUserName(doc.getClientUserName());
        dto.setClientName(doc.getClientName());
        dto.setRoomRent(doc.getRoomRent());
        dto.setRegId(doc.getId());
        return dto;
    }

    private RegistrationWithRoomRequest toDto(RegistrationDocument doc) {
        RegistrationWithRoomRequest dtoWithRoom = new RegistrationWithRoomRequest();
        dtoWithRoom.setRegistration(toRegistration(doc));
        dtoWithRoom.setId(doc.getId());
        if (doc.getRoom() != null) {
            Room room = new Room();
            room.setRoomNumber(doc.getRoom().getRoomNumber());
            room.setHouseNumber(doc.getRoom().getHouseNumber());
            room.setRoomType(doc.getRoom().getRoomType());
            room.setHouseType(doc.getRoom().getHouseType());
            room.setOccupied(doc.getRoom().getOccupied());
            dtoWithRoom.setRoom(room);
        }
        return dtoWithRoom;
    }

    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    private void sendRegistrationNotifications(Registration dto, Room room) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("fname", dto.getFname());
        variables.put("roomNumber", room.getRoomNumber());
        variables.put("houseNumber", room.getHouseNumber());
        variables.put("checkInDate", dto.getCheckInDate());
        if (dto.getEmail() != null) {
            notificationClient.sendEmailWithTemplate(dto.getEmail(), "Registration Successful", "registration-success", variables);
        }
        if (dto.getContactNo() != null) {
            notificationClient.sendSms(dto.getContactNo(), "Hello " + dto.getFname() + ", your registration has been completed.");
        }
    }

    @Recover
    private void recoverSendRegistrationNotifications(Exception e, Registration dto, Room room) {
        // Log the failure and potentially store for later retry or manual processing
        System.err.println("Failed to send registration notifications for " + dto.getFname() + " after 3 attempts: " + e.getMessage());
        // Could implement additional recovery logic here, like saving to a retry queue
    }
}
