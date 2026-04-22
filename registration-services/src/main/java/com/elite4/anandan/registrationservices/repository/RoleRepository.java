package com.elite4.anandan.registrationservices.repository;

import com.elite4.anandan.registrationservices.model.EmployeeRole;
import com.elite4.anandan.registrationservices.model.Role;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends MongoRepository<Role, String> {
    Optional<Role> findById(String id);
    Optional<Role> findByName(EmployeeRole name);
    List<Role> findAllByName(EmployeeRole name);
}
