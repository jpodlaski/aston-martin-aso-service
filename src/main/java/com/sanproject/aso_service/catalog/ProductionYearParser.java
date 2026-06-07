package com.sanproject.aso_service.catalog;

import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Parses catalog era strings like "2018–2023" or "2019-present" into a descending year list.
public final class ProductionYearParser {

    private ProductionYearParser() {
    }

    public static List<Integer> parseEra(String era) {
        if (era == null || era.isBlank()) {
            return List.of();
        }

        String normalized = era.trim();

        if (normalized.matches("\\d{4}")) {
            return List.of(Integer.parseInt(normalized));
        }

        // Accept en-dash, em-dash, hyphen, or " to " as era separators.
        String[] parts = normalized.split("[\u2013\u2014-]| to ", 2);
        int from = Integer.parseInt(parts[0].trim());
        int to;
        if (parts.length == 1 || parts[1].trim().equalsIgnoreCase("present")) {
            to = Year.now().getValue();
        } else {
            to = Integer.parseInt(parts[1].trim());
        }

        if (from > to) {
            return List.of();
        }

        List<Integer> years = new ArrayList<>();
        for (int year = to; year >= from; year--) {
            years.add(year);
        }
        return Collections.unmodifiableList(years);
    }
}
