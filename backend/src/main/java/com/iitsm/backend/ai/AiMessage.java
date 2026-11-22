package com.iitsm.backend.ai;

/** One turn of a conversation with the AI: said by the user, or by the assistant earlier. */
public record AiMessage(Speaker speaker, String text) {

    public enum Speaker { USER, ASSISTANT }

    public static AiMessage user(String text) {
        return new AiMessage(Speaker.USER, text);
    }
}
