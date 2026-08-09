package com.sanproject.aso_service.domain;

/** BOOKING emails rebuild payload from booking_id; CUSTOMER emails store a JSON snapshot. */
public enum EmailOutboxChannel {
    BOOKING,
    CUSTOMER
}
