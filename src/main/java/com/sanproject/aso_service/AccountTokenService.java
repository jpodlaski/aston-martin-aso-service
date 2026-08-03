package com.sanproject.aso_service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Issues and consumes one-time client account tokens (reset / verify).
 * Raw tokens are random UUIDs; only SHA-256 hashes are stored.
 */
@Service
public class AccountTokenService {

    private final CustomerAccountTokenRepository tokenRepository;
    private final long passwordResetTtlSeconds;
    private final long emailVerificationTtlSeconds;

    public AccountTokenService(
            CustomerAccountTokenRepository tokenRepository,
            @Value("${app.account-token.password-reset-ttl-seconds:3600}") long passwordResetTtlSeconds,
            @Value("${app.account-token.email-verification-ttl-seconds:86400}") long emailVerificationTtlSeconds) {
        this.tokenRepository = tokenRepository;
        this.passwordResetTtlSeconds = passwordResetTtlSeconds;
        this.emailVerificationTtlSeconds = emailVerificationTtlSeconds;
    }

    @Transactional
    public String issue(Customer customer, AccountTokenPurpose purpose) {
        tokenRepository.deleteUnusedForCustomerAndPurpose(customer.getId(), purpose);

        String rawToken = UUID.randomUUID().toString() + UUID.randomUUID();
        CustomerAccountToken token = new CustomerAccountToken();
        token.setCustomer(customer);
        token.setTokenHash(hashToken(rawToken));
        token.setPurpose(purpose);
        long ttl = purpose == AccountTokenPurpose.PASSWORD_RESET
                ? passwordResetTtlSeconds
                : emailVerificationTtlSeconds;
        token.setExpiresAt(LocalDateTime.now().plusSeconds(ttl));
        tokenRepository.save(token);
        return rawToken;
    }

    @Transactional
    public Customer consume(String rawToken, AccountTokenPurpose purpose) {
        CustomerAccountToken token = tokenRepository
                .findByTokenHashAndPurpose(hashToken(rawToken), purpose)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token"));

        LocalDateTime now = LocalDateTime.now();
        if (token.isUsed() || token.isExpired(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
        }

        token.setUsedAt(now);
        tokenRepository.save(token);
        return token.getCustomer();
    }

    static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
