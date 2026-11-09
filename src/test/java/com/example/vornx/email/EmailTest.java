package com.example.vornx.email;

import com.example.vornx.security.UserType;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** No Spring, no database, no mail server: renders the real template with a plain Thymeleaf engine. */
class EmailTest {

    private final EmailTemplates templates = new EmailTemplates(
            new FrontendProperties("https://admin.vornx.sa", "https://partner.vornx.sa", "https://my.vornx.sa"));

    private static String render(EmailContent content) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        SpringTemplateEngine engine = new SpringTemplateEngine(); // SpEL expressions, like the real app
        engine.setTemplateResolver(resolver);
        Context ctx = new Context();
        ctx.setVariable("c", content);
        return engine.process("email/base", ctx);
    }

    @Test
    void namesAreEscaped_soNobodyCanInjectHtmlIntoOurEmails() {
        String html = render(templates.customerWelcome("<script>alert(1)</script>", "VX0000000001"));
        assertFalse(html.contains("<script>alert(1)</script>"));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
    }

    @Test
    void resetEmailHasTheLinkTheArabicRtlPartAndAPlainTextVersion() {
        EmailContent c = templates.passwordReset("Fahad", UserType.CUSTOMER, "pr_abc+/=", 30);
        String html = render(c);
        assertTrue(html.contains("https://my.vornx.sa/reset-password?token=pr_abc%2B%2F%3D")); // token is URL-encoded
        assertTrue(html.contains("dir=\"rtl\""));
        assertTrue(html.contains("إعادة تعيين كلمة المرور"));
        assertTrue(c.toPlainText().contains("Set a new password: https://my.vornx.sa/reset-password?token="));
    }

    @Test
    void failedSendsAreRetriedWithGrowingDelaysThenGiveUp() {
        Instant t0 = Instant.parse("2026-11-09T10:00:00Z");
        OutboxEmail email = new OutboxEmail(EmailType.PASSWORD_RESET, "a@b.c", "s", "<p>h</p>", "t", t0);

        email.markFailedAttempt("timeout", t0);
        assertEquals(t0.plus(Duration.ofMinutes(1)), email.getNextAttemptAt());
        email.markFailedAttempt("timeout", t0);
        assertEquals(t0.plus(Duration.ofMinutes(5)), email.getNextAttemptAt());
        email.markFailedAttempt("timeout", t0);
        email.markFailedAttempt("timeout", t0);
        assertEquals(OutboxEmail.Status.PENDING, email.getStatus()); // still trying after 4 failures
        email.markFailedAttempt("timeout", t0);
        assertEquals(OutboxEmail.Status.FAILED, email.getStatus()); // 5th failure: stop
        assertEquals(5, email.getAttempts());
    }
}
