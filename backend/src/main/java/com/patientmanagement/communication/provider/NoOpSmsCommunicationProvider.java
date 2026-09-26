package com.patientmanagement.communication.provider;

import com.patientmanagement.communication.model.CommunicationChannel;

public class NoOpSmsCommunicationProvider implements CommunicationProvider {

    @Override
    public CommunicationChannel channel() {
        return CommunicationChannel.SMS;
    }

    @Override
    public CommunicationProviderResult send(CommunicationDispatchRequest request) {
        return CommunicationProviderResult.simulation();
    }
}
