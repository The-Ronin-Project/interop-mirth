package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.fhir.r4.resource.DomainResource
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.r4.resource.PractitionerRole
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninLocation
import com.projectronin.interop.fhir.ronin.resource.RoninPractitioner
import com.projectronin.interop.fhir.ronin.resource.RoninPractitionerRole
import com.projectronin.interop.mirth.channel.base.ChannelService
import com.projectronin.interop.mirth.channel.destinations.PractitionerNightlyLoadWriter
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.channel.util.generateMetadata
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.exception.ConfigurationMissingException
import com.projectronin.interop.tenant.config.exception.ResourcesNotFoundException
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException
import org.springframework.stereotype.Component

private const val PUBLISH_SERVICE = "publish"

@Component
class PractitionerNightlyLoad(
    tenantService: TenantService,
    transformManager: TransformManager,
    practitionerNightlyLoadWriter: PractitionerNightlyLoadWriter,
    private val tenantConfigurationService: TenantConfigurationService,
    private val ehrFactory: EHRFactory,
    private val roninPractitioner: RoninPractitioner,
    private val roninPractitionerRole: RoninPractitionerRole,
    private val roninLocation: RoninLocation
) : ChannelService(tenantService, transformManager) {
    companion object : ChannelFactory<PractitionerNightlyLoad>()

    override val rootName = "PractitionerNightlyLoad"
    override val destinations = mapOf(PUBLISH_SERVICE to practitionerNightlyLoadWriter)

    override fun channelSourceReader(tenantMnemonic: String, serviceMap: Map<String, Any>): List<MirthMessage> {
        // a missing tenant configuration throws an error; also check the configuration for an empty locationIDs list
        val locationIdsList = tenantConfigurationService.getLocationIDsByTenant(tenantMnemonic)
        if (locationIdsList.isEmpty()) {
            throw ConfigurationMissingException("No Location IDs configured for tenant $tenantMnemonic")
        }

        val tenant = getTenant(tenantMnemonic)
        val vendorFactory = ehrFactory.getVendorFactory(tenant)

        val response = vendorFactory.practitionerService.findPractitionersByLocation(tenant, locationIdsList)

        if (response.resources.isEmpty()) {
            throw ResourcesNotFoundException(
                "No Locations, Practitioners or PractitionerRoles found for tenant $tenantMnemonic"
            )
        }

        val responseLists = listOf(
            response.practitioners,
            response.locations,
            response.practitionerRoles
        )

        val metadata = generateMetadata()

        val messageList = responseLists.flatMap { resourceList ->
            resourceList.chunked(confirmMaxChunkSize(serviceMap)).map {
                val resourceType = it.first().resourceType
                MirthMessage(
                    message = JacksonUtil.writeJsonValue(it),
                    dataMap = mapOf(
                        "${MirthKey.RESOURCES_FOUND.code}.$resourceType" to it,
                        MirthKey.RESOURCE_COUNT.code to it.count(),
                        MirthKey.EVENT_METADATA.code to metadata
                    )
                )
            }
        }
        return messageList
    }

    override fun channelSourceTransformer(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthMessage {
        var resourcesTransformed: List<Resource<out DomainResource<*>>> = listOf()

        val foundCount: Int = sourceMap.keys.count { it.startsWith("${MirthKey.RESOURCES_FOUND.code}.") }
        if (foundCount == 0) {
            throw ResourcesNotFoundException(
                "No Locations, Practitioners or PractitionerRoles found for tenant $tenantMnemonic"
            )
        }
        sourceMap.keys.forEach { key ->
            when (key.removePrefix("${MirthKey.RESOURCES_FOUND.code}.")) {
                "Practitioner" -> {
                    resourcesTransformed =
                        deserializeAndTransformToList(tenantMnemonic, msg, Practitioner::class, roninPractitioner)
                }

                "Location" -> {
                    resourcesTransformed =
                        deserializeAndTransformToList(
                            tenantMnemonic,
                            msg,
                            Location::class,
                            roninLocation
                        )
                }

                "PractitionerRole" -> {
                    resourcesTransformed =
                        deserializeAndTransformToList(
                            tenantMnemonic,
                            msg,
                            PractitionerRole::class,
                            roninPractitionerRole
                        )
                }
            }
        }

        if (resourcesTransformed.isEmpty()) {
            throw ResourcesNotTransformedException(
                "Failed to transform resources for tenant $tenantMnemonic"
            )
        }

        val resourceFHIRIds = resourcesTransformed.mapNotNull { resource -> resource.id?.value }
        return MirthMessage(
            message = JacksonUtil.writeJsonValue(resourcesTransformed),
            dataMap = mapOf(
                MirthKey.RESOURCES_TRANSFORMED.code to resourcesTransformed,
                MirthKey.FHIR_ID_LIST.code to resourceFHIRIds.joinToString(",")
            )
        )
    }
}
