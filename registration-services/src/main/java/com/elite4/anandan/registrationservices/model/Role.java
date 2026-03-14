package com.elite4.anandan.registrationservices.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB document representing an application role with permissions.
 */
@Document(collection = "roles")
public class Role {

    @Id
    private String id;

    @Indexed(unique = true)
    private EmployeeRole name;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public EmployeeRole getName() {
        return name;
    }

    public void setName(EmployeeRole name) {
        this.name = name;
    }
}
