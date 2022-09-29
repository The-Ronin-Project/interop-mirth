package com.projectronin.interop.mirth.connector.ehr

import com.projectronin.interop.ehr.epic.EpicAppointmentService
import com.projectronin.interop.ehr.epic.EpicConditionService
import com.projectronin.interop.ehr.epic.EpicIdentifierService
import com.projectronin.interop.ehr.epic.EpicLocationService
import com.projectronin.interop.ehr.epic.EpicMedicationService
import com.projectronin.interop.ehr.epic.EpicMessageService
import com.projectronin.interop.ehr.epic.EpicObservationService
import com.projectronin.interop.ehr.epic.EpicPatientService
import com.projectronin.interop.ehr.epic.EpicPractitionerService
import com.projectronin.interop.ehr.epic.EpicVendorFactory
import com.projectronin.interop.ehr.epic.auth.EpicAuthenticationService
import com.projectronin.interop.ehr.epic.client.EpicClient
import com.projectronin.interop.mirth.connector.ehr.EhrAuthenticationUtil.authenticationBroker
import com.projectronin.interop.mirth.connector.util.AidboxUtil
import com.projectronin.interop.mirth.connector.util.HttpUtil.httpClient
import com.projectronin.interop.mirth.connector.util.TenantUtil.providerPoolService

/**
 * Utility providing access to Epic-specific services.
 */
internal object EpicServiceFactory {
    val authenticationService = EpicAuthenticationService(httpClient)

    private val epicClient = EpicClient(httpClient, authenticationBroker)

    private val patientService = EpicPatientService(epicClient, 5, AidboxUtil.aidBoxPatientService)
    private val identifierService = EpicIdentifierService()
    private val appointmentService = EpicAppointmentService(
        epicClient,
        patientService,
        identifierService,
        AidboxUtil.aidBoxPractitionerService,
        AidboxUtil.aidBoxPatientService,
        5,
        false
    )
    private val messageService = EpicMessageService(epicClient, providerPoolService)
    private val practitionerService = EpicPractitionerService(epicClient, 1)
    private val conditionService = EpicConditionService(epicClient)
    private val observationService = EpicObservationService(epicClient, 1)
    private val locationService = EpicLocationService(epicClient)
    private val medicationService = EpicMedicationService(epicClient, 5)

    val epicVendorFactory = EpicVendorFactory(
        patientService,
        appointmentService,
        messageService,
        practitionerService,
        conditionService,
        identifierService,
        observationService,
        locationService,
        medicationService
    )
}
