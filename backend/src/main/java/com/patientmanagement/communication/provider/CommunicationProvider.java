package com.patientmanagement.communication.provider;

import com.patientmanagement.communication.model.CommunicationChannel;

public interface CommunicationProvider {

    CommunicationChannel channel();

    CommunicationProviderResult send(CommunicationDispatchRequest request);
}
