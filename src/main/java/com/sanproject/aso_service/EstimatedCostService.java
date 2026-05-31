package com.sanproject.aso_service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class EstimatedCostService {

    public BigDecimal estimateCost(String serviceType) {
        if (serviceType == null || serviceType.isBlank()) {
            return new BigDecimal("150.00");
        }

        String s = serviceType.toLowerCase();
        if (s.contains("oil")) {
            return new BigDecimal("120.00");
        }
        if (s.contains("brake")) {
            return new BigDecimal("250.00");
        }
        if (s.contains("tire") || s.contains("rotation")) {
            return new BigDecimal("80.00");
        }

        return new BigDecimal("150.00");
    }
}

