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

@Document(collection = "users")
public class User {
    @Id
    private String id;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50)
    @Indexed(unique = true)
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String passwordHash;

    @Email(message = "Invalid email format")
    @Indexed(unique = true, sparse = true)
    private String email;

    @Indexed(unique = true, sparse = true)
    private String phoneE164;

    private String phoneRaw;

    private Set<String> roleIds = new HashSet<>();
    private Set<ClientAndRoomOnBoardId> clientDetails;
    private String aadharPhotoPath;
    private boolean active = false;
    private boolean forcePasswordChange = false;
    private String passwordResetOtp;
    private Instant passwordResetOtpExpiry;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;
    private String ownerOfClient;
    private Set<String> rooms = new HashSet<>();

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

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhoneE164() { return phoneE164; }
    public void setPhoneE164(String phoneE164) { this.phoneE164 = phoneE164; }
    public String getPhoneRaw() { return phoneRaw; }
    public void setPhoneRaw(String phoneRaw) { this.phoneRaw = phoneRaw; }
    public Set<String> getRoleIds() { return roleIds; }
    public void setRoleIds(Set<String> roleIds) { this.roleIds = roleIds != null ? roleIds : new HashSet<>(); }
    public void addRoleId(String roleId) { this.roleIds.add(roleId); }
    public Set<ClientAndRoomOnBoardId> getClientDetails() { return clientDetails; }
    public void setClientDetails(Set<ClientAndRoomOnBoardId> clientDetails) { this.clientDetails = clientDetails; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isForcePasswordChange() { return forcePasswordChange; }
    public void setForcePasswordChange(boolean forcePasswordChange) { this.forcePasswordChange = forcePasswordChange; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public String getAadharPhotoPath() { return aadharPhotoPath; }
    public void setAadharPhotoPath(String aadharPhotoPath) { this.aadharPhotoPath = aadharPhotoPath; }
    public String getOwnerOfClient() { return ownerOfClient; }
    public void setOwnerOfClient(String ownerOfClient) { this.ownerOfClient = ownerOfClient; }
    public Set<String> getRooms() { return rooms; }
    public void setRooms(Set<String> rooms) { this.rooms = rooms != null ? rooms : new HashSet<>(); }
    public String getPasswordResetOtp() { return passwordResetOtp; }
    public void setPasswordResetOtp(String passwordResetOtp) { this.passwordResetOtp = passwordResetOtp; }
    public Instant getPasswordResetOtpExpiry() { return passwordResetOtpExpiry; }
    public void setPasswordResetOtpExpiry(Instant passwordResetOtpExpiry) { this.passwordResetOtpExpiry = passwordResetOtpExpiry; }
}
