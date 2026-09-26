package com.patientmanagement.ai.provider;

public class AiProviderMalformedResponseException extends AiProviderException {

    public AiProviderMalformedResponseException(String message) {
        super(message);
    }

    public AiProviderMalformedResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
