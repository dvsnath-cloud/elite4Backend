package com.elite4.anandan.registrationservices.repository;

import com.elite4.anandan.registrationservices.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository for User document access.
 */
public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByPhoneE164(String phoneE164);

    Optional<User> findByUsername(String username);

    Optional<User>findByclientDetailsClientName(String clientName);

    List<User> findAllByclientDetailsClientName(String clientName);

    Optional<User> findByEmail(String email);

    List<User> findByUsernameContainingIgnoreCaseAndActiveTrue(String searchTerm);

    List<User> findByEmailContainingIgnoreCaseAndActiveTrue(String searchTerm);

    List<User> findByPhoneE164ContainingIgnoreCaseAndActiveTrue(String searchTerm);

}
