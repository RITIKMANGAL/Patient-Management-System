package com.patientmanagement.communication.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.patientmanagement.communication.model.CommunicationChannel;
import com.patientmanagement.communication.model.CommunicationType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NoOpSmsCommunicationProviderTests {

    @Test
    void noOpProviderReportsSimulationNotDelivery() {
        NoOpSmsCommunicationProvider provider = new NoOpSmsCommunicationProvider();

        CommunicationProviderResult result = provider.send(new CommunicationDispatchRequest(
                UUID.randomUUID(),
                CommunicationType.APPOINTMENT_CONFIRMATION,
                CommunicationChannel.SMS,
                "+15555550100",
                "Your appointment has been scheduled.",
                "PMCLINIC",
                null
        ));

        assertThat(provider.channel()).isEqualTo(CommunicationChannel.SMS);
        assertThat(result.successful()).isFalse();
        assertThat(result.simulated()).isTrue();
        assertThat(result.providerMessageId()).isNull();
        assertThat(result.failureReason()).isNull();
    }
}
