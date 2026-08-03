package com.sanproject.aso_service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * @Async runs send() on a thread pool so the HTTP response is not blocked waiting for SMTP.
 * Kept as its own bean: Spring AOP proxies only work across beans — @Async on a private method
 * of the same class would be ignored (self-invocation).
 */
@Component
public class BookingNotificationExecutor {

    private final BookingNotificationDelivery delivery;

    public BookingNotificationExecutor(BookingNotificationDelivery delivery) {
        this.delivery = delivery;
    }

    @Async
    public void send(Long bookingId, String event, BookingStatus previousStatus) {
        // Second hop into another bean so @Transactional on deliver() is also applied by the proxy.
        delivery.deliver(bookingId, event, previousStatus);
    }
}
