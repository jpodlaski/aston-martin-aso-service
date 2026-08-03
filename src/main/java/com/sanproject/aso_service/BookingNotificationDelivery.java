package com.sanproject.aso_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Pipeline step that actually builds and sends the email:
 * 1) reload booking + associations inside a read-only transaction
 * 2) POST JSON payload to the Clojure email-renderer microservice
 * 3) send HTML/text via SMTP (Mailhog locally); attach PDF invoice on completion
 */
@Service
public class BookingNotificationDelivery {

    private static final Logger log = LoggerFactory.getLogger(BookingNotificationDelivery.class);

    private final ServiceBookingRepository bookingRepository;
    private final EmailRendererClient emailRendererClient;
    private final MailService mailService;
    private final InvoicePdfService invoicePdfService;

    public BookingNotificationDelivery(
            ServiceBookingRepository bookingRepository,
            EmailRendererClient emailRendererClient,
            MailService mailService,
            InvoicePdfService invoicePdfService) {
        this.bookingRepository = bookingRepository;
        this.emailRendererClient = emailRendererClient;
        this.mailService = mailService;
        this.invoicePdfService = invoicePdfService;
    }

    @Transactional(readOnly = true)
    public void deliver(Long bookingId, String event, BookingStatus previousStatus) {
        // Eager-fetch associations; plain findById leaves lazy collections unusable here.
        ServiceBooking booking = bookingRepository.findByIdWithDetails(bookingId).orElse(null);
        if (booking == null) {
            log.warn("Booking {} not found for {} email", bookingId, event);
            return;
        }

        initializeAssociations(booking);

        Optional<Customer> customer = resolveCustomer(booking);
        if (customer.isEmpty()) {
            log.warn("Skipping email for booking {}: no customer with email", booking.getId());
            return;
        }

        Customer resolved = customer.get();
        BookingEmailPayload payload = buildPayload(booking, event, previousStatus, resolved.getEmail());

        emailRendererClient.render(payload).ifPresentOrElse(
                rendered -> sendRenderedEmail(booking, event, resolved, rendered),
                () -> log.warn("Failed to render {} email for booking {}", event, booking.getId())
        );
    }

    // Touch lazy fields while the Hibernate session is still open.
    private void initializeAssociations(ServiceBooking booking) {
        if (booking.getVehicle() != null) {
            booking.getVehicle().getVin();
            if (booking.getVehicle().getCustomer() != null) {
                booking.getVehicle().getCustomer().getEmail();
            }
        }
        if (booking.getAssignedWorker() != null) {
            booking.getAssignedWorker().getFirstName();
        }
        booking.getServiceTypes().size();
    }

    private void sendRenderedEmail(
            ServiceBooking booking,
            String event,
            Customer customer,
            RenderedEmail rendered) {
        // Completion emails include an invoice PDF when generation succeeds.
        if ("booking_completed".equals(event)) {
            byte[] pdf = null;
            try {
                pdf = invoicePdfService.createInvoicePdf(booking, customer);
                log.info("Generated invoice PDF for booking {} ({} bytes)", booking.getId(), pdf.length);
            } catch (Exception e) {
                log.error("Failed to generate invoice PDF for booking {}, sending email without attachment",
                        booking.getId(), e);
            }

            if (pdf != null) {
                mailService.send(
                        customer.getEmail(),
                        rendered,
                        pdf,
                        "invoice-booking-" + booking.getId() + ".pdf"
                );
                return;
            }
        }

        mailService.send(customer.getEmail(), rendered);
    }

    private BookingEmailPayload buildPayload(
            ServiceBooking booking,
            String event,
            BookingStatus previousStatus,
            String customerEmail) {
        BookingEmailPayload payload = new BookingEmailPayload();
        payload.setEvent(event);
        payload.setCustomerName(booking.getCustomerName());
        payload.setCustomerEmail(customerEmail);
        payload.setCustomerDescription(booking.getCustomerDescription());
        List<String> serviceTypes = booking.getServiceTypes();
        if (serviceTypes != null && !serviceTypes.isEmpty()) {
            payload.setServiceTypes(serviceTypes);
        }
        payload.setCarModel(resolveCarModelForEmail(booking));
        payload.setStatus(booking.getStatus().name());
        if (previousStatus != null) {
            payload.setPreviousStatus(previousStatus.name());
        }
        payload.setBookingId(booking.getId());
        if (booking.getEstimatedCost() != null) {
            payload.setEstimatedCost(booking.getEstimatedCost().toPlainString());
        }
        if (booking.getFinalCost() != null) {
            payload.setFinalCost(booking.getFinalCost().toPlainString());
        }
        payload.setCurrency("EUR");
        payload.setEstimatedDropOffTime(formatDateTime(booking.getEstimatedDropOffTime()));
        payload.setAvailabilityNotes(booking.getAvailabilityNotes());
        payload.setScheduledDateTime(formatDateTime(booking.getScheduledDateTime()));
        if (booking.getCancellationReason() != null) {
            payload.setCancellationReason(booking.getCancellationReason());
        }
        if (booking.getCancelledBy() != null) {
            payload.setCancelledBy(booking.getCancelledBy().name());
        }
        return payload;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return CustomerDateTimeFormatter.format(dateTime);
    }

    private String resolveCarModelForEmail(ServiceBooking booking) {
        Vehicle vehicle = booking.getVehicle();
        // Prefer short model line (e.g. "DBX") over full catalog name in emails.
        if (vehicle != null && vehicle.getModelLine() != null && !vehicle.getModelLine().isBlank()) {
            return vehicle.getModelLine();
        }
        return booking.getCarModel();
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
