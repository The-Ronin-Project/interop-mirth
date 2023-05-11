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
     * Key for a Mirth data map: The value is a collection of resources returned by a get or search.
     */
    RESOURCES_FOUND("resourcesFound"),

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
     * Key for a Mirth data map: The value is the Patient FHIR ID associated with the resource.
     */
    PATIENT_FHIR_ID("patientFhirID"),

    /**
     * A full serialized patient object we need to create in the Ronin clinical data store
     */
    NEW_PATIENT_JSON("patientToCreateJson"),

    /**
     * Count of individual failures encountered while operating on a list of resources.
     */
    FAILURE_COUNT("failureCount"),

    /**
     * Trigger type for an event, i.e. DataTrigger.NIGHTLY
     */
    DATA_TRIGGER("dataTrigger"),

    /**
     * Kafka Event type e.g InteropPublish
     */
    KAFKA_EVENT("kafkaEvent"),

    /**
     * An event's Metadata.
     */
    EVENT_METADATA("kafkaEventMetadata")
}
