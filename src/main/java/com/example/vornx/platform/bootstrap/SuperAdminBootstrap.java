package com.example.vornx.platform.bootstrap;

import com.example.vornx.platform.entity.AdminUser;
import com.example.vornx.platform.enums.AdminRole;
import com.example.vornx.platform.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Solves the "chicken and egg" problem: admins create partners and other admins,
 * but someone has to be the first admin. On startup, if there are NO admins yet,
 * this creates one SUPER_ADMIN from the vornx.bootstrap.* properties.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAdminBootstrap implements ApplicationRunner {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapProperties props;

    @Override
    public void run(ApplicationArguments args) {
        if (adminUserRepository.count() > 0) {
            return;
        }
        if (!StringUtils.hasText(props.adminEmail()) || !StringUtils.hasText(props.adminPassword())) {
            log.warn("No admin exists yet. Set vornx.bootstrap.admin-email and admin-password to create the first one.");
            return;
        }
        AdminUser admin = new AdminUser(
                props.adminName(),
                props.adminEmail().trim().toLowerCase(),
                passwordEncoder.encode(props.adminPassword()),
                AdminRole.SUPER_ADMIN
        );
        adminUserRepository.save(admin);
        log.info("Created the first SUPER_ADMIN: {}", admin.getEmail());
    }
}
