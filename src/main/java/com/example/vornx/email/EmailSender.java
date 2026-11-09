package com.example.vornx.email;

/**
 * "Actually deliver this email". Two implementations, chosen by vornx.mail.mode:
 *   smtp    -> SmtpEmailSender    (production: a real mail server)
 *   preview -> PreviewEmailSender (development: an .html file you can open + a log line)
 * The rest of the app never knows which one is used.
 */
public interface EmailSender {

    void send(OutboxEmail email) throws Exception;
}
