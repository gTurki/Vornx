package com.example.vornx.email;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Real sending through SMTP (Amazon SES, SendGrid, Mailgun, Microsoft 365, Zoho...). */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vornx.mail.mode", havingValue = "smtp", matchIfMissing = true)
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;

    @Value("${vornx.mail.from}")
    private String from;

    @Override
    public void send(OutboxEmail email) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        // multipart = true: the email carries BOTH a plain-text and an HTML version
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(from);
        helper.setTo(email.getToAddress());
        helper.setSubject(email.getSubject());
        helper.setText(email.getTextBody(), email.getHtmlBody());
        mailSender.send(message);
    }
}
