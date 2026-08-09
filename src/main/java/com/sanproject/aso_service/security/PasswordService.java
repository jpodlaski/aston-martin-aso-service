package com.sanproject.aso_service.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * BCrypt password hashing. BCrypt is designed to be slow on purpose (work factor),
 * which makes brute-force attacks expensive. We store only the hash, never the raw password.
 */
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
