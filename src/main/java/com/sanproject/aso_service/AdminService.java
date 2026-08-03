package com.sanproject.aso_service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// Admin account CRUD; new admins default to the ADMIN role when none is supplied.
@Service
public class AdminService {

    private final AdminRepository adminRepository;
    private final WorkerRepository workerRepository;
    private final PasswordService passwordService;

    public AdminService(
            AdminRepository adminRepository,
            WorkerRepository workerRepository,
            PasswordService passwordService) {
        this.adminRepository = adminRepository;
        this.workerRepository = workerRepository;
        this.passwordService = passwordService;
    }

    public List<Admin> getAllAdmins() {
        return adminRepository.findAll();
    }

    public Admin getAdminById(Long id) {
        return adminRepository.findById(id).orElse(null);
    }

    public Admin createAdmin(CreateAdminRequest request) {
        EmployeeRole role = request.getRole() != null ? request.getRole() : EmployeeRole.ADMIN;
        if (!role.canManageWorkers()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role for admin account");
        }

        String login = request.getLogin().trim();
        if (adminRepository.findByLoginIgnoreCase(login).isPresent()
                || workerRepository.findByLoginIgnoreCase(login).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Login already in use");
        }

        Admin admin = new Admin();
        admin.setFirstName(request.getFirstName().trim());
        admin.setLastName(request.getLastName().trim());
        admin.setEmail(request.getEmail().trim());
        admin.setLogin(login);
        admin.setPasswordHash(passwordService.hash(request.getPassword()));
        admin.setRole(role);

        return adminRepository.save(admin);
    }
}
