package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.ehr.dataauthority.client.EHRDataAuthorityClient
import com.projectronin.ehr.dataauthority.models.ChangeType
import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.datalake.DatalakePublishService
import com.projectronin.interop.ehr.DocumentReferenceService
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.generators.datatypes.attachment
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.binary
import com.projectronin.interop.fhir.generators.resources.documentReference
import com.projectronin.interop.fhir.generators.resources.documentReferenceContent
import com.projectronin.interop.fhir.r4.datatype.DynamicValue
import com.projectronin.interop.fhir.r4.datatype.DynamicValueType
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Url
import com.projectronin.interop.fhir.r4.resource.DocumentReference
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.valueset.DocumentReferenceStatus
import com.projectronin.interop.fhir.util.asCode
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DocumentReferencePublishTest {
    private val tenantId = "tenant"
    private val tenant = mockk<Tenant> {
        every { mnemonic } returns tenantId
    }
    private val documentReferenceService = mockk<DocumentReferenceService>()
    private val vendorFactory = mockk<VendorFactory> {
        every { documentReferenceService } returns this@DocumentReferencePublishTest.documentReferenceService
    }
    private val ehrdaClient = mockk<EHRDataAuthorityClient> {
        coEvery { getResourcesChangeStatus(any(), any()) } returns mockk {
            every { failed } returns emptyList()
            every { succeeded } returns listOf(
                mockk {
                    every { changeType } returns ChangeType.CHANGED
                }
            )
        }
    }
    private val datalakePublishService = mockk<DatalakePublishService> {
        every { getDatalakeFullURL(any()) } returns "datalake/path"
        every { getBinaryFilepath(any(), any()) } returns "ehr/Binary/tenant-12345"
        every { publishBinaryData(any(), any()) } just Runs
    }
    private val documentReferencePublish =
        DocumentReferencePublish(
            mockk(),
            mockk(),
            mockk(),
            mockk(),
            mockk(),
            mockk(),
            ehrdaClient,
            datalakePublishService
        )

    private val patient1 = Patient(
        id = Id("$tenantId-1234")
    )
    private val patient2 = Patient(
        id = Id("$tenantId-5678")
    )

    private val documentReference1 = DocumentReference(
        id = Id("$tenantId-13579"),
        status = DocumentReferenceStatus.CURRENT.asCode()
    )
    private val documentReference2 = DocumentReference(
        id = Id("$tenantId-24680"),
        status = DocumentReferenceStatus.CURRENT.asCode()
    )

    private val metadata = mockk<Metadata>(relaxed = true) {
        every { runId } returns "run"
    }

    @Test
    fun `publish events create a PatientPublishDocumentReferenceRequest for patient publish events`() {
        val publishEvent = mockk<InteropResourcePublishV1>(relaxed = true) {
            every { resourceType } returns ResourceType.Patient
            every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(patient1)
            every { metadata } returns this@DocumentReferencePublishTest.metadata
        }
        val request =
            documentReferencePublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(DocumentReferencePublish.PatientPublishDocumentReferenceRequest::class.java, request)
    }

    @Test
    fun `publish events create a EncounterPublishLocationRequest for encounter publish events`() {
        val publishEvent = mockk<InteropResourcePublishV1>(relaxed = true) {
            every { resourceType } returns ResourceType.DocumentReference
            every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(documentReference1)
            every { metadata } returns this@DocumentReferencePublishTest.metadata
        }
        val request =
            documentReferencePublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(DocumentReferencePublish.DocumentReferencePublishDocumentReferenceRequest::class.java, request)
    }

    @Test
    fun `publish events throw exception for unsupported publish events`() {
        val publishEvent = mockk<InteropResourcePublishV1>(relaxed = true) {
            every { resourceType } returns ResourceType.Practitioner
        }
        val exception = assertThrows<IllegalStateException> {
            documentReferencePublish.convertPublishEventsToRequest(
                listOf(publishEvent),
                vendorFactory,
                tenant
            )
        }
        assertEquals(
            "Received resource type (Practitioner) that cannot be used to load document references",
            exception.message
        )
    }

    @Test
    fun `load events create a LoadLocationRequest`() {
        val loadEvent = mockk<InteropResourceLoadV1>(relaxed = true)
        val request = documentReferencePublish.convertLoadEventsToRequest(listOf(loadEvent), vendorFactory, tenant)
        assertInstanceOf(DocumentReferencePublish.LoadDocumentReferenceRequest::class.java, request)
    }

    @Test
    fun `PatientPublishDocumentReferenceRequest skips all publishing`() {
        val publishEvent = mockk<InteropResourcePublishV1>(relaxed = true)
        val request = DocumentReferencePublish.PatientPublishDocumentReferenceRequest(
            listOf(publishEvent),
            documentReferenceService,
            tenant,
            mockk()
        )
        assertTrue(request.skipAllPublishing)
    }

    @Test
    fun `PatientPublishDocumentReferenceRequest handles no documents found`() {
        every { documentReferenceService.findPatientDocuments(tenant, "1234", any(), any()) } returns emptyList()
        every { documentReferenceService.findPatientDocuments(tenant, "5678", any(), any()) } returns emptyList()

        val publishEvent1 = mockk<InteropResourcePublishV1>(relaxed = true) {
            every { resourceType } returns ResourceType.Patient
            every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(patient1)
            every { dataTrigger } returns InteropResourcePublishV1.DataTrigger.nightly
            every { metadata } returns this@DocumentReferencePublishTest.metadata
        }
        val publishEvent2 = mockk<InteropResourcePublishV1>(relaxed = true) {
            every { resourceType } returns ResourceType.Patient
            every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(patient2)
            every { dataTrigger } returns InteropResourcePublishV1.DataTrigger.nightly
            every { metadata } returns this@DocumentReferencePublishTest.metadata
        }
        val request = DocumentReferencePublish.PatientPublishDocumentReferenceRequest(
            listOf(publishEvent1, publishEvent2),
            documentReferenceService,
            tenant,
            mockk()
        )

        val key1 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-1234")
        val key2 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-5678")

        val resourcesByKey = request.loadResources(listOf(key1, key2))
        assertEquals(0, resourcesByKey.size)
    }

    @Test
    fun `PatientPublishDocumentReferenceRequest handles documents found`() {
        val docRef1 = mockk<DocumentReference>()
        val docRef2 = mockk<DocumentReference>()
        val docRef3 = mockk<DocumentReference>()

        every {
            documentReferenceService.findPatientDocuments(
                tenant,
                "1234",
                any(),
                any()
            )
        } returns listOf(docRef1, docRef2)
        every {
            documentReferenceService.findPatientDocuments(
                tenant,
                "5678",
                any(),
                any()
            )
        } returns listOf(docRef3)

        val kafkaService = mockk<KafkaPublishService> {
            every { publishResources(tenantId, DataTrigger.NIGHTLY, any(), any()) } returns mockk()
        }

        val publishEvent1 = mockk<InteropResourcePublishV1>(relaxed = true) {
            every { resourceType } returns ResourceType.Patient
            every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(patient1)
            every { dataTrigger } returns InteropResourcePublishV1.DataTrigger.nightly
            every { metadata } returns this@DocumentReferencePublishTest.metadata
        }
        val publishEvent2 = mockk<InteropResourcePublishV1>(relaxed = true) {
            every { resourceType } returns ResourceType.Patient
            every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(patient2)
            every { dataTrigger } returns InteropResourcePublishV1.DataTrigger.nightly
            every { metadata } returns this@DocumentReferencePublishTest.metadata
        }
        val request = DocumentReferencePublish.PatientPublishDocumentReferenceRequest(
            listOf(publishEvent1, publishEvent2),
            documentReferenceService,
            tenant,
            kafkaService
        )

        val key1 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-1234")
        val key2 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-5678")

        val resourcesByKey = request.loadResources(listOf(key1, key2))
        assertEquals(2, resourcesByKey.size)
        assertEquals(listOf(docRef1, docRef2), resourcesByKey[key1])
        assertEquals(listOf(docRef3), resourcesByKey[key2])

        verify(exactly = 1) {
            kafkaService.publishResources(
                tenantId,
                DataTrigger.NIGHTLY,
                listOf(docRef1, docRef2),
                any()
            )
        }
        verify(exactly = 1) { kafkaService.publishResources(tenantId, DataTrigger.NIGHTLY, listOf(docRef3), any()) }
    }

    @Test
    fun `DocumentReferencePublishDocumentReferenceRequest skips kafka publishing`() {
        val publishEvent = mockk<InteropResourcePublishV1>(relaxed = true)
        val request = DocumentReferencePublish.DocumentReferencePublishDocumentReferenceRequest(
            listOf(publishEvent),
            documentReferenceService,
            tenant
        )
        assertTrue(request.skipKafkaPublishing)
    }

    @Test
    fun `DocumentReferencePublishDocumentReferenceRequest loads resources`() {
        val publishEvent1 = mockk<InteropResourcePublishV1>(relaxed = true) {
            every { resourceType } returns ResourceType.DocumentReference
            every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(documentReference1)
            every { dataTrigger } returns InteropResourcePublishV1.DataTrigger.nightly
            every { metadata } returns this@DocumentReferencePublishTest.metadata
        }
        val publishEvent2 = mockk<InteropResourcePublishV1>(relaxed = true) {
            every { resourceType } returns ResourceType.DocumentReference
            every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(documentReference2)
            every { dataTrigger } returns InteropResourcePublishV1.DataTrigger.nightly
            every { metadata } returns this@DocumentReferencePublishTest.metadata
        }
        val request = DocumentReferencePublish.DocumentReferencePublishDocumentReferenceRequest(
            listOf(publishEvent1, publishEvent2),
            documentReferenceService,
            tenant
        )

        val key1 = ResourceRequestKey("run", ResourceType.DocumentReference, tenant, "$tenantId-13579")
        val key2 = ResourceRequestKey("run", ResourceType.DocumentReference, tenant, "$tenantId-24680")

        val resourcesByKey = request.loadResources(listOf(key1, key2))
        assertEquals(2, resourcesByKey.size)
        assertEquals(listOf(documentReference1), resourcesByKey[key1])
        assertEquals(listOf(documentReference2), resourcesByKey[key2])
    }

    @Test
    fun `post transform works`() {
        val fakeBinaryID = "12345"
        val fakeBinary = binary {
            id of Id("12345")
        }
        val vendorFactory = mockk<VendorFactory> {
            every { binaryService } returns mockk {
                every { getByID(any(), fakeBinaryID) } returns fakeBinary
            }
        }
        val docReference = documentReference {
            content of listOf(
                documentReferenceContent {
                    attachment of attachment {
                        url of "Binary/$fakeBinaryID"
                    }
                }
            )
        }

        val key1 = mockk<ResourceRequestKey>()
        val result = documentReferencePublish.postTransform(tenant, mapOf(key1 to listOf(docReference)), vendorFactory)

        val attachment1 = result[key1]!!.first().content.first().attachment!!
        assertEquals("Binary/tenant-12345", attachment1.url!!.value)
        assertEquals(
            DynamicValue(DynamicValueType.URL, docReference.content.first().attachment!!.url),
            attachment1.extension.find { it.url?.value == "http://projectronin.io/fhir/StructureDefinition/Extension/originalAttachmentURL" }?.value
        )
        assertEquals(
            DynamicValue(DynamicValueType.URL, Url("datalake/path")),
            attachment1.extension.find { it.url?.value == "http://projectronin.io/fhir/StructureDefinition/Extension/datalakeAttachmentURL" }?.value
        )
    }
}