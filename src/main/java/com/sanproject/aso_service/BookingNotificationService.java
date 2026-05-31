package com.sanproject.aso_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class BookingNotificationService {

    private static final Logger log = LoggerFactory.getLogger(BookingNotificationService.class);

    private final EmailRendererClient emailRendererClient;
    private final MailService mailService;
    private final InvoicePdfService invoicePdfService;

    public BookingNotificationService(
            EmailRendererClient emailRendererClient,
            MailService mailService,
            InvoicePdfService invoicePdfService) {
        this.emailRendererClient = emailRendererClient;
        this.mailService = mailService;
        this.invoicePdfService = invoicePdfService;
    }

    @Async
    public void notifyCreated(ServiceBooking booking) {
        sendNotification(booking, "created", null);
    }

    @Async
    public void notifyStatusChanged(ServiceBooking booking, BookingStatus previousStatus) {
        sendNotification(booking, "status_changed", previousStatus);
    }

    private void sendNotification(ServiceBooking booking, String event, BookingStatus previousStatus) {
        Optional<Customer> customer = resolveCustomer(booking);
        if (customer.isEmpty()) {
            log.warn("Skipping email for booking {}: no customer with email", booking.getId());
            return;
        }

        Customer resolved = customer.get();
        BookingEmailPayload payload = new BookingEmailPayload();
        payload.setEvent(event);
        payload.setCustomerName(booking.getCustomerName());
        payload.setCustomerEmail(resolved.getEmail());
        payload.setServiceType(booking.getServiceType());
        payload.setCarModel(booking.getCarModel());
        payload.setStatus(booking.getStatus().name());
        if (previousStatus != null) {
            payload.setPreviousStatus(previousStatus.name());
        }
        payload.setBookingId(booking.getId());
        if (booking.getEstimatedCost() != null) {
            payload.setEstimatedCost(booking.getEstimatedCost().toPlainString());
        }
        payload.setCurrency("EUR");

        emailRendererClient.render(payload).ifPresent(rendered -> {
            if ("status_changed".equals(event) && booking.getStatus() == BookingStatus.COMPLETED) {
                try {
                    byte[] pdf = invoicePdfService.createInvoicePdf(booking, resolved);
                    log.info("Generated invoice PDF for booking {} ({} bytes)", booking.getId(), pdf.length);
                    mailService.send(
                            resolved.getEmail(),
                            rendered,
                            pdf,
                            "invoice-booking-" + booking.getId() + ".pdf"
                    );
                    return;
                } catch (Exception e) {
                    log.error("Failed to generate/send invoice PDF for booking {}", booking.getId(), e);
                }
            }

            mailService.send(resolved.getEmail(), rendered);
        });
    }


    private Optional<Customer> resolveCustomer(ServiceBooking booking) {
        Vehicle vehicle = booking.getVehicle();
        if (vehicle == null || vehicle.getCustomer() == null) {
            return Optional.empty();
        }
        Customer customer = vehicle.getCustomer();
        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(customer);
    }
}
