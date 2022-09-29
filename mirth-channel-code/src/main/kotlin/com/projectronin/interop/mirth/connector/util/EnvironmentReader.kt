package com.projectronin.interop.mirth.connector.util

import kotlinx.coroutines.runBlocking

/**
 * This class is an abstraction around reading environment variables. It allows a straightforward way to mock access to environment variables.
 */
class EnvironmentReader {
    companion object {
        private val vaultValues: Map<String, String> by lazy {
            runBlocking {
                runCatching { VaultClient().readConfig() }.getOrDefault(emptyMap())
            }
        }

        /**
         * Reads the environment variable present for [name].
         */
        internal fun read(name: String): String? = vaultValues[name] ?: System.getenv(name)

        /**
         * Reads the environment variable present for [name], returning [default] if none is found.
         */
        internal fun readWithDefault(name: String, default: String): String =
            vaultValues[name] ?: System.getenv(name) ?: default

        /**
         * Reads the environment variable present for [name], throws [IllegalStateException] if none is found.
         */
        internal fun readRequired(name: String): String = vaultValues[name] ?: read(name)
            ?: throw IllegalStateException("Required environment variable $name missing.")
    }
}
