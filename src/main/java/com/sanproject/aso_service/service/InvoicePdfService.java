package com.sanproject.aso_service.service;

import com.sanproject.aso_service.domain.Customer;
import com.sanproject.aso_service.domain.ServiceBooking;
import com.sanproject.aso_service.domain.Vehicle;
import com.sanproject.aso_service.email.CustomerDateTimeFormatter;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Branded PDF invoice (completion email + client download).
 * Visual language matches the ASO email templates: graphite, racing green, logo header.
 */
@Service
public class InvoicePdfService {

    private static final String BRAND_NAME = "Aston Martin ASO Service";
    /** Helvetica/WinAnsi cannot render Ł — keep ASCII for PDF Type1 fonts. */
    private static final String WORKSHOP_ADDRESS = "Sportowa 31, Lodz, Poland";
    private static final String WORKSHOP_HOURS = "06:00-20:00";
    private static final String CURRENCY = "EUR";

    private static final Color INK = new Color(0x0a, 0x0c, 0x0e);
    private static final Color INK_SOFT = new Color(0x14, 0x18, 0x1c);
    private static final Color HEADER_BG = new Color(0x00, 0x39, 0x3d);
    private static final Color SNOW = new Color(0xf3, 0xf4, 0xf2);
    private static final Color MUTED = new Color(0x8a, 0x8e, 0x89);
    private static final Color ACCENT_SOFT = new Color(0x8f, 0xbf, 0xa5);
    private static final Color ACCENT_BORDER = new Color(0x2f, 0x6b, 0x50);

    private static final ClassPathResource LOGO =
            new ClassPathResource("email/aston-martin-logo.png");

    private static final float MARGIN_X = 48f;
    private static final float LABEL_WIDTH = 130f;

    public byte[] createInvoicePdf(ServiceBooking booking, Customer customer) {
        BigDecimal cost = booking.getFinalCost() != null
                ? booking.getFinalCost()
                : booking.getEstimatedCost();
        String costText = cost != null ? cost.toPlainString() : "N/A";

        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDRectangle box = page.getMediaBox();
            float pageW = box.getWidth();
            float pageH = box.getHeight();

            PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            PDImageXObject logo = loadLogo(doc);

            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                // Page background
                fillRect(content, 0, 0, pageW, pageH, INK);

                // Header band
                float headerH = 110f;
                fillRect(content, 0, pageH - headerH, pageW, headerH, HEADER_BG);
                strokeLine(content, 0, pageH - headerH, pageW, pageH - headerH, ACCENT_BORDER);

                if (logo != null) {
                    float logoW = 200f;
                    float scale = logoW / logo.getWidth();
                    float logoH = logo.getHeight() * scale;
                    float logoX = (pageW - logoW) / 2f;
                    float logoY = pageH - headerH + (headerH - logoH) / 2f;
                    content.drawImage(logo, logoX, logoY, logoW, logoH);
                }

                float y = pageH - headerH - 36f;

                // Title + meta
                y = drawText(content, fontBold, 11, MARGIN_X, y, "INVOICE", ACCENT_SOFT);
                y -= 6f;
                y = drawText(content, fontBold, 18, MARGIN_X, y,
                        "Booking #" + booking.getId(), SNOW);
                y -= 8f;
                y = drawText(content, font, 11, MARGIN_X, y,
                        "Issued " + formatDate(LocalDate.now()), MUTED);
                y -= 22f;

                // Details card
                List<String[]> rows = buildDetailRows(booking, customer, costText);
                float cardTop = y + 14f;
                float rowsHeight = 28f;
                for (String[] row : rows) {
                    int lineCount = wrap(row[1], font, 11, pageW - MARGIN_X * 2 - LABEL_WIDTH - 16).size();
                    rowsHeight += Math.max(18f, lineCount * 16f + 2f);
                }
                float cardBottom = cardTop - rowsHeight;
                fillRect(content, MARGIN_X - 12f, cardBottom, pageW - (MARGIN_X - 12f) * 2, rowsHeight, INK_SOFT);
                strokeRect(content, MARGIN_X - 12f, cardBottom, pageW - (MARGIN_X - 12f) * 2, rowsHeight, ACCENT_BORDER);

                y = cardTop - 20f;
                for (String[] row : rows) {
                    y = drawDetailRow(content, font, fontBold, y, row[0], row[1], pageW);
                }

                y = cardBottom - 28f;

                // Total highlight
                float totalH = 48f;
                float totalBottom = y - totalH;
                fillRect(content, MARGIN_X - 12f, totalBottom, pageW - (MARGIN_X - 12f) * 2, totalH, HEADER_BG);
                strokeRect(content, MARGIN_X - 12f, totalBottom, pageW - (MARGIN_X - 12f) * 2, totalH, ACCENT_BORDER);
                drawText(content, font, 11, MARGIN_X, totalBottom + 28f, "TOTAL DUE", ACCENT_SOFT);
                drawText(content, fontBold, 16, MARGIN_X, totalBottom + 12f,
                        costText + " " + CURRENCY, SNOW);

                // Footer
                float footerH = 88f;
                fillRect(content, 0, 0, pageW, footerH, HEADER_BG);
                strokeLine(content, 0, footerH, pageW, footerH, ACCENT_BORDER);
                float fy = footerH - 22f;
                fy = drawText(content, font, 9, MARGIN_X, fy, "SERVICE LOCATION", ACCENT_SOFT);
                fy -= 4f;
                fy = drawText(content, fontBold, 11, MARGIN_X, fy, WORKSHOP_ADDRESS, SNOW);
                fy -= 8f;
                fy = drawText(content, font, 10, MARGIN_X, fy,
                        "Pickup during workshop hours, " + WORKSHOP_HOURS + ".", MUTED);
                fy -= 8f;
                drawText(content, font, 10, MARGIN_X, fy, BRAND_NAME, SNOW);
            }

            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate invoice PDF for booking " + booking.getId(), e);
        }
    }

    private List<String[]> buildDetailRows(ServiceBooking booking, Customer customer, String costText) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Customer", safe(booking.getCustomerName())});
        rows.add(new String[]{"Email", safe(customer.getEmail())});

        Vehicle v = booking.getVehicle();
        rows.add(new String[]{"Vehicle", safe(booking.getCarModel())});
        if (v != null && v.getVin() != null && !v.getVin().isBlank()) {
            rows.add(new String[]{"VIN", pdfSafe(v.getVin())});
        }
        if (booking.getCustomerDescription() != null && !booking.getCustomerDescription().isBlank()) {
            rows.add(new String[]{"Issue", pdfSafe(booking.getCustomerDescription())});
        }
        String services = formatServiceTypes(booking.getServiceTypes());
        if (!services.isBlank()) {
            rows.add(new String[]{"Services", services});
        }
        if (booking.getScheduledDateTime() != null) {
            rows.add(new String[]{"Appointment", formatDateTime(booking.getScheduledDateTime())});
        }
        rows.add(new String[]{"Final cost", costText + " " + CURRENCY});
        return rows;
    }

    private float drawDetailRow(PDPageContentStream content,
                                PDType1Font font,
                                PDType1Font fontBold,
                                float y,
                                String label,
                                String value,
                                float pageW) throws IOException {
        float valueX = MARGIN_X + LABEL_WIDTH;
        float maxValueWidth = pageW - valueX - MARGIN_X;
        List<String> lines = wrap(value, font, 11, maxValueWidth);

        drawText(content, font, 9, MARGIN_X, y, label.toUpperCase(), ACCENT_SOFT);
        float lineY = y;
        for (String line : lines) {
            drawText(content, fontBold, 11, valueX, lineY, line, SNOW);
            lineY -= 16f;
        }
        return y - Math.max(18f, lines.size() * 16f + 2f);
    }

    private PDImageXObject loadLogo(PDDocument doc) {
        try (InputStream in = LOGO.getInputStream()) {
            return PDImageXObject.createFromByteArray(doc, in.readAllBytes(), "aso-logo");
        } catch (IOException ex) {
            return null;
        }
    }

    private void fillRect(PDPageContentStream content, float x, float y, float w, float h, Color color)
            throws IOException {
        content.setNonStrokingColor(color);
        content.addRect(x, y, w, h);
        content.fill();
    }

    private void strokeRect(PDPageContentStream content, float x, float y, float w, float h, Color color)
            throws IOException {
        content.setStrokingColor(color);
        content.setLineWidth(1f);
        content.addRect(x, y, w, h);
        content.stroke();
    }

    private void strokeLine(PDPageContentStream content, float x1, float y1, float x2, float y2, Color color)
            throws IOException {
        content.setStrokingColor(color);
        content.setLineWidth(1f);
        content.moveTo(x1, y1);
        content.lineTo(x2, y2);
        content.stroke();
    }

    private float drawText(PDPageContentStream content,
                           PDType1Font font,
                           float size,
                           float x,
                           float y,
                           String text,
                           Color color) throws IOException {
        content.setNonStrokingColor(color);
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(pdfSafe(text));
        content.endText();
        return y - (size + 4f);
    }

    private List<String> wrap(String text, PDType1Font font, float fontSize, float maxWidth)
            throws IOException {
        String safe = pdfSafe(text);
        List<String> lines = new ArrayList<>();
        if (safe.isBlank()) {
            lines.add("");
            return lines;
        }
        String[] words = safe.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            float width = font.getStringWidth(candidate) / 1000f * fontSize;
            if (width <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
            } else {
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                }
                current.setLength(0);
                current.append(word);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return CustomerDateTimeFormatter.format(dateTime);
    }

    private String formatDate(LocalDate date) {
        return date.toString();
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

    private String formatServiceTypes(List<String> serviceTypes) {
        if (serviceTypes == null || serviceTypes.isEmpty()) {
            return "";
        }
        return String.join(", ", serviceTypes);
    }
}
