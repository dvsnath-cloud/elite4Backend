package com.elite4.anandan.registrationservices.repository;


import com.elite4.anandan.registrationservices.document.RoomOnBoardDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

/**
 * Repository for Room document access.
 */
public interface RoomsOrHouseRepository extends MongoRepository<RoomOnBoardDocument, String> {

    Optional<RoomOnBoardDocument> findById(String id);
}
