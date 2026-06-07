package com.sanproject.aso_service;

// SCHEDULED = awaiting claim; IN_PROGRESS = claimed; terminal states are COMPLETED and CANCELLED.
public enum BookingStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
