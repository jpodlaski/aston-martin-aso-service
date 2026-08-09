package com.sanproject.aso_service.config;

import com.sanproject.aso_service.domain.Admin;
import com.sanproject.aso_service.domain.EmployeeRole;
import com.sanproject.aso_service.repository.AdminRepository;
import com.sanproject.aso_service.security.PasswordService;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds demo admin/admin on first empty DB only — convenience for local docker-compose demos.
 * Would be gated behind a "dev" profile (or removed) before a real production deploy.
 */
@Component
public class DemoAuthDataLoader implements CommandLineRunner {

    private final AdminRepository adminRepository;
    private final PasswordService passwordService;

    public DemoAuthDataLoader(AdminRepository adminRepository, PasswordService passwordService) {
        this.adminRepository = adminRepository;
        this.passwordService = passwordService;
    }

    @Override
    public void run(String... args) {
        seedInitialAdmins();
    }

    // Seeds a single demo admin only when the database has no admin rows yet.
    private void seedInitialAdmins() {
        if (adminRepository.count() > 0) {
            return;
        }

        Admin admin = new Admin();
        admin.setFirstName("Admin");
        admin.setLastName("User");
        admin.setEmail("admin@aso.local");
        admin.setLogin("admin");
        admin.setPasswordHash(passwordService.hash("admin"));
        admin.setRole(EmployeeRole.ADMIN);
        adminRepository.save(admin);
    }
}
