package com.projectronin.interop.mirth.channel.base

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.model.MirthFilterResponse
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.channel.model.MirthResponse
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import kotlin.reflect.KClass

/**
 * Abstract Mirth destination service class.
 * Provides functions for a channel's
 * Destination stages (Writer, Filter, Transformer).
 *
 * For clarification, see the Mirth user guide:
 * https://www.nextgen.com/-/media/files/nextgen-connect/nextgen-connect-311-user-guide.pdf
 * or the corresponding guide for the Mirth version in use.
 * See "The Message Processing Lifecycle" under "The Fundamentals of Mirth Connect".
 *
 * Each destination in each Mirth channel in Interop MUST override this function in a [DestinationService] subclass:
 * - channelDestinationWriter()
 *
 * Each destination in each Mirth channel in Interop MAY also override these functions in the same [DestinationService] subclass:
 * - channelDestinationFilter()
 * - channelDestinationTransformer()
 *
 * The [ChannelService] sets the rootName for each of its [DestinationService] subclasses
 * when it populates the members of its destinations list.
 *
 * For the correct order of execution of all required and optional Mirth channel stages, see [BaseService].
 */
abstract class DestinationService(
    tenantService: TenantService,
    transformManager: TransformManager,
    private val publishService: PublishService
) :
    BaseService(tenantService, transformManager) {
    /**
     * Mirth channels call destinationFilter() from the Destination Filter script,
     * if there is a Filter on this Destination.
     *
     * Previous channel stage: Source Transformer, or earlier stages.
     *
     * Next channel stage: Destination Transformer, or later stages.
     *
     * @param unusedValue Deprecated, unused field that Mirth is providing.
     * @param msg a string value from Mirth.
     * @param sourceMap the sourceMap from Mirth, including data collected in the serviceMap from earlier stages.
     * @param channelMap the channelMap from Mirth
     *      Map keys: For conventions and a few reserved values see [BaseService].
     * @return true if the message should continue processing, false to stop processing the message.
     */
    fun destinationFilter(
        unusedValue: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthFilterResponse {
        try {
            return channelDestinationFilter(
                sourceMap[MirthKey.TENANT_MNEMONIC.code] as String,
                msg,
                sourceMap,
                channelMap
            )
        } catch (e: Throwable) {
            logger.error(e) { "Exception encountered during destinationFilter: ${e.message}" }
            throw e
        }
    }

    /**
     * [ChannelService] subclasses must override channelDestinationFilter() to execute actions for destinationFilter()
     * if there is a Filter on this Destination; otherwise omit it.
     *
     * Previous channel stage: Source Transformer, or earlier stages.
     *
     * Next channel stage: Destination Transformer, or later stages.
     *
     * @param tenantMnemonic expect the correct value to be supplied.
     * @param msg expect [destinationFilter] to pass in the msg.
     * @param sourceMap expect [destinationFilter] to pass in the sourceMap.
     * @param channelMap expect [destinationFilter] to pass in the channelMap.
     * @return true if the message should continue processing, false to stop processing the message.
     */
    open fun channelDestinationFilter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthFilterResponse {
        return MirthFilterResponse(true)
    }

    /**
     * Mirth channels call destinationTransformer() from the Destination Transformer script,
     * if there is a Transformer on this Destination.
     *
     * Previous channel stage: Destination Filter, or earlier stages.
     *
     * Next channel stage: Destination Writer.
     *
     * @param unusedValue Deprecated, unused field that Mirth is providing.
     * @param msg a string value from Mirth.
     * @param sourceMap the sourceMap from Mirth, including data collected in the serviceMap from earlier stages.
     * @param channelMap the channelMap from Mirth
     *      Map keys: For conventions and a few reserved values see [BaseService].
     * @return a list of Mirth response data to pass to the next channel stage.
     */
    fun destinationTransformer(
        unusedValue: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthMessage {
        try {
            return channelDestinationTransformer(
                sourceMap[MirthKey.TENANT_MNEMONIC.code] as String,
                msg,
                sourceMap,
                channelMap
            )
        } catch (e: Throwable) {
            logger.error(e) { "Exception encountered during destinationTransformer: ${e.message}" }
            throw e
        }
    }

    /**
     * [ChannelService] subclasses must override channelDestinationTransformer() to execute actions for destinationTransformer()
     * if there is a Transformer on this Destination; otherwise omit it.
     *
     * Previous channel stage: Destination Filter, or earlier stages.
     *
     * Next channel stage: Destination Writer.
     *
     * @param tenantMnemonic expect the correct value to be supplied.
     * @param msg expect [destinationTransformer] to pass in the msg.
     * @param sourceMap expect [destinationTransformer] to pass in the sourceMap.
     * @param channelMap expect [destinationTransformer] to pass in the channelMap.
     * @return a list of Mirth response data to pass to the next channel stage.
     */
    open fun channelDestinationTransformer(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthMessage {
        return MirthMessage(msg)
    }

    /**
     * Required. Mirth channels call destinationWriter() from each Destination Writer script.
     *
     * Previous channel stage: Destination Transformer, or earlier stages.
     *
     * Next channel stage: Response Transformer, Postprocessor, or (None).
     *
     * @param unusedValue Deprecated, unused field that Mirth is providing.
     * @param msg a string value from Mirth.
     * @param sourceMap the sourceMap from Mirth, including data collected in the serviceMap from earlier stages.
     * @param channelMap the channelMap from Mirth
     *      Map keys: For conventions and a few reserved values see [BaseService].
     * @return a list of Mirth response data to pass to the next channel stage.
     */
    fun destinationWriter(
        unusedValue: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthResponse {
        try {
            return channelDestinationWriter(
                sourceMap[MirthKey.TENANT_MNEMONIC.code] as String,
                msg,
                sourceMap,
                channelMap
            )
        } catch (e: Throwable) {
            logger.error(e) { "Exception encountered during destinationWriter: ${e.message}" }
            throw e
        }
    }

    /**
     * Required: [DestinationService] subclasses must override channelDestinationWriter() to execute actions for destinationWriter().
     *
     * Previous channel stage: Destination Transformer, or earlier stages.
     *
     * Next channel stage: Response Transformer, Postprocessor, or (None).
     *
     * @param tenantMnemonic expect the correct value to be supplied.
     * @param msg expect [destinationWriter] to pass in the msg.
     * @param sourceMap expect [destinationWriter] to pass in the sourceMap.
     * @param channelMap expect [destinationWriter] to pass in the channelMap.
     * @return a list of Mirth response data to pass to the next channel stage.
     */
    abstract fun channelDestinationWriter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthResponse

    /**
     * Gets the list of resources to publish from the channelMap[MirthKey.RESOURCES_TRANSFORMED] entry.
     * To construct success or failure messages, derives the resourceType, such as "Observation", from clazz.
     * Publishes the list using publishResources(resourceList, resourceType).
     * @param sourceMap the Mirth sourceMap from the channelDestinationWriter()
     * @param channelMap the Mirth channelMap from the channelDestinationWriter()
     * @param resourceType label for the resource type, such as "Observation"; default if not supplied is "Resource".
     * @return [MirthResponse] status ERROR if nothing to publish, or return from publishResource()
     */
    @Suppress("UNCHECKED_CAST")
    protected fun <T : Resource<T>> publishTransformed(
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any?>,
        resourceType: String = "Resource"
    ): MirthResponse {
        val tenantMnemonic = sourceMap[MirthKey.TENANT_MNEMONIC.code] as String
        val resourceList = channelMap[MirthKey.RESOURCES_TRANSFORMED.code]?.let { it as List<T> }
        if (resourceList.isNullOrEmpty()) {
            return MirthResponse(
                status = MirthResponseStatus.ERROR,
                message = "No transformed $resourceType(s) to publish"
            )
        }
        return publishResources(tenantMnemonic, resourceList, resourceType)
    }

    /**
     * Gets the list of resources to publish by deserializing the string from the channel msg parameter.
     * To construct success or failure messages, derives the resourceType, such as "Observation", from clazz.
     * Publishes the list using publishResources(resourceList, resourceType).
     * @param tenantMnemonic the tenant for which the resources are being published
     * @param msg the Mirth channel msg from the channelDestinationWriter()
     * @param clazz the specific resource class, if only one type of resource is in the list.
     * @return [MirthResponse] status ERROR if nothing to publish, or return from publishResource()
     */
    @Suppress("UNCHECKED_CAST")
    protected fun <T : Resource<T>> deserializeAndPublishList(
        tenantMnemonic: String,
        msg: String,
        clazz: KClass<T>
    ): MirthResponse {
        val resourceType = clazz.simpleName
        val resourceList = JacksonUtil.readJsonList(msg, clazz)
        if (resourceList.isEmpty()) {
            return MirthResponse(
                status = MirthResponseStatus.ERROR,
                detailedMessage = msg,
                message = "No transformed $resourceType(s) to publish"
            )
        }
        return publishResources(tenantMnemonic, resourceList, resourceType)
    }

    /**
     * Publishes resources to the Ronin clinical data store for the channel destination.
     * Constructs success and failure messages using the resourceType label, like:
     * "Published 5 Observation(s)" or a generic default: "Published 5 Resource(s)".
     * @param tenantMnemonic the tenant for which the resources are being published
     * @param resourceList the resources to be published
     * @param resourceType label for the resource type, such as "Observation"; default if not supplied is "Resource".
     * @param successDataMap information to put in the [MirthResponse] dataMap in case of success.
     * @return [MirthResponse] status SENT (with successDataMap) or ERROR.
     */
    protected fun <T : Resource<T>> publishResources(
        tenantMnemonic: String,
        resourceList: List<T>,
        resourceType: String? = "Resource",
        successDataMap: Map<String, Any>? = null
    ): MirthResponse {
        if (!publishService.publishFHIRResources(tenantMnemonic, resourceList)) {
            return MirthResponse(
                status = MirthResponseStatus.ERROR,
                detailedMessage = JacksonUtil.writeJsonValue(resourceList),
                message = "Failed to publish $resourceType(s)"
            )
        }
        return MirthResponse(
            status = MirthResponseStatus.SENT,
            detailedMessage = JacksonUtil.writeJsonValue(resourceList),
            message = "Published ${resourceList.size} $resourceType(s)",
            dataMap = successDataMap ?: emptyMap()
        )
    }
}
