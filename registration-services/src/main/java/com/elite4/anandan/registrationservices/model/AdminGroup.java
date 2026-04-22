package com.elite4.anandan.registrationservices.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Represents an admin group whose members are granted ROLE_ADMIN across the entire application.
 * The "super-admin" group (superAdmin=true) is bootstrapped by the system and cannot be deleted.
 */
@Document(collection = "admin_groups")
public class AdminGroup {

    @Id
    private String id;

    @Indexed(unique = true)
    private String groupName;

    private String description;

    /** Marks the system-bootstrapped super-admin group. */
    private boolean superAdmin = false;

    /** Usernames of all members who belong to this admin group. */
    private Set<String> memberUsernames = new LinkedHashSet<>();

    private String createdBy;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isSuperAdmin() { return superAdmin; }
    public void setSuperAdmin(boolean superAdmin) { this.superAdmin = superAdmin; }

    public Set<String> getMemberUsernames() { return memberUsernames; }
    public void setMemberUsernames(Set<String> memberUsernames) {
        this.memberUsernames = memberUsernames != null ? memberUsernames : new LinkedHashSet<>();
    }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
