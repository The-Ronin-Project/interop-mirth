package com.projectronin.interop.mirth.connector.util

import com.projectronin.interop.aidbox.LocationService
import com.projectronin.interop.aidbox.PatientService
import com.projectronin.interop.aidbox.PractitionerService
import com.projectronin.interop.aidbox.PublishService
import com.projectronin.interop.aidbox.auth.AidboxAuthenticationBroker
import com.projectronin.interop.aidbox.auth.AidboxAuthenticationService
import com.projectronin.interop.aidbox.auth.AidboxCredentials
import com.projectronin.interop.aidbox.client.AidboxClient

/**
 * Utility providing access to Aidbox
 */
internal object AidboxUtil {
    private val aidboxId = EnvironmentReader.readRequired("AIDBOX_CLIENT_ID")
    private val aidboxKey = EnvironmentReader.readRequired("AIDBOX_CLIENT_SECRET")
    private val aidboxRestUrl = EnvironmentReader.readRequired("AIDBOX_REST_URL")
    private val aidboxCredentials = AidboxCredentials(aidboxId, aidboxKey)
    private val authService = AidboxAuthenticationService(HttpUtil.httpClient, aidboxRestUrl, aidboxCredentials)
    private val authBroker = AidboxAuthenticationBroker(authService)
    private val aidboxClient = AidboxClient(HttpUtil.httpClient, aidboxRestUrl, authBroker)
    val aidboxPublishService = PublishService(aidboxClient)
    val aidBoxPractitionerService = PractitionerService(aidboxClient, 100)
    val aidBoxPatientService = PatientService(aidboxClient, 100)
    val aidboxLocationService = LocationService(aidboxClient, 100)
}
