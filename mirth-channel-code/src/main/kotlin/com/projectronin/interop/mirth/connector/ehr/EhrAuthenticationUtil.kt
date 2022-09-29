package com.projectronin.interop.mirth.connector.ehr

import com.projectronin.interop.ehr.auth.EHRAuthenticationBroker

/**
 * Utility providing access to EHR Authentication services.
 */
internal object EhrAuthenticationUtil {
    val authenticationBroker = EHRAuthenticationBroker(listOf(EpicServiceFactory.authenticationService))
}
