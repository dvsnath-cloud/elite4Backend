package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.dto.ClientAndRoomOnBoardId;
import com.elite4.anandan.registrationservices.model.AdminGroup;
import com.elite4.anandan.registrationservices.model.EmployeeRole;
import com.elite4.anandan.registrationservices.model.Role;
import com.elite4.anandan.registrationservices.model.User;
import com.elite4.anandan.registrationservices.repository.AdminGroupRepository;
import com.elite4.anandan.registrationservices.repository.RoleRepository;
import com.elite4.anandan.registrationservices.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bootstrap service to create initial admin user and super-admin group if none exist.
 */
@Component
public class AdminBootstrap implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final AdminGroupRepository adminGroupRepository;

    public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          RoleRepository roleRepository, AdminGroupRepository adminGroupRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
        this.adminGroupRepository = adminGroupRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        try {
            // ── 0. Deduplicate roles (heals duplicate docs from prior bootstrap runs) ──
            for (EmployeeRole er : EmployeeRole.values()) {
                List<Role> roles = roleRepository.findAllByName(er);
                if (roles.size() > 1) {
                    Role keep = roles.get(0);
                    Set<String> extraIds = roles.stream().skip(1)
                            .map(Role::getId).collect(Collectors.toSet());
                    userRepository.findAll().forEach(user -> {
                        if (user.getRoleIds() != null && user.getRoleIds().stream().anyMatch(extraIds::contains)) {
                            Set<String> fixed = new LinkedHashSet<>(user.getRoleIds());
                            extraIds.forEach(fixed::remove);
                            fixed.add(keep.getId());
                            user.setRoleIds(fixed);
                            userRepository.save(user);
                        }
                    });
                    extraIds.forEach(roleRepository::deleteById);
                    System.out.println("✅ Deduplicated role: " + er.name() + " (removed " + extraIds.size() + " duplicates)");
                }
            }

            // ── 1. Ensure super-admin user exists ──────────────────────────────
            boolean adminExists = userRepository.findAll()
                    .stream()
                    .anyMatch(user -> user.getEmail() != null &&
                             user.getEmail().equals("darsiviswanath@gmail.com") &&
                             user.isActive());

            if (!adminExists) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPasswordHash(passwordEncoder.encode("admin@123"));
                admin.setEmail("darsiviswanath@gmail.com");
                admin.setPhoneE164("+919611040912");
                admin.setPhoneRaw("9611040912");
                Set<ClientAndRoomOnBoardId> details = new HashSet<>();
                ClientAndRoomOnBoardId cid = new ClientAndRoomOnBoardId();
                cid.setColiveName("my own");
                cid.setRoomOnBoardId("12345678");
                details.add(cid);
                admin.setClientDetails(details);

                Set<String> roleIds = new HashSet<>();
                for (EmployeeRole er : EmployeeRole.values()) {
                    Role r = new Role();
                    r.setName(er);
                    roleIds.add(roleRepository.save(r).getId());
                }
                admin.setRoleIds(roleIds);
                admin.setActive(true);
                userRepository.save(admin);
                System.out.println("✅ Default admin user created — username: admin / password: admin@123");
            } else {
                System.out.println("✅ Admin user already exists, skipping.");
            }

            // ── 2. Ensure super-admin group exists ─────────────────────────────
            if (adminGroupRepository.findByGroupName("super-admin").isEmpty()) {
                AdminGroup superAdminGroup = new AdminGroup();
                superAdminGroup.setGroupName("super-admin");
                superAdminGroup.setDescription("Super administrators — full application access for all members.");
                superAdminGroup.setSuperAdmin(true);
                Set<String> members = new LinkedHashSet<>();
                members.add("admin");
                superAdminGroup.setMemberUsernames(members);
                superAdminGroup.setCreatedBy("system");
                adminGroupRepository.save(superAdminGroup);
                System.out.println("✅ Super-admin group created with member: admin");
            } else {
                System.out.println("✅ Super-admin group already exists, skipping.");
            }

        } catch (Exception e) {
            System.err.println("❌ Error during admin bootstrap: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
