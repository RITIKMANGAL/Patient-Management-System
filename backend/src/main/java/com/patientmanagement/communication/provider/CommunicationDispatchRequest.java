package com.patientmanagement.communication.provider;

import com.patientmanagement.communication.model.CommunicationChannel;
import com.patientmanagement.communication.model.CommunicationType;
import java.util.UUID;

public record CommunicationDispatchRequest(
        UUID communicationId,
        CommunicationType type,
        CommunicationChannel channel,
        String recipient,
        String message,
        String sender,
        String templateId
) {

    @Override
    public String toString() {
        return "CommunicationDispatchRequest[communicationId=" + communicationId
                + ", type=" + type
                + ", channel=" + channel
                + ", sender=" + sender
                + ", templateId=" + templateId
                + ", recipient=<redacted>, message=<redacted>]";
    }
}
