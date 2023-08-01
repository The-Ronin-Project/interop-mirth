package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.ehr.dataauthority.client.EHRDataAuthorityClient
import com.projectronin.ehr.dataauthority.models.ChangeType
import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.datalake.DatalakePublishService
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.generators.datatypes.attachment
import com.projectronin.interop.fhir.generators.resources.binary
import com.projectronin.interop.fhir.generators.resources.documentReference
import com.projectronin.interop.fhir.generators.resources.documentReferenceContent
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Url
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.DocumentReference
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.kafka.ResourceRequestKey
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.OffsetDateTime

class DocumentReferencePublishTest {
    lateinit var tenant: Tenant
    lateinit var destination: DocumentReferencePublish

    @BeforeEach
    fun setup() {
        tenant = mockk {
            every { mnemonic } returns "tenant"
        }
        val kafkaPublishService = mockk<KafkaPublishService> {
            coEvery { publishResources(any(), any(), any(), any()) } returns mockk()
        }

        val ehrdaClient = mockk<EHRDataAuthorityClient> {
            coEvery { getResourcesChangeStatus(any(), any()) } returns mockk {
                every { failed } returns emptyList()
                every { succeeded } returns listOf(
                    mockk {
                        every { changeType } returns ChangeType.CHANGED
                    }
                )
            }
        }
        val datalakePublishService = mockk<DatalakePublishService> {
            every { getDatalakeFullURL(any()) } returns "datalake/path"
            every { getBinaryFilepath(any(), any()) } returns "ehr/Binary/tenant-12345"
            every { publishBinaryData(any(), any()) } just Runs
        }
        destination = DocumentReferencePublish(
            mockk(),
            mockk(),
            mockk(),
            mockk(),
            mockk(),
            kafkaService = kafkaPublishService,
            ehrDataAuthorityClient = ehrdaClient,
            datalakeService = datalakePublishService
        )
        mockkObject(JacksonUtil)
    }

    @AfterEach
    fun unmockk() {
        unmockkAll()
    }

    @Test
    fun `channel creation works`() {
        assertNotNull(destination)
    }

    @Test
    fun `fails on unknown request`() {
        assertThrows<IllegalStateException> {
            destination.convertEventToRequest("boo", "", mockk(), mockk())
        }
    }

    @Test
    fun `works for load events`() {
        val metadata = Metadata("run123", runDateTime = OffsetDateTime.now())
        val event = InteropResourceLoadV1(
            "tenant",
            "id",
            ResourceType.DocumentReference,
            InteropResourceLoadV1.DataTrigger.adhoc,
            metadata
        )
        val mockDocumentReference = mockk<DocumentReference>()
        every { JacksonUtil.readJsonObject("boo", InteropResourceLoadV1::class) } returns event
        val mockVendorFactory = mockk<VendorFactory> {
            every { documentReferenceService.getByID(tenant, "id") } returns mockDocumentReference
        }
        val request = destination.convertEventToRequest(
            "boo",
            InteropResourceLoadV1::class.simpleName!!,
            mockVendorFactory,
            tenant
        )

        val requestKeys = listOf(ResourceRequestKey("run123", ResourceType.DocumentReference, tenant, "id"))
        assertEquals(requestKeys, request.requestKeys)

        val results = request.loadResources(requestKeys)
        assertEquals(mockDocumentReference, results.first())
    }

    @Test
    fun `works for patient publish events`() {
        val metadata = Metadata("run123", runDateTime = OffsetDateTime.now())
        val event = InteropResourcePublishV1(
            "tenant",
            ResourceType.Patient,
            InteropResourcePublishV1.DataTrigger.adhoc,
            "{}",
            metadata
        )
        val mockPatient = mockk<Patient> {
            every { id?.value } returns "tenant-123"
            every { identifier } returns listOf(
                mockk {
                    every { system } returns CodeSystem.RONIN_FHIR_ID.uri
                    every { value } returns "123".asFHIR()
                }
            )
        }
        val mockDocumentReference = mockk<DocumentReference> {}
        every { JacksonUtil.readJsonObject("boo", InteropResourcePublishV1::class) } returns event
        every { JacksonUtil.readJsonObject("{}", Patient::class) } returns mockPatient
        val mockVendorFactory = mockk<VendorFactory> {
            every {
                documentReferenceService.findPatientDocuments(
                    tenant,
                    "123",
                    match { it.isAfter(LocalDate.now().minusYears(1).minusDays(1)) },
                    match { it.isBefore(LocalDate.now().plusYears(1).plusDays(1)) }
                )
            } returns listOf(mockDocumentReference)
        }
        val request = destination.convertEventToRequest(
            "boo",
            InteropResourcePublishV1::class.simpleName!!,
            mockVendorFactory,
            tenant
        )

        val requestKeys = listOf(ResourceRequestKey("run123", ResourceType.Patient, tenant, "tenant-123"))
        assertEquals(requestKeys.first().toString(), request.requestKeys.first().toString())

        val results = request.loadResources(requestKeys)
        assertEquals(listOf(mockDocumentReference), results)
    }

    @Test
    fun `document reference source publish events`() {
        val metadata = Metadata("run123", runDateTime = OffsetDateTime.now())
        val event = InteropResourcePublishV1(
            "tenant",
            ResourceType.DocumentReference,
            InteropResourcePublishV1.DataTrigger.adhoc,
            "{}",
            metadata
        )

        val mockDocumentReference = mockk<DocumentReference> { every { id!!.value } returns "123" }
        every { JacksonUtil.readJsonObject("boo", InteropResourcePublishV1::class) } returns event
        every { JacksonUtil.readJsonObject("{}", DocumentReference::class) } returns mockDocumentReference
        val request = destination.convertEventToRequest(
            "boo",
            InteropResourcePublishV1::class.simpleName!!,
            mockk {
                every { documentReferenceService } returns mockk()
            },
            tenant
        )

        val requestKeys = listOf(ResourceRequestKey("run123", ResourceType.DocumentReference, tenant, "tenant-123"))
        assertEquals(requestKeys.first().toString(), request.requestKeys.first().toString())

        val results = request.loadResources(requestKeys)
        assertEquals(mockDocumentReference, results.first())
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
                        url of Url("Binary/$fakeBinaryID")
                    }
                }
            )
        }
        val result = destination.postTransform(tenant, listOf(docReference), vendorFactory)
        assertEquals(result.first().content.first().attachment!!.url!!.value, "Binary/tenant-12345")
    }
}
