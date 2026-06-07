package com.sanproject.aso_service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

// BCrypt hashing; matches returns false when the stored hash is null.
@Service
public class PasswordService {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String passwordHash) {
        return passwordHash != null && encoder.matches(rawPassword, passwordHash);
    }
}
