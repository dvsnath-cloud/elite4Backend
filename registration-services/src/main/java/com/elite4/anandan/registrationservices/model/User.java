package com.elite4.anandan.registrationservices.model;

import com.elite4.anandan.registrationservices.dto.ClientAndRoomOnBoardId;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * MongoDB document representing an application user with assigned roles.
 */
@Document(collection = "users")
public class User {

    /* ================================
       IDENTIFICATION
       ================================ */
    @Id
    private String id;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50)
    @Indexed(unique = true)
    private String username;

    @NotBlank(message = "ownerOfClient is required")
    @Size(min = 3, max = 50)
    @Indexed(unique = true)
    private String ownerOfClient;

    /* ================================
       AUTHENTICATION & SECURITY
       ================================ */
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String passwordHash;

    public Set<ClientAndRoomOnBoardId> getClientDetails() {
        return clientDetails;
    }

    public void setClientDetails(Set<ClientAndRoomOnBoardId> clientDetails) {
        this.clientDetails = clientDetails;
    }

    /* ================================
           CONTACT INFORMATION
           ================================ */
    @Email(message = "Invalid email format")
    @Indexed(unique = true, sparse = true)
    private String email;

    // Canonical phone number in E.164 (e.g., +919876543210)
    @Indexed(unique = true)
    private String phoneE164;

    // Raw phone number entered by the user
    private String phoneRaw;

    /* ================================
       ACCESS CONTROL
       ================================ */
    private Set<String> roleIds = new HashSet<>();

    /* ================================
       CLIENT & ONBOARDING DETAILS
       ================================ */
    private Set<ClientAndRoomOnBoardId> clientDetails;
    /* ================================
       STATUS & AUDIT
       ================================ */
    private boolean active = false;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;


    /* ================================
       CONSTRUCTORS
       ================================ */
    public User() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public User(String username, String passwordHash, String email) {
        this();
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
    }

    public User(String username, String passwordHash, String email, String phoneE164, String phoneRaw) {
        this();
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.phoneE164 = phoneE164;
        this.phoneRaw = phoneRaw;
    }


    /* ================================
       GETTERS & SETTERS
       ================================ */
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getOwnerOfClient() {
        return ownerOfClient;
    }

    public void setOwnerOfClient(String ownerOfClient) {
        this.ownerOfClient = ownerOfClient;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneE164() {
        return phoneE164;
    }

    public void setPhoneE164(String phoneE164) {
        this.phoneE164 = phoneE164;
    }

    public String getPhoneRaw() {
        return phoneRaw;
    }

    public void setPhoneRaw(String phoneRaw) {
        this.phoneRaw = phoneRaw;
    }

    public Set<String> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(Set<String> roleIds) {
        this.roleIds = roleIds != null ? roleIds : new HashSet<>();
    }

    public void addRoleId(String roleId) {
        this.roleIds.add(roleId);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}