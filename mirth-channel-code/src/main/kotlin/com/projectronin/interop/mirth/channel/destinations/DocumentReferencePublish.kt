package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.ehr.dataauthority.client.EHRDataAuthorityClient
import com.projectronin.ehr.dataauthority.models.ChangeType
import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.collection.mapValuesNotNull
import com.projectronin.interop.datalake.DatalakePublishService
import com.projectronin.interop.ehr.DocumentReferenceService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.datatype.DynamicValue
import com.projectronin.interop.fhir.r4.datatype.DynamicValueType
import com.projectronin.interop.fhir.r4.datatype.Extension
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Url
import com.projectronin.interop.fhir.r4.resource.DocumentReference
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.util.localizeFhirId
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.kafka.model.PublishResourceWrapper
import com.projectronin.interop.mirth.channel.base.kafka.KafkaEventResourcePublisher
import com.projectronin.interop.mirth.channel.base.kafka.event.IdBasedPublishResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.event.ResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.request.LoadResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.PublishResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.rcdm.common.enums.RoninExtension
import com.projectronin.interop.rcdm.transform.TransformManager
import com.projectronin.interop.rcdm.transform.model.TransformResponse
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.OffsetDateTime

@Component
class DocumentReferencePublish(
    ehrFactory: EHRFactory,
    publishService: PublishService,
    tenantService: TenantService,
    transformManager: TransformManager,
    private val kafkaService: KafkaPublishService,
    private val ehrDataAuthorityClient: EHRDataAuthorityClient,
    private val datalakeService: DatalakePublishService,
    @Value("\${ehrda.url}")
    ehrDataAuthorityBaseUrl: String,
) : KafkaEventResourcePublisher<DocumentReference>(
        tenantService,
        ehrFactory,
        transformManager,
        publishService,
    ) {
    private val ehrdaBinaryUrlFormat = "${ehrDataAuthorityBaseUrl.removeSuffix("/")}/tenants/%s/resources/Binary/%s"

    override fun convertPublishEventsToRequest(
        events: List<InteropResourcePublishV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant,
    ): PublishResourceRequest<DocumentReference> {
        // Only events for the same resource type are grouped, so just peek at the first one
        return when (val resourceType = events.first().resourceType) {
            ResourceType.Patient ->
                PatientPublishDocumentReferenceRequest(
                    events,
                    vendorFactory.documentReferenceService,
                    tenant,
                    kafkaService,
                )

            ResourceType.DocumentReference ->
                DocumentReferencePublishDocumentReferenceRequest(
                    events,
                    vendorFactory.documentReferenceService,
                    tenant,
                )

            else -> throw IllegalStateException("Received resource type ($resourceType) that cannot be used to load document references")
        }
    }

    override fun convertLoadEventsToRequest(
        events: List<InteropResourceLoadV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant,
    ): LoadResourceRequest<DocumentReference> {
        return LoadDocumentReferenceRequest(events, vendorFactory.documentReferenceService, tenant)
    }

    internal class PatientPublishDocumentReferenceRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: DocumentReferenceService,
        override val tenant: Tenant,
        private val kafkaService: KafkaPublishService,
    ) : PublishResourceRequest<DocumentReference>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { PatientPublishEvent(it, tenant) }

        override val skipAllPublishing = true // prevents anything happening after loadResources is called

        private var documentsLoaded: Int = 0
        override val requestSpecificMirthMetadata: Map<String, String>
            get() = mapOf(MirthKey.RESOURCE_COUNT.code to documentsLoaded.toString())

        override fun loadResourcesForIds(
            requestFhirIds: List<String>,
            startDate: OffsetDateTime?,
            endDate: OffsetDateTime?,
        ): Map<String, List<DocumentReference>> {
            val response =
                requestFhirIds.mapNotNull { fhirId ->
                    val documents =
                        fhirService.findPatientDocuments(
                            tenant,
                            fhirId,
                            startDate?.toLocalDate() ?: LocalDate.now().minusMonths(2),
                            endDate?.toLocalDate() ?: LocalDate.now(),
                        )

                    if (documents.isEmpty()) {
                        null
                    } else {
                        val event =
                            eventsByRequestKey.entries.single {
                                val key = it.key
                                if (key.unlocalizedResourceId == fhirId) {
                                    if (startDate == null) {
                                        true
                                    } else {
                                        key.dateRange == Pair(startDate, endDate)
                                    }
                                } else {
                                    false
                                }
                            }.value

                        val documentsWithLocalizedIds =
                            documents.map {
                                it.copy(id = Id(it.id!!.value!!.localizeFhirId(tenant.mnemonic)))
                            }

                        // push each DocumentReference individually so the destination can multi-thread Binary reads
                        kafkaService.publishResourceWrappers(
                            tenant.mnemonic,
                            dataTrigger,
                            documentsWithLocalizedIds.map { PublishResourceWrapper(it) },
                            event.getUpdatedMetadata(),
                        )
                        fhirId to documents
                    }
                }.toMap()
            documentsLoaded += response.values.flatten().size
            return response
        }

        private class PatientPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            IdBasedPublishResourceEvent<Patient>(publishEvent, tenant, Patient::class)
    }

    internal class DocumentReferencePublishDocumentReferenceRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: DocumentReferenceService,
        override val tenant: Tenant,
    ) : PublishResourceRequest<DocumentReference>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { DocumentReferencePublishEvent(it, tenant) }

        override val skipKafkaPublishing = true // prevent infinite loop of kafka events

        override fun loadResourcesForIds(
            requestFhirIds: List<String>,
            startDate: OffsetDateTime?,
            endDate: OffsetDateTime?,
        ): Map<String, List<DocumentReference>> {
            return eventsByRequestKey.map { (key, event) ->
                key.unlocalizedResourceId to listOf((event as DocumentReferencePublishEvent).sourceResource)
            }.toMap()
        }

        private class DocumentReferencePublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            IdBasedPublishResourceEvent<DocumentReference>(publishEvent, tenant, DocumentReference::class)
    }

    internal class LoadDocumentReferenceRequest(
        loadEvents: List<InteropResourceLoadV1>,
        override val fhirService: DocumentReferenceService,
        tenant: Tenant,
    ) : LoadResourceRequest<DocumentReference>(loadEvents, tenant)

    /**
     * Creates the EHRDA Binary URL from the unlocalized [binaryFhirId]
     */
    private fun ehrdaBinaryUrl(
        binaryFhirId: String,
        tenant: Tenant,
    ): String = ehrdaBinaryUrlFormat.format(tenant.mnemonic, binaryFhirId.localizeFhirId(tenant.mnemonic))

    override fun postTransform(
        tenant: Tenant,
        transformedResourcesByKey: Map<ResourceRequestKey, List<TransformResponse<DocumentReference>>>,
        vendorFactory: VendorFactory,
    ): Map<ResourceRequestKey, List<TransformResponse<DocumentReference>>> {
        val binaryService = vendorFactory.binaryService
        val handledDocumentReferenceList =
            transformedResourcesByKey.mapValuesNotNull { (_, transformedResponses) ->
                transformedResponses.mapNotNull { transformedResponse ->
                    val documentReference = transformedResponse.resource
                    val binaryFHIRIDs = mutableListOf<String>()

                    @Suppress("ktlint:standard:max-line-length")
                    val docContentList =
                        documentReference.content.filter { it.attachment?.url?.value?.contains("Binary/") == true }
                            .map { content ->
                                content.copy(
                                    attachment =
                                        content.attachment!!.let { attachment ->
                                            val binaryURL = attachment.url!!
                                            val binaryFHIRID = binaryURL.value!!.split("/").last()
                                            binaryFHIRIDs.add(binaryFHIRID)
                                            attachment.copy(
                                                url =
                                                    Url(
                                                        value = ehrdaBinaryUrl(binaryFHIRID, tenant),
                                                        extension =
                                                            binaryURL.extension +
                                                                listOf(
                                                                    Extension(
                                                                        url = RoninExtension.TENANT_SOURCE_DOCUMENT_REFERENCE_ATTACHMENT_URL.uri,
                                                                        value = DynamicValue(DynamicValueType.URL, binaryURL),
                                                                    ),
                                                                    Extension(
                                                                        url = RoninExtension.DATALAKE_DOCUMENT_REFERENCE_ATTACHMENT_URL.uri,
                                                                        value =
                                                                            DynamicValue(
                                                                                DynamicValueType.URL,
                                                                                Url(
                                                                                    datalakeService.getDatalakeFullURL(
                                                                                        datalakeService.getBinaryFilepath(
                                                                                            tenant.mnemonic,
                                                                                            binaryFHIRID.localizeFhirId(tenant.mnemonic),
                                                                                        ),
                                                                                    ),
                                                                                ),
                                                                            ),
                                                                    ),
                                                                ),
                                                    ),
                                            )
                                        },
                                )
                            }
                    val newDocumentReference = documentReference.copy(content = docContentList)
                    // determines if the DocumentReference has 'changed', which is what we are using to
                    // decide whether to load the Binary objects or not
                    val changed =
                        runBlocking {
                            ehrDataAuthorityClient.getResourcesChangeStatus(
                                tenant.mnemonic,
                                listOf(newDocumentReference),
                            )
                        }
                    if (changed.failed.isNotEmpty() || changed.succeeded.first().changeType == ChangeType.UNCHANGED) {
                        logger.info { "Document reference ${documentReference.id!!.value} is unchanged; skipping Binary load." }
                        return@mapNotNull null
                    }

                    val binaryList =
                        runCatching {
                            binaryFHIRIDs.map {
                                val binary = binaryService.getByID(tenant, it)
                                binary.copy(
                                    id = binary.id!!.copy(value = it.localizeFhirId(tenant.mnemonic)),
                                )
                            }
                        }.getOrNull() ?: return@mapNotNull null

                    datalakeService.publishBinaryData(tenant.mnemonic, binaryList)
                    TransformResponse(newDocumentReference, transformedResponse.embeddedResources)
                }.ifEmpty { null }
            }
        return handledDocumentReferenceList
    }
}
