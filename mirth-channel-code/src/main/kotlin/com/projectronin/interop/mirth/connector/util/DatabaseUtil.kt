package com.projectronin.interop.mirth.connector.util

import org.ktorm.database.Database
import org.ktorm.support.mysql.MySqlDialect

/**
 * Utility providing access to the Database.
 */
internal object DatabaseUtil {
    private val tenantDatabaseURL = EnvironmentReader.readRequired("TENANT_DB_URL")
    private val tenantDatabaseUsername = EnvironmentReader.read("TENANT_DB_USERNAME")
    private val tenantDatabasePassword = EnvironmentReader.read("TENANT_DB_PASSWORD")
    val tenantDatabase =
        Database.connect(
            url = tenantDatabaseURL, user = tenantDatabaseUsername, password = tenantDatabasePassword,
            dialect = MySqlDialect()
        )

    private val queueDatabaseURL = EnvironmentReader.readRequired("QUEUE_DB_URL")
    private val queueDatabaseUsername = EnvironmentReader.read("QUEUE_DB_USERNAME")
    private val queueDatabasePassword = EnvironmentReader.read("QUEUE_DB_PASSWORD")
    val queueDatabase =
        Database.connect(
            url = queueDatabaseURL,
            user = queueDatabaseUsername,
            password = queueDatabasePassword,
            dialect = MySqlDialect()
        )
}
