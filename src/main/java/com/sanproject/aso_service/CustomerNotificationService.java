package com.sanproject.aso_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Async emails for registration, verification, password reset, and vehicle add/remove.
 * Payload is built on the request thread; actual SMTP send happens asynchronously.
 */
@Service
public class CustomerNotificationService {

    private static final Logger log = LoggerFactory.getLogger(CustomerNotificationService.class);

    private final EmailRendererClient emailRendererClient;
    private final MailService mailService;

    public CustomerNotificationService(EmailRendererClient emailRendererClient, MailService mailService) {
        this.emailRendererClient = emailRendererClient;
        this.mailService = mailService;
    }

    @Async
    public void notifyRegistered(Customer customer) {
        notifyRegistered(customer, null);
    }

    @Async
    public void notifyRegistered(Customer customer, String actionUrl) {
        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            log.warn("Skipping registration email for customer {}: no email", customer.getId());
            return;
        }

        CustomerEmailPayload payload = basePayload("customer_registered", customer);
        payload.setActionUrl(actionUrl);
        send(payload);
    }

    @Async
    public void notifyEmailVerification(Customer customer, String actionUrl) {
        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            log.warn("Skipping verification email for customer {}: no email", customer.getId());
            return;
        }

        CustomerEmailPayload payload = basePayload("email_verification", customer);
        payload.setActionUrl(actionUrl);
        send(payload);
    }

    @Async
    public void notifyPasswordReset(Customer customer, String actionUrl) {
        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            log.warn("Skipping password-reset email for customer {}: no email", customer.getId());
            return;
        }

        CustomerEmailPayload payload = basePayload("password_reset", customer);
        payload.setActionUrl(actionUrl);
        send(payload);
    }

    @Async
    public void notifyPasswordChanged(Customer customer) {
        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            log.warn("Skipping password-changed email for customer {}: no email", customer.getId());
            return;
        }
        send(basePayload("password_changed", customer));
    }

    @Async
    public void notifyPasswordChanged(String name, String email) {
        if (email == null || email.isBlank()) {
            log.warn("Skipping password-changed email: no email");
            return;
        }
        CustomerEmailPayload payload = new CustomerEmailPayload();
        payload.setEvent("password_changed");
        payload.setCustomerName(name != null && !name.isBlank() ? name : "User");
        payload.setCustomerEmail(email);
        send(payload);
    }

    @Async
    public void notifyVehicleAdded(Vehicle vehicle) {
        Customer customer = vehicle.getCustomer();
        if (customer == null || customer.getEmail() == null || customer.getEmail().isBlank()) {
            log.warn("Skipping vehicle email for vehicle {}: no customer email", vehicle.getId());
            return;
        }

        send(buildVehiclePayload("vehicle_added", vehicle, customer));
    }

    @Async
    public void notifyVehicleRemoved(Vehicle vehicle) {
        Customer customer = vehicle.getCustomer();
        if (customer == null || customer.getEmail() == null || customer.getEmail().isBlank()) {
            log.warn("Skipping vehicle removal email for vehicle {}: no customer email", vehicle.getId());
            return;
        }

        CustomerEmailPayload payload = buildVehiclePayload("vehicle_removed", vehicle, customer);
        send(payload);
    }

    private CustomerEmailPayload basePayload(String event, Customer customer) {
        CustomerEmailPayload payload = new CustomerEmailPayload();
        payload.setEvent(event);
        payload.setCustomerName(customer.getFirstName() + " " + customer.getLastName());
        payload.setCustomerEmail(customer.getEmail());
        return payload;
    }

    private CustomerEmailPayload buildVehiclePayload(String event, Vehicle vehicle, Customer customer) {
        CustomerEmailPayload payload = basePayload(event, customer);
        // Prefer model line (e.g. DBX) over full catalog name in vehicle emails.
        payload.setCarModel(vehicle.getModelLine() != null ? vehicle.getModelLine() : vehicle.getModel());
        payload.setVin(vehicle.getVin());
        payload.setModelLine(vehicle.getModelLine());
        payload.setProductionYear(vehicle.getProductionYear());
        payload.setBodyStyle(vehicle.getBodyStyle());
        payload.setEngine(vehicle.getEngine());
        payload.setPower(vehicle.getPower());
        payload.setTransmission(vehicle.getTransmission());
        payload.setDrivetrain(vehicle.getDrivetrain());
        return payload;
    }

    private void send(CustomerEmailPayload payload) {
        emailRendererClient.renderCustomer(payload).ifPresentOrElse(
                rendered -> mailService.send(payload.getCustomerEmail(), rendered),
                () -> log.warn("Failed to send {} email to {}", payload.getEvent(), payload.getCustomerEmail())
        );
    }
}
