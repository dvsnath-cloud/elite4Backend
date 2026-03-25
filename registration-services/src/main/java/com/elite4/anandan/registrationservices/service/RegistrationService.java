package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.document.RegistrationDocument;
import com.elite4.anandan.registrationservices.document.RoomOnBoardDocument;
import com.elite4.anandan.registrationservices.dto.*;
import com.elite4.anandan.registrationservices.model.User;
import com.elite4.anandan.registrationservices.repository.RegistrationRepository;
import com.elite4.anandan.registrationservices.repository.RoomsOrHouseRepository;
import com.elite4.anandan.registrationservices.repository.UserRepository;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RegistrationService {
    private final RegistrationRepository registrationRepository;
    private final NotificationClient notificationClient;
    private final UserRepository userRepository;
    private final RoomsOrHouseRepository roomsOrHouseRepository;

    public RegistrationWithRoomRequest create(Registration dto, Room room) {
        // Validate that clientUserName is provided (required field)
        if (dto.getClientUserName() == null || dto.getClientUserName().trim().isEmpty()) {
            throw new IllegalArgumentException("Client username must be provided for registration");
        }

        // Validate that contactNo is provided (required field)
        if (dto.getContactNo() == null || dto.getContactNo().trim().isEmpty()) {
            throw new IllegalArgumentException("Contact number must be provided for registration");
        }

        // Check if user with same contact number is already onboarded
        Optional<RegistrationDocument> existingRegistrationByContact = registrationRepository.findByContactNo(dto.getContactNo());
        if (existingRegistrationByContact.isPresent()) {
            throw new IllegalArgumentException(
                    "A user is already onboarded with contact number '" + dto.getContactNo() +
                    "'. User name: '" + existingRegistrationByContact.get().getFname() +
                    "', Client: '" + existingRegistrationByContact.get().getClientName() + "'"
            );
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
        String role = "";
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            Authentication authenticationnew = SecurityContextHolder.getContext().getAuthentication();
            //get role from authenticationnew
            role = authenticationnew.getAuthorities().stream()
                    .map(grantedAuthority -> grantedAuthority.getAuthority())
                    .filter(auth -> auth.startsWith("ROLE_"))
                    .findFirst()
                    .orElse("ROLE_USER");
        }
        String id = null;
        if (!role.equals("ROLE_USER")) {
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
            for (ClientAndRoomOnBoardId clientDetail : clientAndRoomOnBoardIds) {
                if (clientDetail.getClientName().equals(dto.getClientName())) {
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
                                    availableRoom.getRoomNumber() != null &&
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
                                    availableRoom.getHouseNumber() != null &&
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
        }

        // Proceed with registration
        RegistrationDocument doc = toDocumentWithId(id, dto, room);
        doc = registrationRepository.save(doc);

        // send notification after successful registration
        //sendRegistrationNotifications(dto, room);

        return toDto(doc);
    }

    public Optional<RegistrationWithRoomRequest> update(String id, Registration dto, Room room, Boolean changingRoom) {
        return registrationRepository.findById(id)
                .map(existing -> {
                    RegistrationDocument doc = toDocument(dto, room);
                    doc.setId(id);
                    if (existing.getRoom() != null && !changingRoom) doc.setRoom(existing.getRoom());
                    else {
                        doc.setRoom(room);
                        doc.setId("U" + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 14));
                    }
                    ;
                    if (existing.getOccupied() != null) doc.setOccupied(existing.getOccupied());
                    doc = registrationRepository.save(doc);
                    if(changingRoom){
                    registrationRepository.deleteById(id);
                    }
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
        Optional<RegistrationDocument> reg = registrationRepository.findByContactNo(String.valueOf(contactNo));
        if(reg.isPresent()) {
            return reg.map(this::toDto);
        }else{
            PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
            Phonenumber.PhoneNumber number = null;
            try {
                number = phoneUtil.parse(contactNo, "IN");
            } catch (NumberParseException e) {
                throw new RuntimeException(e);
            }
            long nationalNumber = number.getNationalNumber();
            Optional<RegistrationDocument> regWithNumber = registrationRepository.findByContactNo(String.valueOf(contactNo));
            if(regWithNumber.isPresent()) {
                return regWithNumber.map(this::toDto);
            } else {
                String newNumber= "0"+String.valueOf(nationalNumber);
                return registrationRepository.findByContactNo(newNumber).map(this::toDto);
            }
        }
    }

    public List<RegistrationWithRoomRequest> findAll() {
        return registrationRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findAllByRoomNumber(String clientUserName, String clientName, String roomNumber) {
        return registrationRepository.findByClientNameAndClientUserNameAndRoomRoomNumberAndOccupied(
                clientName,
                clientUserName,
                roomNumber,
                Registration.roomOccupied.OCCUPIED).stream()
                .filter(doc -> doc.getCheckOutDate() == null || doc.getCheckOutDate().toString().isBlank())
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findAllByHouseNumber(String clientUserName, String clientName, String roomNumber) {
        return registrationRepository.findByClientUserNameAndClientNameAndRoomHouseNumberAndOccupied(clientUserName, clientName, roomNumber, Registration.roomOccupied.OCCUPIED).stream()
                .filter(doc -> doc.getCheckOutDate() == null || doc.getCheckOutDate().toString().isBlank())
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findAllByRoomType(String clientUserName, String clientName, String roomType) {
        return registrationRepository.findAllByClientUserNameAndClientNameAndRoomRoomType(clientUserName, clientName, roomType).stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findAllByHouseType(String clientUserName, String clientName, String houseType) {
        return registrationRepository.findAllByClientUserNameAndClientNameAndRoomHouseType(clientUserName, clientName, houseType).stream().map(this::toDto).collect(Collectors.toList());
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

    public ResponseEntity checkoutAll(UserForCheckOutForAll userForCheckOutForAll){
        AtomicReference<String> clientName = new AtomicReference<>();
         AtomicReference<String> clientUserName = new AtomicReference<>();
         AtomicReference<Room> room = new AtomicReference<>(new Room());
         List<String> vacatedRegistrationIds = new ArrayList<>();
        for(UpdateUserForCheckOut updateUserForCheckOut : userForCheckOutForAll.getUpdateUserForCheckOutSet()) {
            registrationRepository.findById(updateUserForCheckOut.getRegistrationId())
                    .map(doc -> {
                        doc.setCheckOutDate(updateUserForCheckOut.getCheckOutDate());
                        doc.setOccupied(Registration.roomOccupied.VACATED);
                        clientName.set(doc.getClientName());
                        clientUserName.set(doc.getClientUserName());
                        room.set(doc.getRoom());
                        // If the room is being vacated, also update the room's occupied status
                        RegistrationDocument saved = registrationRepository.save(doc);
                        vacatedRegistrationIds.add(saved.getId());
                        return toDto(saved);
                    }).orElse(null);
        }
        Optional<User> clientUser = userRepository.findByclientDetailsClientName(clientName.get());
        if (clientUser.isPresent()) {
            User user = clientUser.get();
            List<RegistrationDocument> registrationDocuments = registrationRepository.findByClientNameAndClientUserNameAndRoomRoomNumberAndOccupied(clientUserName.get(),clientName.get(), room.get().getRoomNumber(),Registration.roomOccupied.OCCUPIED);
            if(registrationDocuments.stream().count()>=1){
                room.get().setOccupied(Room.roomOccupied.PARTIALLY_OCCUPIED);
            }else{
                room.get().setOccupied(Room.roomOccupied.NOT_OCCUPIED);
            }
        }
        return ResponseEntity.ok("Registrations with IDs " + vacatedRegistrationIds + " have been checked out and rooms updated accordingly.");
    }

    public Optional<RegistrationWithRoomRequest> checkout(UpdateUserForCheckOut updateUserForCheckOut){
        String id = updateUserForCheckOut.getRegistrationId();
        Date checkOutDate = updateUserForCheckOut.getCheckOutDate();
        return registrationRepository.findById(id)
                .map(doc -> {
                    doc.setCheckOutDate(checkOutDate);
                    doc.setOccupied(Registration.roomOccupied.VACATED);
                    RegistrationDocument saved = registrationRepository.save(doc);
                    // If the room is being vacated, also update the room's occupied status
                    Optional<User> clientUser = userRepository.findByclientDetailsClientName(doc.getClientName());
                        if (clientUser.isPresent()) {
                            User user = clientUser.get();
                            Set<ClientAndRoomOnBoardId> clientAndRoomOnBoardIds = user.getClientDetails();
                            for (ClientAndRoomOnBoardId clientDetail : clientAndRoomOnBoardIds) {
                                if (clientDetail.getClientName().equals(doc.getClientName())) {
                                    Optional<RoomOnBoardDocument> roomOnBoardDocument = roomsOrHouseRepository.findById(clientDetail.getRoomOnBoardId());
                                    if (roomOnBoardDocument.isPresent()) {
                                        Set<Room> rooms = roomOnBoardDocument.get().getRooms();
                                        for (Room room : rooms) {
                                            if ((room.getRoomNumber() != null && room.getRoomNumber().equals(doc.getRoom().getRoomNumber())) ||
                                                    (room.getHouseNumber() != null && room.getHouseNumber().equals(doc.getRoom().getHouseNumber()))) {
                                                List<RegistrationDocument> registrationDocuments = registrationRepository.findByClientNameAndClientUserNameAndRoomRoomNumberAndOccupied(doc.getClientUserName(),doc.getClientUserName(),room.getRoomNumber(),Registration.roomOccupied.OCCUPIED);
                                                if(registrationDocuments.stream().count()>=1){
                                                    room.setOccupied(Room.roomOccupied.PARTIALLY_OCCUPIED);
                                                }else{
                                                    room.setOccupied(Room.roomOccupied.NOT_OCCUPIED);
                                                }
                                            }
                                        }
                                        roomOnBoardDocument.get().setRooms(rooms);
                                        roomsOrHouseRepository.save(roomOnBoardDocument.get());
                                    }
                                }
                            }
                        }
                    return toDto(saved);
                });
    }

    /**
     * Updates the checkout date for the first registration matching the given first name and room number.
     *
     * @param checkOutDate new checkout date to set
     * @return the updated registration if found, empty otherwise
     */
    public Optional<RegistrationWithRoomRequest> updateCheckOutDateByID(String id, Date checkOutDate) {
        return checkout (new UpdateUserForCheckOut(id, checkOutDate));
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
     * Returns all registrations (with room info) where occupied status is VACATED.
     */
    public List<RegistrationWithRoomRequest> findRoomsWithPartiallyOccupiedStatus() {
        return registrationRepository.findByOccupied(Registration.roomOccupied.VACATED).stream()
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

    private RegistrationDocument toDocumentWithId(String id, Registration dto, Room room) {
        RegistrationDocument registrationDocument = toDocument(dto, room);
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
