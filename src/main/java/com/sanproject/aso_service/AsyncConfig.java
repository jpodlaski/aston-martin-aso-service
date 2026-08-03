package com.sanproject.aso_service;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Turns on Spring's @Async support used by notification executors. */
@Configuration
@EnableAsync
public class AsyncConfig {
}
