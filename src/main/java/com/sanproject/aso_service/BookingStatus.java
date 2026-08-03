package com.sanproject.aso_service;

/**
 * Booking lifecycle statuses (ordinal stored in DB as SMALLINT via default @Enumerated).
 * SCHEDULED = request in the workshop queue; IN_PROGRESS = claimed by a worker;
 * COMPLETED / CANCELLED are terminal (no further transitions).
 */
public enum BookingStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
