package com.sanproject.aso_service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Builds a simple PDF invoice with Apache PDFBox for the booking_completed email attachment.
 * Not a full accounting system — just enough for the demo "invoice on completion" story.
 */
@Service
public class InvoicePdfService {

    public byte[] createInvoicePdf(ServiceBooking booking, Customer customer) {
        // Prefer final cost; fall back to estimate when final is missing.
        BigDecimal cost = booking.getFinalCost() != null
                ? booking.getFinalCost()
                : booking.getEstimatedCost();
        String costText = cost != null ? cost.toPlainString() : "N/A";
        String currency = "EUR";

        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                content.beginText();
                content.setFont(fontBold, 18);
                content.newLineAtOffset(50, 760);
                content.showText("Aston Martin ASO Service Invoice");
                content.endText();

                float y = 720;
                y = writeLine(doc, content, font, 12, y, "Booking ID: " + booking.getId());
                y = writeLine(doc, content, font, 12, y, "Customer: " + booking.getCustomerName());
                y = writeLine(doc, content, font, 12, y, "Customer Email: " + safe(customer.getEmail()));

                Vehicle v = booking.getVehicle();
                y = writeLine(doc, content, font, 12, y, "Vehicle: " + safe(booking.getCarModel())
                        + (v != null && v.getVin() != null ? " (VIN: " + v.getVin() + ")" : ""));

                y = writeLine(doc, content, font, 12, y, "Customer report: " + safe(booking.getCustomerDescription()));
                y = writeLine(doc, content, font, 12, y, "Services: " + formatServiceTypes(booking.getServiceTypes()));

                if (booking.getEstimatedDropOffTime() != null) {
                    y = writeLine(doc, content, font, 12, y,
                            "Estimated drop-off: " + formatDateTime(booking.getEstimatedDropOffTime()));
                }
                if (booking.getAvailabilityNotes() != null && !booking.getAvailabilityNotes().isBlank()) {
                    y = writeLine(doc, content, font, 12, y,
                            "Availability: " + booking.getAvailabilityNotes());
                }
                if (booking.getScheduledDateTime() != null) {
                    y = writeLine(doc, content, font, 12, y,
                            "Appointment: " + formatDateTime(booking.getScheduledDateTime()));
                }

                y = writeLine(doc, content, font, 12, y, "Status: " + booking.getStatus());
                writeLine(doc, content, font, 12, y, "Final cost: " + costText + " " + currency);
            }

            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate invoice PDF for booking " + booking.getId(), e);
        }
    }

    private float writeLine(PDDocument doc,
                            PDPageContentStream content,
                            PDType1Font font,
                            int fontSize,
                            float y,
                            String text) throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(50, y);
        content.showText(pdfSafe(text));
        content.endText();
        return y - 20;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return CustomerDateTimeFormatter.format(dateTime);
    }

    private String safe(String s) {
        return pdfSafe(s);
    }

    private String pdfSafe(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sanitized = new StringBuilder(text.length());
        for (char ch : text.toCharArray()) {
            sanitized.append(ch <= 255 ? ch : '?');
        }
        return sanitized.toString();
    }

    private String formatServiceTypes(java.util.List<String> serviceTypes) {
        if (serviceTypes == null || serviceTypes.isEmpty()) {
            return "";
        }
        return String.join(", ", serviceTypes);
    }
}
