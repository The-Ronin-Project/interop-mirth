package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.r4.resource.DomainResource
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.r4.resource.PractitionerRole
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.fhir.ronin.resource.RoninLocation
import com.projectronin.interop.fhir.ronin.resource.RoninPractitioner
import com.projectronin.interop.fhir.ronin.resource.RoninPractitionerRole
import com.projectronin.interop.mirth.channel.base.ChannelService
import com.projectronin.interop.mirth.channel.destinations.PractitionerNightlyLoadWriter
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.mirth.connector.ServiceFactoryImpl
import com.projectronin.interop.tenant.config.exception.ConfigurationMissingException
import com.projectronin.interop.tenant.config.exception.ResourcesNotFoundException
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException

private const val PUBLISH_SERVICE = "publish"

class PractitionerNightlyLoad(serviceFactory: ServiceFactory = ServiceFactoryImpl) : ChannelService(serviceFactory) {
    companion object : ChannelFactory<PractitionerNightlyLoad>()

    override val rootName = "PractitionerLoad"
    override val destinations = mapOf(PUBLISH_SERVICE to PractitionerNightlyLoadWriter(rootName, serviceFactory))

    override fun channelSourceReader(tenantMnemonic: String, serviceMap: Map<String, Any>): List<MirthMessage> {
        // a missing tenant configuration throws an error; also check the configuration for an empty locationIDs list
        val locationIdsList = serviceFactory.tenantConfigurationFactory().getLocationIDsByTenant(tenantMnemonic)
        if (locationIdsList.isEmpty()) {
            throw ConfigurationMissingException("No Location IDs configured for tenant $tenantMnemonic")
        }

        val tenant = serviceFactory.getTenant(tenantMnemonic)
        val vendorFactory = serviceFactory.vendorFactory(tenant)

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

        val messageList = responseLists.flatMap { resourceList ->
            resourceList.chunked(confirmMaxChunkSize(serviceMap)).map {
                val resourceType = it.first().resourceType
                MirthMessage(
                    message = JacksonUtil.writeJsonValue(it),
                    dataMap = mapOf(
                        "${MirthKey.RESOURCES_FOUND.code}.$resourceType" to it,
                        MirthKey.RESOURCE_COUNT.code to it.count()
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
                        deserializeAndTransformToList(tenantMnemonic, msg, Practitioner::class, RoninPractitioner)
                }
                "Location" -> {
                    resourcesTransformed =
                        deserializeAndTransformToList(
                            tenantMnemonic,
                            msg,
                            Location::class,
                            RoninLocation.create(serviceFactory.conceptMapClient())
                        )
                }
                "PractitionerRole" -> {
                    resourcesTransformed =
                        deserializeAndTransformToList(tenantMnemonic, msg, PractitionerRole::class, RoninPractitionerRole)
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
