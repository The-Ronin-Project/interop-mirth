package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.ehr.dataauthority.client.EHRDataAuthorityClient
import com.projectronin.ehr.dataauthority.models.ChangeType
import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.aidbox.utils.findFhirID
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.datalake.DatalakePublishService
import com.projectronin.interop.ehr.DocumentReferenceService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.datatype.DynamicValue
import com.projectronin.interop.fhir.r4.datatype.DynamicValueType
import com.projectronin.interop.fhir.r4.datatype.Extension
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.datatype.primitive.Url
import com.projectronin.interop.fhir.r4.resource.DocumentReference
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninDocumentReference
import com.projectronin.interop.fhir.ronin.util.localize
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.kafka.IdBasedPublishEventResourceLoadRequest
import com.projectronin.interop.mirth.channel.base.kafka.KafkaEventResourcePublisher
import com.projectronin.interop.mirth.channel.base.kafka.LoadEventResourceLoadRequest
import com.projectronin.interop.mirth.channel.base.kafka.ResourceLoadRequest
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class DocumentReferencePublish(
    ehrFactory: EHRFactory,
    publishService: PublishService,
    tenantService: TenantService,
    transformManager: TransformManager,
    profileTransformer: RoninDocumentReference,
    private val kafkaService: KafkaPublishService,
    private val ehrDataAuthorityClient: EHRDataAuthorityClient,
    private val datalakeService: DatalakePublishService
) : KafkaEventResourcePublisher<DocumentReference>(
    tenantService,
    ehrFactory,
    transformManager,
    publishService,
    profileTransformer
) {

    override fun convertEventToRequest(
        serializedEvent: String,
        eventClassName: String,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): ResourceLoadRequest<DocumentReference> {
        return when (eventClassName) {
            InteropResourcePublishV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourcePublishV1::class)
                when (event.resourceType) {
                    ResourceType.Patient ->
                        PatientSourceDocumentReferenceLoadRequest(
                            event,
                            vendorFactory.documentReferenceService,
                            tenant,
                            kafkaService
                        )
                    // circular publish event generated from patient events
                    ResourceType.DocumentReference ->
                        DocumentReferenceSourceDocumentReferenceRequest(
                            event,
                            vendorFactory.documentReferenceService,
                            tenant
                        )
                    else -> throw IllegalStateException("Received resource type that cannot be used to load documentReferences")
                }
            }

            InteropResourceLoadV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourceLoadV1::class)
                DocumentReferenceLoadRequest(
                    event,
                    vendorFactory.documentReferenceService,
                    tenant
                )
            }

            else -> throw IllegalStateException("Received a string which cannot deserialize to a known event")
        }
    }

    private class PatientSourceDocumentReferenceLoadRequest(
        sourceEvent: InteropResourcePublishV1,
        override val fhirService: DocumentReferenceService,
        tenant: Tenant,
        val kafkaService: KafkaPublishService
    ) : IdBasedPublishEventResourceLoadRequest<DocumentReference, Patient>(sourceEvent, tenant) {
        override val sourceResource: Patient = JacksonUtil.readJsonObject(sourceEvent.resourceJson, Patient::class)
        override val skipAllPublishing = true // prevents anything happening after loadResources is called
        override fun loadResources(): List<DocumentReference> {
            val patientFhirId = sourceResource.identifier.findFhirID()
            val documents = fhirService.findPatientDocuments(
                tenant,
                patientFhirId,
                LocalDate.now().minusMonths(2),
                LocalDate.now()
            )
            // push each DocumentReference individually so the destination can multi-thread Binary reads
            kafkaService.publishResources(
                tenant.mnemonic,
                dataTrigger,
                documents,
                metadata.copy(
                    upstreamReferences = listOf(
                        Metadata.UpstreamReference(
                            ResourceType.Patient,
                            id = sourceResource.id!!.value!!
                        )
                    )
                )
            )
            return documents
        }
    }

    private class DocumentReferenceSourceDocumentReferenceRequest(
        sourceEvent: InteropResourcePublishV1,
        override val fhirService: DocumentReferenceService,
        tenant: Tenant
    ) : IdBasedPublishEventResourceLoadRequest<DocumentReference, DocumentReference>(sourceEvent, tenant) {
        override val sourceResource: DocumentReference =
            JacksonUtil.readJsonObject(sourceEvent.resourceJson, DocumentReference::class)
        override val skipKafkaPublishing = true // prevent infinite loop of kafka events
        override fun loadResources(): List<DocumentReference> {
            return listOf(sourceResource)
        }
    }

    private class DocumentReferenceLoadRequest(
        sourceEvent: InteropResourceLoadV1,
        override val fhirService: DocumentReferenceService,
        tenant: Tenant
    ) : LoadEventResourceLoadRequest<DocumentReference>(sourceEvent, tenant)

    // called by base class for Load and Publish events to handle Binary resources.
    override fun postTransform(
        tenant: Tenant,
        transformedList: List<DocumentReference>,
        vendorFactory: VendorFactory
    ): List<DocumentReference> {
        val binaryService = vendorFactory.binaryService
        val handledDocumentReferenceList = transformedList.mapNotNull { documentReference ->
            val binaryFHIRIDs = mutableListOf<String>()
            val docContentList =
                documentReference.content.filter { it.attachment?.url?.value?.contains("Binary/") == true }
                    .map { content ->
                        content.copy(
                            attachment = content.attachment!!.let { attachment ->
                                val binaryURL = attachment.url!!
                                val binaryFHIRID = binaryURL.value!!.split("/").last()
                                binaryFHIRIDs.add(binaryFHIRID)
                                attachment.copy(
                                    url = Url("Binary/${binaryFHIRID.localize(tenant)}"),
                                    extension = attachment.extension + listOf(
                                        Extension(
                                            url = Uri("http://projectronin.io/fhir/StructureDefinition/Extension/originalAttachmentURL"),
                                            value = DynamicValue(DynamicValueType.URL, binaryURL)
                                        ),
                                        Extension(
                                            url = Uri("http://projectronin.io/fhir/StructureDefinition/Extension/datalakeAttachmentURL"),
                                            value = DynamicValue(
                                                DynamicValueType.URL,
                                                Url(
                                                    datalakeService.getDatalakeFullURL(
                                                        datalakeService.getBinaryFilepath(
                                                            tenant.mnemonic,
                                                            binaryFHIRID.localize(tenant)
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            }
                        )
                    }
            val newDocumentReference = documentReference.copy(content = docContentList)
            // determines if the DocumentReference has 'changed', which is what we are using to
            // decide whether to load the Binary objects or not
            val changed = runBlocking {
                ehrDataAuthorityClient.getResourcesChangeStatus(
                    tenant.mnemonic,
                    listOf(newDocumentReference)
                )
            }
            if (changed.failed.isNotEmpty() || changed.succeeded.first().changeType == ChangeType.UNCHANGED) {
                logger.info { "Document reference ${documentReference.id!!.value} is unchanged; skipping Binary load." }
                return@mapNotNull null
            }
            val binaryList = binaryFHIRIDs.map {
                val binary = binaryService.getByID(tenant, it)
                binary.copy(
                    id = binary.id!!.copy(value = it.localize(tenant))
                )
            }
            datalakeService.publishBinaryData(tenant.mnemonic, binaryList)
            newDocumentReference
        }
        return handledDocumentReferenceList
    }
}
