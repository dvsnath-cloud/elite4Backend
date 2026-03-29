package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.dto.ClientAndRoomOnBoardId;
import com.elite4.anandan.registrationservices.model.EmployeeRole;
import com.elite4.anandan.registrationservices.model.Role;
import com.elite4.anandan.registrationservices.model.User;
import com.elite4.anandan.registrationservices.repository.RoleRepository;
import com.elite4.anandan.registrationservices.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Bootstrap service to create initial admin user if none exists.
 */
@Component
public class AdminBootstrap implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;

    public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        try {
            // Check if any admin user exists
            boolean adminExists = userRepository.findAll()
                    .stream()
                    .anyMatch(user -> user.getEmail() != null &&
                             user.getEmail().equals("darsiviswanath@gmail.com") &&
                             user.isActive());

            if (!adminExists) {
                // Create default admin user
                User admin = new User();
                admin.setUsername("admin");
                admin.setPasswordHash(passwordEncoder.encode("admin@123"));
                admin.setEmail("darsiviswanath@gmail.com");
                admin.setPhoneE164("+919611040912");
                admin.setPhoneRaw("9611040912");
                Set<ClientAndRoomOnBoardId> ClientAndRoomOnBoardIds = new HashSet<>();
                ClientAndRoomOnBoardId clientAndRoomOnBoardId = new ClientAndRoomOnBoardId();
                clientAndRoomOnBoardId.setColiveName("my own");
                clientAndRoomOnBoardId.setRoomOnBoardId("12345678");
                ClientAndRoomOnBoardIds.add(clientAndRoomOnBoardId);
                admin.setClientDetails(ClientAndRoomOnBoardIds);
                // Create and save roles first
                Set<String> roleIds = new HashSet<>();
                // Create ADMIN role
                Role adminRole = new Role();
                adminRole.setName(EmployeeRole.ROLE_ADMIN);
                Role savedAdminRole = roleRepository.save(adminRole);
                roleIds.add(savedAdminRole.getId());

                // Create MODERATOR role
                Role moderatorRole = new Role();
                moderatorRole.setName(EmployeeRole.ROLE_MODERATOR);
                Role savedModeratorRole = roleRepository.save(moderatorRole);
                roleIds.add(savedModeratorRole.getId());

                // Create USER role
                Role userRole = new Role();
                userRole.setName(EmployeeRole.ROLE_USER);
                Role savedUserRole = roleRepository.save(userRole);
                roleIds.add(savedUserRole.getId());

                // Create GUEST role
                Role guestRole = new Role();
                guestRole.setName(EmployeeRole.ROLE_GUEST);
                Role savedGuestRole = roleRepository.save(guestRole);
                roleIds.add(savedGuestRole.getId());
                admin.setRoleIds(roleIds);
                admin.setActive(true); // Admin is active by default

                userRepository.save(admin);
                System.out.println("Default admin user created:");
                System.out.println("Username: admin");
                System.out.println("Password: admin@123");
                System.out.println("Email: darsiviswanath@gmail.com");
                System.out.println("Roles: ADMIN, MODERATOR, USER, GUEST");
            } else {
                System.out.println("Admin user already exists, skipping bootstrap.");
            }
        } catch (Exception e) {
            System.err.println("Error during admin bootstrap: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
