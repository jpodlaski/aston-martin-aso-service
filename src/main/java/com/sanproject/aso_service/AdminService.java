package com.sanproject.aso_service;

import org.springframework.stereotype.Service;

import java.util.List;

// Admin account CRUD; new admins default to the ADMIN role when none is supplied.
@Service
public class AdminService {

    private final AdminRepository adminRepository;

    public AdminService(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    public List<Admin> getAllAdmins() {
        return adminRepository.findAll();
    }

    public Admin getAdminById(Long id) {
        return adminRepository.findById(id).orElse(null);
    }

    public Admin createAdmin(Admin admin) {
        if (admin.getRole() == null) {
            admin.setRole(EmployeeRole.ADMIN);
        }
        return adminRepository.save(admin);
    }
}
