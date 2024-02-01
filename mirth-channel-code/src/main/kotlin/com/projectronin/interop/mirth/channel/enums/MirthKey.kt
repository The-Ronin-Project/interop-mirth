package com.projectronin.interop.mirth.channel.enums

/**
 * Common keys to use in Mirth source maps, channel maps, and data maps.
 */
enum class MirthKey(val code: String) {
    /**
     * Key for a Mirth data map: The value is a Ronin tenant ID value in the http://projectronin.com/id/tenantId system.
     * Interops calls this value the tenant mnemonic to avoid confusion with any other ID or identifier value.
     */
    TENANT_MNEMONIC("tenantMnemonic"),

    /**
     * When the channel has a strategy to manage data flow, maxChunkSize is the data chunk size to use.
     */
    MAX_CHUNK_SIZE("maxChunkSize"),

    /**
     * Key for a Mirth data map: The value is a collection of transformed resources.
     */
    RESOURCES_TRANSFORMED("resourcesTransformed"),

    /**
     * Key for a Mirth data map: The value is a FHIR resource type
     */
    RESOURCE_TYPE("resourceType"),

    /**
     * Key for a Mirth data map: The value is a number of resources, such as the result count from a search.
     */
    RESOURCE_COUNT("resourceCount"),

    /**
     * Key for a Mirth data map: The value is a comma-separated list of FHIR IDs.
     */
    FHIR_ID_LIST("fhirIDs"),

    /**
     * Key for a Mirth data map: The value is one FHIR ID.
     */
    FHIR_ID("fhirID"),

    /**
     * Count of individual failures encountered while operating on a list of resources.
     */
    FAILURE_COUNT("failureCount"),

    /**
     * Kafka Event type e.g InteropPublish
     */
    KAFKA_EVENT("kafkaEvent"),

    /**
     * An event's Metadata.
     */
    EVENT_METADATA("kafkaEventMetadata"),

    /**
     * An event's source reference.
     */
    EVENT_METADATA_SOURCE("kafkaEventSource"),

    /**
     * An event's source reference.
     */
    EVENT_RUN_ID("kafkaEventRunId"),

    /**
     * The ID for the backfill event
     */
    BACKFILL_ID("backfillId"),

    /**
     *  Type of PatientDiscovery run
     */
    DISCOVERY_TYPE("discoveryType"),
}
