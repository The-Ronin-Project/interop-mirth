package com.projectronin.interop.mirth.channels.client.mirth

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

const val appointmentLoadChannelName = "AppointmentLoad"
const val carePlanLoadChannelName = "CarePlanLoad"
const val conditionLoadChannelName = "ConditionLoad"
const val docRefLoadName = "DocumentReferenceLoad"
const val encounterLoadChannelName = "EncounterLoad"
const val locationLoadChannelName = "LocationLoad"
const val medicationAdministrationLoadChannelName = "MedicationAdministrationLoad"
const val medicationLoadChannelName = "MedicationLoad"
const val medicationRequestLoadChannelName = "MedicationRequestLoad"
const val medicationStatementLoadChannelName = "MedicationStatementLoad"
const val observationLoadChannelName = "ObservationLoad"
const val patientLoadChannelName = "PatientLoad"
const val patientDiscoverChannelName = "PatientDiscovery"
const val practitionerLoadChannelName = "PractitionerLoad"
const val requestGroupLoadChannelName = "RequestGroupLoad"
const val diagnosticReportLoadChannelName = "DiagnosticReportLoad"

// some non dag but still install to make easier
const val backfillDiscoveryQueueName = "BackfillDiscoveryQueue"
const val kafkaAppointmentQueueChannelName = "KafkaAppointmentQueue"
const val kafkaConditionQueueChannelName = "KafkaConditionQueue"
const val kafkaPatientQueueChannelName = "KafkaPatientQueue"
const val kafkaPractitionerQueueChannelName = "KafkaPractitionerQueue"
const val onboardFlagChannelName = "OnboardFlag"
const val resourceRequestChannelName = "ResourceRequest"

object ChannelMap {
    val fullDag = listOf(
        appointmentLoadChannelName,
        carePlanLoadChannelName,
        conditionLoadChannelName,
        conditionLoadChannelName,
        docRefLoadName,
        encounterLoadChannelName,
        locationLoadChannelName,
        medicationAdministrationLoadChannelName,
        medicationStatementLoadChannelName,
        medicationLoadChannelName,
        medicationRequestLoadChannelName,
        observationLoadChannelName,
        patientLoadChannelName,
        patientDiscoverChannelName,
        practitionerLoadChannelName,
        requestGroupLoadChannelName,
        diagnosticReportLoadChannelName,

        // non dag
        backfillDiscoveryQueueName,
        kafkaAppointmentQueueChannelName,
        kafkaConditionQueueChannelName,
        kafkaPatientQueueChannelName,
        kafkaPractitionerQueueChannelName,
        onboardFlagChannelName,
        resourceRequestChannelName
    )
    val installedDag: Map<String, String>
    init {
        installedDag = fullDag.associateWith {
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
