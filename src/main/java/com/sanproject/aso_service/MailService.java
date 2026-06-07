package com.sanproject.aso_service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

// Sends multipart HTML emails via SMTP; optional PDF attachment for completion invoices.
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public MailService(JavaMailSender mailSender, @Value("${app.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void send(String to, RenderedEmail rendered) {
        send(to, rendered, null, null);
    }

    public void send(String to, RenderedEmail rendered, byte[] attachmentBytes, String attachmentFilename) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(rendered.getSubject());
            helper.setText(rendered.getTextBody(), rendered.getHtmlBody());

            if (attachmentBytes != null) {
                String name = attachmentFilename != null ? attachmentFilename : "invoice.pdf";
                helper.addAttachment(name, new ByteArrayResource(attachmentBytes), "application/pdf");
            }

            mailSender.send(message);
            log.info("Sent email to {} with subject '{}'", to, rendered.getSubject());
        } catch (MessagingException ex) {
            log.warn("Failed to send email to {}: {}", to, ex.getMessage());
        }
    }
}
