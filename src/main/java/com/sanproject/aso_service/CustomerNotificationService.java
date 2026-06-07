package com.sanproject.aso_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

// Async emails for registration and vehicle add/remove; payload is built before the async hop.
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
        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            log.warn("Skipping registration email for customer {}: no email", customer.getId());
            return;
        }

        CustomerEmailPayload payload = new CustomerEmailPayload();
        payload.setEvent("customer_registered");
        payload.setCustomerName(customer.getFirstName() + " " + customer.getLastName());
        payload.setCustomerEmail(customer.getEmail());

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

    private CustomerEmailPayload buildVehiclePayload(String event, Vehicle vehicle, Customer customer) {
        CustomerEmailPayload payload = new CustomerEmailPayload();
        payload.setEvent(event);
        payload.setCustomerName(customer.getFirstName() + " " + customer.getLastName());
        payload.setCustomerEmail(customer.getEmail());
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
