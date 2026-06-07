package com.sanproject.aso_service;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// Enables @Async for booking and customer notification services.
@Configuration
@EnableAsync
public class AsyncConfig {
}
