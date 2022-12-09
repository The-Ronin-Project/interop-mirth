package com.projectronin.interop.mirth.channel.base

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.fhir.ronin.ProfileTransformer
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException
import com.projectronin.interop.tenant.config.exception.TenantMissingException
import mu.KotlinLogging
import kotlin.reflect.KClass

/**
 * Shared base class for abstract Mirth [ChannelService] and [DestinationService] subclasses.
 * Provides common properties and utility functions.
 *
 * Note: Subclasses of [BaseService] use maps frequently.
 * Subclasses MAY NOT use the following literal values as map keys, because they are already in use:
 *
 * - "tenantMnemonic"
 * - "channelService"
 * - "serviceMap"
 *
 * The following are the Mirth channel stages, in sequential processing order.
 * Not all stages are required or useful in every Interops channel design.
 *
 * - Deploy
 * - Source Reader
 * - Attachment Handler
 * - Preprocessor
 * - Source Filter
 * - Source Transformer
 * - Destinations (one or more per channel) - each Destination may have 1-3 stages:
 *     - Destination Filter
 *     - Destination Transformer
 *     - Destination Writer
 * - Response Transformer
 * - Postprocessor
 * - Response
 * - Undeploy
 *
 * Required stages for Interops channels are:
 * Deploy, Source Reader, and at least one Destination Writer.
 * Other stages may be present in an Interops channel definition, and if so,
 * they execute in the order shown.
 *
 * Deploy runs once, to install the channel code version in Mirth.
 *
 * Channel stages from the Source Reader through the Postprocessor run in sequence each time the channel polls.
 *
 * When a channel is defined with multiple Destinations,
 * Destinations can be configured to run in independent order,
 * in parallel threads, or queued to wait for each other, as desired.
 * Regardless, the overall order of channel stages, and of
 * stages within each Destination, is as shown.
 *
 * The Postprocessor executes once for each poll only after all Destinations
 * and the Response Transformer (if any) have finished.
 *
 * Undeploy runs as part of Redeploying a new channel version. It may run separately as well.
 *
 * For clarification, see the Mirth user guide:
 * https://www.nextgen.com/-/media/files/nextgen-connect/nextgen-connect-311-user-guide.pdf
 * or the corresponding guide for the Mirth version in use.
 * See "The Message Processing Lifecycle" under "The Fundamentals of Mirth Connect".
 */
abstract class BaseService(val serviceFactory: ServiceFactory) {
    protected val logger = KotlinLogging.logger(this::class.java.name)

    /**
     * rootName is the tenant agnostic channel name as archived in source control.
     * Example: "PractitionerLoad".
     *
     * Each deployed channel in Mirth prefixes a tenant mnemonic and hyphen to this rootName.
     * The tenant mnemonic is the lowercase string defined for each Ronin customer in the Ronin tenant ID list.
     *
     * Example: the deployed channel name "MDAOC-PractitionerLoad" in Mirth
     * corresponds to the [BaseService] rootName "PractitionerLoad"
     * for the "mdaoc" tenant mnemonic.
     */
    abstract val rootName: String

    /**
     * When the channel has a strategy to manage data flow, maxChunkSize is the data chunk size to use.
     */
    var maxChunkSize: Int = 5

    /**
     * If the tenant mnemonic value is not already present in the input serviceMap,
     * extract the tenant mnemonic string from the deployedChannelName and add it to the serviceMap.
     *
     * @return copy of serviceMap with the tenant mnemonic value at the key "tenantMnemonic".
     * @throws TenantMissingException if no tenant mnemonic can be found.
     */
    protected fun addTenantToServiceMap(deployedChannelName: String, serviceMap: Map<String, Any>): Map<String, Any> {
        return if (serviceMap.containsKey(MirthKey.TENANT_MNEMONIC.code)) {
            serviceMap
        } else {
            mapOf(MirthKey.TENANT_MNEMONIC.code to getTenantNameFromDeployedChannelName(deployedChannelName)) + serviceMap
        }
    }

    /**
     * Extract the tenant mnemonic string from the deployed channel name string.
     * Example: deployedChannelName "MDAOC-PractitionerLoad" and
     * the channel rootName "PractitionerLoad"
     * return the tenant mnemonic "mdaoc".
     *
     * @return tenant mnemonic value.
     * @throws TenantMissingException if no tenant mnemonic can be parsed from the name.
     */
    protected fun getTenantNameFromDeployedChannelName(deployedChannelName: String): String {
        if ((rootName.isEmpty()) || (deployedChannelName == rootName)) {
            throw TenantMissingException()
        }
        val index = deployedChannelName.indexOf("-$rootName")
        return if (index > 0) deployedChannelName.substring(0, index).lowercase() else throw TenantMissingException()
    }

    /**
     * If an incoming Mirth data map, such as a sourceMap,
     * provides a map of MirthKey.MAX_CHUNK_SIZE.code to an Int value,
     * this method updates [maxChunkSize] to the Int value and returns the new Int value of [maxChunkSize].
     */
    protected fun confirmMaxChunkSize(dataMap: Map<String, Any>): Int {
        if (dataMap.contains(MirthKey.MAX_CHUNK_SIZE.code)) {
            maxChunkSize = dataMap[MirthKey.MAX_CHUNK_SIZE.code] as Int
        }
        return maxChunkSize
    }

    /**
     * For a sourceReader() or destinationWriter() that receives a list of resources from the channel msg parameter.
     * Deserializes the list and calls transformToList() to transform it; returns the resulting list as output.
     * @param tenantMnemonic tenant mnemonic
     * @param msg the Mirth channel msg
     * @param clazz the specific resource class
     * @param transformer the profile transformer class
     * @return resources after transformation; individual failures are skipped, so this list may be shorter or empty.
     */
    protected fun <T : Resource<T>> deserializeAndTransformToList(
        tenantMnemonic: String,
        msg: String,
        clazz: KClass<T>,
        transformer: ProfileTransformer<T>
    ): List<T> {
        return transformToList(tenantMnemonic, JacksonUtil.readJsonList(msg, clazz), transformer)
    }

    /**
     * For a sourceReader()) or destinationWriter() that gets and transforms lists of resources "in place" - rather
     * that receiving resources via the Mirth channelMap or msg passed along by an earlier channel stage -
     * this function transforms the input list to the output list using the supplied transformer class.
     * @param tenantMnemonic tenant mnemonic
     * @param resourceList the resources before transformation
     * @param transformer the profile transformer class
     * @return resources after transformation; individual failures are skipped, so this list may be shorter or empty.
     */
    protected fun <T : Resource<T>> transformToList(
        tenantMnemonic: String,
        resourceList: List<T>,
        transformer: ProfileTransformer<T>
    ): List<T> {
        val tenant = serviceFactory.getTenant(tenantMnemonic)
        val transformManager = serviceFactory.transformManager()
        return resourceList.mapNotNull { resource ->
            transformManager.transformResource(resource, transformer, tenant)
        }
    }

    /**
     * For a sourceTransformer() or destinationTransformer() that returns a [MirthMessage] to the channel.
     * Gets the list of resources to transform by deserializing the string from the channel msg parameter.
     * Calls transformToMessage() to transform them and return a [MirthMessage] with the results.
     * @param tenantMnemonic tenant mnemonic
     * @param msg the Mirth channel msg
     * @param clazz the specific resource class
     * @param transformer the profile transformer class
     * @return [MirthMessage] message = serialized list of transformed resources, dataMap = info about list counts
     * @throw [ResourcesNotTransformedException] the transformed result is null or empty; the channel should stop
     */
    protected fun <T : Resource<T>> deserializeAndTransformToMessage(
        tenantMnemonic: String,
        msg: String,
        clazz: KClass<T>,
        transformer: ProfileTransformer<T>
    ): MirthMessage {
        val resourceList = JacksonUtil.readJsonList(msg, clazz)
        val resourceType = clazz.simpleName
        return transformToMessage(tenantMnemonic, resourceList, resourceType!!, transformer)
    }

    /**
     * For a sourceTransformer() or destinationTransformer() that returns a [MirthMessage] to the channel.
     * Transforms the list of resources using the supplied transformer.
     * To construct success or failure messages, derives the resourceType, such as "Observation", from clazz.
     * Individual failures are skipped, so the output list may be shorter. dataMap provides before/after counts.
     * @param tenantMnemonic tenant mnemonic
     * @param resourceList the resource list
     * @param resourceType label for the resource type, such as "Observation"; default if not supplied is "Resource".
     * @param transformer the profile transformer class
     * @return [MirthMessage] where message = serialized list of resources, dataMap = information about issues if any
     * @throw [ResourcesNotTransformedException] the transformed result is null or empty; the channel should stop
     */
    protected fun <T : Resource<T>> transformToMessage(
        tenantMnemonic: String,
        resourceList: List<T>,
        resourceType: String,
        transformer: ProfileTransformer<T>
    ): MirthMessage {
        val tenant = serviceFactory.getTenant(tenantMnemonic)
        val transformManager = serviceFactory.transformManager()
        val transformedList = resourceList.mapNotNull { resource ->
            transformManager.transformResource(resource, transformer, tenant)
        }
        if (transformedList.isEmpty()) {
            throw ResourcesNotTransformedException(
                message = "Failed to transform $resourceType(s) for tenant $tenantMnemonic"
            )
        }
        val foundCount = resourceList.size
        val transformedCount = transformedList.size
        val failureCount = foundCount - transformedCount
        if (failureCount > 0) {
            logger.warn {
                "$failureCount of $foundCount $resourceType transformation(s) failed"
            }
        }
        return MirthMessage(
            message = JacksonUtil.writeJsonValue(transformedList),
            dataMap = mapOf(MirthKey.FAILURE_COUNT.code to failureCount)
        )
    }
}
