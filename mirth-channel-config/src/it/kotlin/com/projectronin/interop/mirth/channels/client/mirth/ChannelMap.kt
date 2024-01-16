package com.projectronin.interop.mirth.channels.client.mirth

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

const val APPOINTMENT_LOAD_CHANNEL_NAME = "AppointmentLoad"
const val CARE_PLAN_LOAD_CHANNEL_NAME = "CarePlanLoad"
const val CONDITION_LOAD_CHANNEL_NAME = "ConditionLoad"
const val DOC_REF_LOAD_CHANNEL_NAME = "DocumentReferenceLoad"
const val ENCOUNTER_LOAD_CHANNEL_NAME = "EncounterLoad"
const val LOCATION_LOAD_CHANNEL_NAME = "LocationLoad"
const val MEDICATION_ADMIN_LOAD_CHANNEL_NAME = "MedicationAdministrationLoad"
const val MEDICATION_LOAD_CHANNEL_NAME = "MedicationLoad"
const val MEDICATION_REQUEST_LOAD_CHANNEL_NAME = "MedicationRequestLoad"
const val MEDICATION_STATEMENT_LOAD_CHANNEL_NAME = "MedicationStatementLoad"
const val OBSERVATION_LOAD_CHANNEL_NAME = "ObservationLoad"
const val PATIENT_LOAD_CHANNEL_NAME = "PatientLoad"
const val PATIENT_DISCOVERY_CHANNEL_NAME = "PatientDiscovery"
const val PRACTITIONER_LOAD_CHANNEL_NAME = "PractitionerLoad"
const val PROCEDURE_LOAD_CHANNEL_NAME = "ProcedureLoad"
const val REQUEST_GROUP_LOAD_CHANNEL_NAME = "RequestGroupLoad"
const val SERVICE_REQUEST_LOAD_CHANNEL_NAME = "ServiceRequestLoad"
const val DIAGNOSTIC_REPORT_LOAD_CHANNEL_NAME = "DiagnosticReportLoad"

// some non dag but still install to make easier
const val BACKFILL_DISCOVERY_QUEUE_CHANNEL_NAME = "BackfillDiscoveryQueue"
const val KAFKA_APPOINTMENT_QUEUE_CHANNEL_NAME = "KafkaAppointmentQueue"
const val KAFKA_CONDITION_QUEUE_CHANNEL_NAME = "KafkaConditionQueue"
const val KAFKA_PATIENT_QUEUE_CHANNEL_NAME = "KafkaPatientQueue"
const val KAFKA_PRACTITIONER_QUEUE_CHANNEL_NAME = "KafkaPractitionerQueue"
const val ONBOARD_FLAG_CHANNEL_NAME = "OnboardFlag"
const val RESOURCE_REQUEST_CHANNEL_NAME = "ResourceRequest"

object ChannelMap {
    val fullDag =
        listOf(
            APPOINTMENT_LOAD_CHANNEL_NAME,
            CARE_PLAN_LOAD_CHANNEL_NAME,
            CONDITION_LOAD_CHANNEL_NAME,
            CONDITION_LOAD_CHANNEL_NAME,
            DOC_REF_LOAD_CHANNEL_NAME,
            ENCOUNTER_LOAD_CHANNEL_NAME,
            LOCATION_LOAD_CHANNEL_NAME,
            MEDICATION_ADMIN_LOAD_CHANNEL_NAME,
            MEDICATION_STATEMENT_LOAD_CHANNEL_NAME,
            MEDICATION_LOAD_CHANNEL_NAME,
            MEDICATION_REQUEST_LOAD_CHANNEL_NAME,
            OBSERVATION_LOAD_CHANNEL_NAME,
            PATIENT_LOAD_CHANNEL_NAME,
            PATIENT_DISCOVERY_CHANNEL_NAME,
            PRACTITIONER_LOAD_CHANNEL_NAME,
            PROCEDURE_LOAD_CHANNEL_NAME,
            REQUEST_GROUP_LOAD_CHANNEL_NAME,
            SERVICE_REQUEST_LOAD_CHANNEL_NAME,
            DIAGNOSTIC_REPORT_LOAD_CHANNEL_NAME,
            // non dag
            BACKFILL_DISCOVERY_QUEUE_CHANNEL_NAME,
            KAFKA_APPOINTMENT_QUEUE_CHANNEL_NAME,
            KAFKA_CONDITION_QUEUE_CHANNEL_NAME,
            KAFKA_PATIENT_QUEUE_CHANNEL_NAME,
            KAFKA_PRACTITIONER_QUEUE_CHANNEL_NAME,
            ONBOARD_FLAG_CHANNEL_NAME,
            RESOURCE_REQUEST_CHANNEL_NAME,
        )
    val installedDag: Map<String, String>

    init {
        installedDag =
            fullDag.associateWith {
                val id = installChannel(it)
                // mirth is a wittle baby a needs some time
                runBlocking { delay(250) }
                MirthClient.deployChannel(id)
                id
            }
    }

    private fun installChannel(channelToInstall: String): String {
        val channelFile = File("channels/$channelToInstall/channel/$channelToInstall.xml")
        val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val document = documentBuilder.parse(channelFile)

        val channelId = document.getElementsByTagName("id").item(0).firstChild.textContent
        // make it poll quicker for its
        document.getElementsByTagName("pollingFrequency").item(0).firstChild.textContent = "1000"
        val domSource = DOMSource(document)
        val transformer = TransformerFactory.newInstance().newTransformer()

        val stringWriter = StringWriter()
        val streamResult = StreamResult(stringWriter)
        transformer.transform(domSource, streamResult)

        val modifiedXml = stringWriter.toString()
        MirthClient.putChannel(channelId, modifiedXml)
        MirthClient.enableChannel(channelId)

        return channelId
    }
}
