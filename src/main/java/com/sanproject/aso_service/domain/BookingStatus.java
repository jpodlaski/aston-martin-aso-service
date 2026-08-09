package com.sanproject.aso_service.domain;

/**
 * Booking lifecycle statuses (ordinal stored in DB as SMALLINT via default @Enumerated).
 * SCHEDULED = client request awaiting consultant;
 * READY_FOR_WORK = consultant accepted + appointment set, awaiting technician claim;
 * IN_PROGRESS = claimed by a technician;
 * COMPLETED / CANCELLED are terminal.
 *
 * New values must be appended so existing ordinals stay valid.
 */
public enum BookingStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    READY_FOR_WORK
}
