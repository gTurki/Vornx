package com.example.vornx.email;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Clock;
import java.time.Instant;

/**
 * Other modules call queue(...) INSIDE their transaction. The email is rendered now and saved in the outbox;
 * EmailOutboxJob sends it a few seconds later. If the action is rolled back, the email disappears with it.
 */
@Service
@RequiredArgsConstructor
public class EmailService {

    private final OutboxEmailRepository outboxRepository;
    private final ITemplateEngine templateEngine;
    private final Clock clock;

    /** MANDATORY: must be called from inside a transaction, so the email is tied to the action. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void queue(String to, EmailType type, EmailContent content) {
        Context ctx = new Context();
        ctx.setVariable("c", content);
        String html = templateEngine.process("email/base", ctx);
        outboxRepository.save(new OutboxEmail(type, to, content.subject(), html, content.toPlainText(),
                Instant.now(clock)));
    }
}
