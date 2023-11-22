package com.projectronin.interop.gradle.mirth

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import javax.inject.Inject

/**
 * Extension defining the Mirth plugin configuration options.
 */
open class MirthExtension @Inject constructor(objectFactory: ObjectFactory) {
    /**
     * The directory from which docker commands should be run. It is expected that at least a docker-compose file will reside within this folder, as well as a folder where the Mirth Connector library will be read from by Mirth.
     */
    lateinit var dockerDirectory: Provider<Directory>

    /**
     * The Mirth Connector library dependency defining which library should be installed.
     */
    lateinit var mirthConnectorLibrary: Project

    /**
     * The directory where Mirth Code Template Libraries are stored.
     */
    lateinit var codeTemplateLibraryDirectory: Provider<Directory>

    /**
     * The [ChannelExtension] for this extension.
     */
    internal val channel: com.projectronin.interop.gradle.mirth.ChannelExtension =
        objectFactory.newInstance(com.projectronin.interop.gradle.mirth.ChannelExtension::class.java)

    /**
     * Sets up the [ChannelExtension] defining channel-specific configuration.
     */
    fun channel(action: Action<com.projectronin.interop.gradle.mirth.ChannelExtension>) {
        action.execute(channel)
    }
}

/**
 * Extension defining channel-specific configuration.
 */
open class ChannelExtension @Inject constructor(objectFactory: ObjectFactory) {
    /**
     * The directory where Mirth Channels and their metadata are stored.
     */
    lateinit var baseDirectory: Provider<Directory>

    lateinit var generatedChannelDirectory: Provider<Directory>

    /**
     * The [TenantConfigExtension] for this extension.
     */
    internal val tenantConfig: com.projectronin.interop.gradle.mirth.TenantConfigExtension = objectFactory.newInstance(
        com.projectronin.interop.gradle.mirth.TenantConfigExtension::class.java
    )

    /**
     * Sets up the [TenantConfigExtension] defining tenant configuration.
     */
    fun tenant(action: Action<com.projectronin.interop.gradle.mirth.TenantConfigExtension>) {
        action.execute(tenantConfig)
    }

    /**
     * The [AidboxExtension] for this extension.
     */
    internal val aidBoxConfig: com.projectronin.interop.gradle.mirth.AidboxExtension =
        objectFactory.newInstance(com.projectronin.interop.gradle.mirth.AidboxExtension::class.java)

    /**
     * Sets up the [AidboxExtension] defining aidbox configuration.
     */
    fun aidbox(action: Action<com.projectronin.interop.gradle.mirth.AidboxExtension>) {
        action.execute(aidBoxConfig)
    }
}

/**
 * Extension defining tenant-specific configuration.
 */
open class TenantConfigExtension @Inject constructor(objectFactory: ObjectFactory) {
    /**
     * The default mnemonic that should be used.
     */
    lateinit var defaultMnemonic: String

    /**
     * The [TenantConfigAuthExtension] for this extension.
     */
    internal val auth: com.projectronin.interop.gradle.mirth.TenantConfigAuthExtension =
        objectFactory.newInstance(com.projectronin.interop.gradle.mirth.TenantConfigAuthExtension::class.java)

    /**
     * Sets up the [TenantConfigAuthExtension] defining tenant auth configuration.
     */
    fun auth(action: Action<com.projectronin.interop.gradle.mirth.TenantConfigAuthExtension>) {
        action.execute(auth)
    }
}

/**
 * Extension defining tenant-specific auth configuration.
 */
open class TenantConfigAuthExtension {
    /**
     * The URL from which auth tokens should be retrieved.
     */
    var tokenUrl: String = "http://localhost:1081/oauth/token" // do not call the real oauth server, call your local one

    /**
     * The key from which the client ID will be looked up in the environment variables.
     */
    var clientIdKey: String = "AUTH_CLIENT_ID"

    /**
     * The key from which the client secret will be looked up in the environment variables.
     */
    var clientSecretKey: String = "AUTH_CLIENT_SECRET"

    /**
     * The audience under which to register the auth token.
     */
    var audience: String = "https://interop-proxy-server-oci.dev.projectronin.io"
}

open class AidboxExtension {
    /**
     * The URL where aidbox lives
     */
    var url: String = "http://localhost:8888"

    /**
     * The key from which the client ID will be looked up in the environment variables.
     */
    var clientIdKey: String = "AIDBOX_CLIENT_ID"

    /**
     * The key from which the client secret will be looked up in the environment variables.
     */
    var clientSecretKey: String = "AIDBOX_CLIENT_SECRET"
}
