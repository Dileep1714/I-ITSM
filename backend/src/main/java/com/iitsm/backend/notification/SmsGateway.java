package com.iitsm.backend.notification;

/**
 * Sends one text message. <b>Adapter target</b>, like {@link EmailSender}: a real provider (Twilio, AWS SNS…)
 * would be one more implementation.
 */
public interface SmsGateway {

    void send(String recipientName, String text);
}
