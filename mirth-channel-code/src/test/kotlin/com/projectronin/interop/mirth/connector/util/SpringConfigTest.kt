package com.projectronin.interop.mirth.connector.util

import com.projectronin.interop.fhir.r4.resource.Patient
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class SpringConfigTest {

    @Test
    fun codeCov() {
        val config = SpringConfig()
        assertNotNull(config.property())
        assertThrows<Exception> { config.queueDatabase("URL", "name", "pass") }
        assertThrows<Exception> { config.ehrDatabase("URL", "name", "pass") }
        assertThrows<Exception> { config.queueDatabase("URL", null, null) }
        assertThrows<Exception> { config.ehrDatabase("URL", null, null) }
        assertThrows<Exception> { SpringUtil.applicationContext.getBean(Patient::class.java) }
    }
}
