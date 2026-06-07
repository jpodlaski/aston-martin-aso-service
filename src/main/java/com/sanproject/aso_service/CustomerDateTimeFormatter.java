package com.sanproject.aso_service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// Shared readable datetime format for emails: "2026-06-15, Monday, 10:30".
public final class CustomerDateTimeFormatter {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd, EEEE, HH:mm", Locale.ENGLISH);

    private CustomerDateTimeFormatter() {
    }

    public static String format(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(FORMATTER) : null;
    }
}
