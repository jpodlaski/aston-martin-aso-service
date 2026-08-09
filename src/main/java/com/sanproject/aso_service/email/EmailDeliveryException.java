package com.sanproject.aso_service.email;

/**
 * Thrown when render/SMTP fails so the outbox processor can retry the PENDING row.
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
