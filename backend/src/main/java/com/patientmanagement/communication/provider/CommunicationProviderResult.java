package com.patientmanagement.communication.provider;

public record CommunicationProviderResult(
        boolean successful,
        boolean simulated,
        String providerMessageId,
        String failureReason
) {

    public static CommunicationProviderResult sent(String providerMessageId) {
        return new CommunicationProviderResult(true, false, providerMessageId, null);
    }

    public static CommunicationProviderResult failed(String failureReason) {
        return new CommunicationProviderResult(false, false, null, failureReason);
    }

    public static CommunicationProviderResult simulation() {
        return new CommunicationProviderResult(false, true, null, null);
    }
}
