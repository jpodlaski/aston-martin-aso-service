package com.sanproject.aso_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Async outbox dispatch + scheduled retry of PENDING email_outbox rows after crashes. */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
