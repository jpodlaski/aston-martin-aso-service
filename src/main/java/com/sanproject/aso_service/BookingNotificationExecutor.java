package com.sanproject.aso_service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

// Runs email delivery off the HTTP thread; must stay separate from @Transactional delivery.
@Component
public class BookingNotificationExecutor {

    private final BookingNotificationDelivery delivery;

    public BookingNotificationExecutor(BookingNotificationDelivery delivery) {
        this.delivery = delivery;
    }

    @Async
    public void send(Long bookingId, String event, BookingStatus previousStatus) {
        // Proxy call into another bean so @Transactional on deliver() is applied.
        delivery.deliver(bookingId, event, previousStatus);
    }
}
