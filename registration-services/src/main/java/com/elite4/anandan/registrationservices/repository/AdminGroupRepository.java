package com.elite4.anandan.registrationservices.repository;

import com.elite4.anandan.registrationservices.model.AdminGroup;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AdminGroupRepository extends MongoRepository<AdminGroup, String> {

    Optional<AdminGroup> findByGroupName(String groupName);

    /** Returns true if the given username is a member of any admin group. */
    boolean existsByMemberUsernamesContaining(String username);

    /** Returns all groups the given username belongs to. */
    List<AdminGroup> findByMemberUsernamesContaining(String username);
}
