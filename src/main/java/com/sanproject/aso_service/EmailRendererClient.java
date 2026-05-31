package com.sanproject.aso_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Component
public class EmailRendererClient {

    private static final Logger log = LoggerFactory.getLogger(EmailRendererClient.class);

    private final RestClient restClient;

    public EmailRendererClient(@Value("${app.email-renderer.url}") String rendererUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(rendererUrl)
                .build();
    }

    public Optional<RenderedEmail> render(BookingEmailPayload payload) {
        try {
            RenderedEmail rendered = restClient.post()
                    .uri("/render/booking-email")
                    .body(payload)
                    .retrieve()
                    .body(RenderedEmail.class);
            return Optional.ofNullable(rendered);
        } catch (RestClientException ex) {
            log.warn("Failed to render booking email for booking {}: {}",
                    payload.getBookingId(), ex.getMessage());
            return Optional.empty();
        }
    }
}
