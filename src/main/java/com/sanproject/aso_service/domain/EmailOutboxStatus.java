package com.sanproject.aso_service.domain;

/**
 * PENDING = waiting to send (or waiting for retry).
 * SENT = delivered successfully.
 * FAILED = gave up after max_attempts (still visible for debugging / future work).
 */
public enum EmailOutboxStatus {
    PENDING,
    SENT,
    FAILED
}
