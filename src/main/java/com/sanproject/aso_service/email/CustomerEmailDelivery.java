package com.sanproject.aso_service.email;

import com.sanproject.aso_service.domain.Customer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends a customer email from a stored outbox payload (registration, verify, vehicles, etc.).
 */
@Service
public class CustomerEmailDelivery {

    private static final Logger log = LoggerFactory.getLogger(CustomerEmailDelivery.class);

    private final EmailRendererClient emailRendererClient;
    private final MailService mailService;

    public CustomerEmailDelivery(EmailRendererClient emailRendererClient, MailService mailService) {
        this.emailRendererClient = emailRendererClient;
        this.mailService = mailService;
    }

    public void deliver(CustomerEmailPayload payload) {
        if (payload.getCustomerEmail() == null || payload.getCustomerEmail().isBlank()) {
            throw new EmailDeliveryException("Customer email payload has no recipient");
        }

        RenderedEmail rendered = emailRendererClient.renderCustomer(payload)
                .orElseThrow(() -> new EmailDeliveryException(
                        "Failed to render customer email event " + payload.getEvent()));

        mailService.send(payload.getCustomerEmail(), rendered);
        log.info("Delivered customer email event {} to {}", payload.getEvent(), payload.getCustomerEmail());
    }
}
