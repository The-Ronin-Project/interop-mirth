package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninLocation
import com.projectronin.interop.fhir.ronin.util.unlocalize
import com.projectronin.interop.mirth.channel.base.ChannelService
import com.projectronin.interop.mirth.channel.destinations.LocationWriter
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.exception.ResourcesNotFoundException
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException
import org.springframework.stereotype.Component

private const val PUBLISH_SERVICE = "publish"

@Component
class LocationLoad(
    tenantService: TenantService,
    transformManager: TransformManager,
    locationWriter: LocationWriter,
    private val ehrFactory: EHRFactory,
    private val tenantConfigurationService: TenantConfigurationService,
    private val roninLocation: RoninLocation
) : ChannelService(tenantService, transformManager) {
    companion object : ChannelFactory<LocationLoad>()

    override val rootName = "LocationLoad"
    override val destinations = mapOf(PUBLISH_SERVICE to locationWriter)

    override fun channelSourceReader(tenantMnemonic: String, serviceMap: Map<String, Any>): List<MirthMessage> {

        val locationIdsList = tenantConfigurationService.getLocationIDsByTenant(tenantMnemonic)
        if (locationIdsList.isEmpty()) {
            throw ResourcesNotFoundException("No Location IDs configured for tenant $tenantMnemonic")
        }

        val tenant = getTenant(tenantMnemonic)
        val vendorFactory = ehrFactory.getVendorFactory(tenant)

        val response = vendorFactory.locationService.getLocationsByFHIRId(
            tenant,
            locationIdsList.unlocalize(tenant)
        )
        return response.values.chunked(confirmMaxChunkSize(serviceMap)).map { locations ->
            MirthMessage(
                message = JacksonUtil.writeJsonValue(locations),
                dataMap = mapOf(
                    MirthKey.RESOURCES_FOUND.code to locations,
                    MirthKey.RESOURCE_TYPE.code to locations.first().resourceType,
                    MirthKey.RESOURCE_COUNT.code to locations.size
                )
            )
        }
    }

    override fun channelSourceTransformer(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthMessage {

        val locations = JacksonUtil.readJsonList(msg, Location::class)
        if (locations.isEmpty()) {
            throw ResourcesNotFoundException("No Locations found for tenant $tenantMnemonic")
        }

        val locationsTransformed = transformToList(tenantMnemonic, locations, roninLocation)
        if (locationsTransformed.isEmpty()) {
            throw ResourcesNotTransformedException("Failed to transform Locations for tenant $tenantMnemonic")
        }

        val locationFHIRIds = locationsTransformed.mapNotNull { resource -> resource.id?.value }
        return MirthMessage(
            message = JacksonUtil.writeJsonValue(locationsTransformed),
            dataMap = mapOf(
                MirthKey.RESOURCES_TRANSFORMED.code to locationsTransformed,
                MirthKey.FHIR_ID_LIST.code to locationFHIRIds.joinToString(",")
            )
        )
    }
}
