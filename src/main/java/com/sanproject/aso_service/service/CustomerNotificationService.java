package com.sanproject.aso_service.service;

import com.sanproject.aso_service.domain.Customer;
import com.sanproject.aso_service.domain.EmailOutbox;
import com.sanproject.aso_service.domain.Vehicle;
import com.sanproject.aso_service.email.CustomerEmailPayload;
import com.sanproject.aso_service.email.EmailOutboxDispatcher;
import com.sanproject.aso_service.email.EmailOutboxService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Customer-facing emails (registration, verification, password, vehicles).
 * Builds the payload on the request thread (so action URLs / vehicle snapshots are fixed),
 * stores it in the outbox, and sends after commit with retry on failure/crash.
 */
@Service
public class CustomerNotificationService {

    private static final Logger log = LoggerFactory.getLogger(CustomerNotificationService.class);

    private final EmailOutboxService outboxService;
    private final EmailOutboxDispatcher dispatcher;

    public CustomerNotificationService(EmailOutboxService outboxService, EmailOutboxDispatcher dispatcher) {
        this.outboxService = outboxService;
        this.dispatcher = dispatcher;
    }

    public void notifyRegistered(Customer customer) {
        notifyRegistered(customer, null);
    }

    public void notifyRegistered(Customer customer, String actionUrl) {
        if (missingEmail(customer)) {
            log.warn("Skipping registration email for customer {}: no email", customer.getId());
            return;
        }
        CustomerEmailPayload payload = basePayload("customer_registered", customer);
        payload.setActionUrl(actionUrl);
        enqueue(customer.getId(), payload);
    }

    public void notifyEmailVerification(Customer customer, String actionUrl) {
        if (missingEmail(customer)) {
            log.warn("Skipping verification email for customer {}: no email", customer.getId());
            return;
        }
        CustomerEmailPayload payload = basePayload("email_verification", customer);
        payload.setActionUrl(actionUrl);
        enqueue(customer.getId(), payload);
    }

    public void notifyPasswordReset(Customer customer, String actionUrl) {
        if (missingEmail(customer)) {
            log.warn("Skipping password-reset email for customer {}: no email", customer.getId());
            return;
        }
        CustomerEmailPayload payload = basePayload("password_reset", customer);
        payload.setActionUrl(actionUrl);
        enqueue(customer.getId(), payload);
    }

    public void notifyPasswordChanged(Customer customer) {
        if (missingEmail(customer)) {
            log.warn("Skipping password-changed email for customer {}: no email", customer.getId());
            return;
        }
        enqueue(customer.getId(), basePayload("password_changed", customer));
    }

    public void notifyPasswordChanged(String name, String email) {
        if (email == null || email.isBlank()) {
            log.warn("Skipping password-changed email: no email");
            return;
        }
        CustomerEmailPayload payload = new CustomerEmailPayload();
        payload.setEvent("password_changed");
        payload.setCustomerName(name != null && !name.isBlank() ? name : "User");
        payload.setCustomerEmail(email);
        enqueue(null, payload);
    }

    public void notifyAccountDeletionRequested(Customer customer, String actionUrl) {
        if (missingEmail(customer)) {
            log.warn("Skipping account-deletion email for customer {}: no email", customer.getId());
            return;
        }
        CustomerEmailPayload payload = basePayload("account_deletion", customer);
        payload.setActionUrl(actionUrl);
        enqueue(customer.getId(), payload);
    }

    public void notifyAccountDeleted(String name, String email) {
        if (email == null || email.isBlank()) {
            log.warn("Skipping account-deleted email: no email");
            return;
        }
        CustomerEmailPayload payload = new CustomerEmailPayload();
        payload.setEvent("account_deleted");
        payload.setCustomerName(name != null && !name.isBlank() ? name : "Customer");
        payload.setCustomerEmail(email);
        enqueue(null, payload);
    }

    public void notifyVehicleAdded(Vehicle vehicle) {
        Customer customer = vehicle.getCustomer();
        if (customer == null || missingEmail(customer)) {
            log.warn("Skipping vehicle email for vehicle {}: no customer email", vehicle.getId());
            return;
        }
        enqueue(customer.getId(), buildVehiclePayload("vehicle_added", vehicle, customer));
    }

    public void notifyVehicleRemoved(Vehicle vehicle) {
        Customer customer = vehicle.getCustomer();
        if (customer == null || missingEmail(customer)) {
            log.warn("Skipping vehicle removal email for vehicle {}: no customer email", vehicle.getId());
            return;
        }
        enqueue(customer.getId(), buildVehiclePayload("vehicle_removed", vehicle, customer));
    }

    private void enqueue(Long customerId, CustomerEmailPayload payload) {
        EmailOutbox row = outboxService.enqueueCustomer(payload.getEvent(), customerId, payload);
        dispatcher.dispatchAfterCommit(row.getId());
    }

    private boolean missingEmail(Customer customer) {
        return customer.getEmail() == null || customer.getEmail().isBlank();
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
}
