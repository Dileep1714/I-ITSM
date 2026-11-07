package com.iitsm.backend.notification;

/**
 * Sends one email. <b>Adapter target:</b> the rest of the system talks to this small interface, never to a mail
 * library directly. Swapping SMTP for SendGrid or Amazon SES means writing one new adapter.
 */
public interface EmailSender {

    void send(String to, String subject, String body);

    /** For the admin screen: is this a real sender or a simulation? */
    boolean isSimulated();
}
