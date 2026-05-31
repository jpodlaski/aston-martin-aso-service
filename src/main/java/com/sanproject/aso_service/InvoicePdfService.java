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

@Service
public class InvoicePdfService {

    public byte[] createInvoicePdf(ServiceBooking booking, Customer customer) {
        BigDecimal cost = booking.getEstimatedCost();
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

                writeLine(doc, content, font, 12, 720, "Booking ID: " + booking.getId());
                writeLine(doc, content, font, 12, 700, "Customer: " + booking.getCustomerName());
                writeLine(doc, content, font, 12, 680, "Customer Email: " + safe(customer.getEmail()));

                Vehicle v = booking.getVehicle();
                writeLine(doc, content, font, 12, 660, "Vehicle: " + safe(booking.getCarModel())
                        + (v != null && v.getVin() != null ? " (VIN: " + v.getVin() + ")" : ""));

                writeLine(doc, content, font, 12, 640, "Service: " + safe(booking.getServiceType()));
                writeLine(doc, content, font, 12, 620, "Status: " + booking.getStatus());
                writeLine(doc, content, font, 12, 600, "Estimated Cost: " + costText + " " + currency);
            }

            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate invoice PDF for booking " + booking.getId(), e);
        }
    }

    private void writeLine(PDDocument doc,
                            PDPageContentStream content,
                            PDType1Font font,
                            int fontSize,
                            float y,
                            String text) throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(50, y);
        content.showText(text != null ? text : "");
        content.endText();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}

