package com.example.vornx.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * DEVELOPMENT ONLY: instead of sending, saves each email as an .html file (open it in your browser)
 * and logs one line. No mail server needed.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "vornx.mail.mode", havingValue = "preview")
public class PreviewEmailSender implements EmailSender {

    private final Path dir;

    public PreviewEmailSender(@Value("${vornx.mail.preview-dir:mail-preview}") String dir) {
        this.dir = Path.of(dir).toAbsolutePath();
    }

    @Override
    public void send(OutboxEmail email) throws Exception {
        Files.createDirectories(dir);
        String safeTo = email.getToAddress().replaceAll("[^A-Za-z0-9@._-]", "_");
        Path file = dir.resolve(String.format("%05d-%s-%s.html", email.getId(), email.getType(), safeTo));
        Files.writeString(file, email.getHtmlBody(), StandardCharsets.UTF_8);
        log.info("[EMAIL PREVIEW] to={} subject=\"{}\" -> {}", email.getToAddress(), email.getSubject(), file.toUri());
    }
}
