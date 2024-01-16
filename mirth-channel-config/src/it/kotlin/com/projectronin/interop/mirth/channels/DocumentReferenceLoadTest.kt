package com.projectronin.interop.mirth.channels

import com.projectronin.interop.fhir.generators.datatypes.attachment
import com.projectronin.interop.fhir.generators.datatypes.codeableConcept
import com.projectronin.interop.fhir.generators.datatypes.coding
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.date
import com.projectronin.interop.fhir.generators.primitives.daysAgo
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.binary
import com.projectronin.interop.fhir.generators.resources.documentReference
import com.projectronin.interop.fhir.generators.resources.documentReferenceContent
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Url
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channels.client.KafkaClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.DOC_REF_LOAD_CHANNEL_NAME
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DocumentReferenceLoadTest : BaseChannelTest(
    DOC_REF_LOAD_CHANNEL_NAME,
    listOf("DocumentReference"),
    listOf("DocumentReference"),
) {
    @Test
    fun `channel works`() {
        tenantInUse = TEST_TENANT

        val fakeBinary = binary { }
        val fakeBinaryID = MockEHRTestData.add(fakeBinary)
        val patient1 =
            patient {
                birthDate of
                    date {
                        year of 1990
                        month of 1
                        day of 3
                    }
            }
        val patient1Id = MockEHRTestData.add(patient1)
        val roninPatient =
            patient1.copy(
                id = Id("$tenantInUse-$patient1Id"),
                identifier = patient1.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(patient1Id),
            )
        val fakeDocumentReference =
            documentReference {
                date of 2.daysAgo()
                type of
                    codeableConcept {
                        coding of
                            listOf(
                                coding {
                                    system of "http://loinc.org"
                                    display of "note"
                                    code of "34806-0"
                                },
                            )
                    }
                subject of reference("Patient", patient1Id)
                category of
                    listOf(
                        codeableConcept {
                            coding of
                                listOf(
                                    coding {
                                        system of "http://hl7.org/fhir/us/core/CodeSystem/us-core-documentreference-category"
                                        code of "clinical-note"
                                    },
                                )
                        },
                    )
                content of
                    listOf(
                        documentReferenceContent {
                            attachment of
                                attachment {
                                    url of Url("Binary/$fakeBinaryID")
                                }
                        },
                    )
            }
        MockOCIServerClient.createExpectations(
            "DocumentReference",
            MockEHRTestData.add(fakeDocumentReference),
            tenantInUse,
        )

        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(roninPatient),
        )

        waitForMessage(2)

        assertEquals(1, getAidboxResourceCount("DocumentReference"))

        // now we test if the change detection stuff works
        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(roninPatient),
        )
        waitForMessage(4)
        val messageList2 = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList2)
        assertEquals(4, messageList2.size)
        // should have 4 messages still only 1 doc reference in aidbox
        assertEquals(1, getAidboxResourceCount("DocumentReference"))
    }
}
