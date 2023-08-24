package com.projectronin.interop.mirth.channels

import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.primitives.date
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.kafka.PatientOnboardingStatus
import com.projectronin.interop.kafka.model.ExternalTopic
import com.projectronin.interop.kafka.model.KafkaAction
import com.projectronin.interop.kafka.model.KafkaEvent
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.KafkaClient
import com.projectronin.interop.mirth.channels.client.MockEHRClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.TenantClient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

const val onboardFlagChannelName = "OnboardFlag"

class OnboardFlagTest : BaseChannelTest(
    onboardFlagChannelName,
    listOf(),
    listOf("Flag")
) {

    private val onboardTopic = ExternalTopic(
        systemName = "chokuto",
        topicName = "oci.us-phoenix-1.chokuto.patient-onboarding-status-publish.v1",
        dataSchema = "https://github.com/projectronin/contract-event-prodeng-patient-onboarding-status/blob/main/v1/patient-onboarding-status.schema.json"
    )

    @Test
    fun `onboard flag service - happy path`() {
        tenantInUse = testTenant
        val patient1 = patient {
            birthDate of date {
                year of 1990
                month of 1
                day of 3
            }
            identifier of listOf(
                identifier {
                    system of "mockPatientInternalSystem"
                },
                identifier {
                    system of "mockEHRMRNSystem"
                    value of "1000000001"
                }
            )
            name of listOf(
                name {
                    use of "usual" // This is required to generate the Epic response.
                },
                name {
                    use of "official"
                }
            )
            gender of "male"
            telecom of emptyList()
        }
        val patient1Id = MockEHRTestData.add(patient1)
        MockOCIServerClient.createExpectations("patient", patient1Id, testTenant)
        val fakeAidboxPatientId = "$tenantInUse-$patient1Id"
        val fakeAidboxPatient = patient1.copy(
            id = Id(fakeAidboxPatientId),
            identifier = patient1.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(patient1Id)
        )
        AidboxTestData.add(fakeAidboxPatient)

        val event = KafkaEvent(
            "patient",
            "onboarding",
            KafkaAction.CREATE,
            "",
            PatientOnboardingStatus(
                patient1Id,
                testTenant,
                PatientOnboardingStatus.OnboardAction.ONBOARD,
                LocalDateTime.now().toString()
            )
        )

        KafkaClient.client.publishEvents(onboardTopic, listOf(event))

        waitForMessage(1)
        val flagBundle = MockEHRClient.getAllResources("Flag")
        assertEquals(1, flagBundle.entry.filter { it.resource?.resourceType == "Flag" }.size)
    }

    @Test
    fun `onboard flag service - filter works`() {
        tenantInUse = testTenant
        val oldConfig = TenantClient.getMirthConfig(tenantInUse)
        TenantClient.putMirthConfig(
            tenantInUse,
            TenantClient.MirthConfig(locationIds = listOf("12345"), blockedResources = listOf("PatientOnboardFlag"))
        )
        val patient1 = patient {
            birthDate of date {
                year of 1990
                month of 1
                day of 3
            }
            identifier of listOf(
                identifier {
                    system of "mockPatientInternalSystem"
                },
                identifier {
                    system of "mockEHRMRNSystem"
                    value of "1000000001"
                }
            )
            name of listOf(
                name {
                    use of "usual" // This is required to generate the Epic response.
                },
                name {
                    use of "official"
                }
            )
            gender of "male"
            telecom of emptyList()
        }
        val patient1Id = MockEHRTestData.add(patient1)
        MockOCIServerClient.createExpectations("patient", patient1Id, testTenant)
        val fakeAidboxPatientId = "$tenantInUse-$patient1Id"
        val fakeAidboxPatient = patient1.copy(
            id = Id(fakeAidboxPatientId),
            identifier = patient1.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(patient1Id)
        )
        AidboxTestData.add(fakeAidboxPatient)

        val event = KafkaEvent(
            "patient",
            "onboarding",
            KafkaAction.CREATE,
            "",
            PatientOnboardingStatus(
                patient1Id,
                testTenant,
                PatientOnboardingStatus.OnboardAction.ONBOARD,
                LocalDateTime.now().toString()
            )
        )

        KafkaClient.client.publishEvents(onboardTopic, listOf(event))

        waitForMessage(1)
        val message = MirthClient.getChannelMessageIds(testChannelId).first()
            .let { MirthClient.getMessageById(testChannelId, it) }
        assertEquals("FILTERED", message.destinationMessages.first().status)
        TenantClient.putMirthConfig(tenantInUse, oldConfig)
    }
}
