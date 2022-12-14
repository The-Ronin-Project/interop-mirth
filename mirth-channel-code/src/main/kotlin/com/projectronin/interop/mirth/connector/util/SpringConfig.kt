package com.projectronin.interop.mirth.connector.util

import com.projectronin.interop.kafka.config.KafkaConfig
import org.ktorm.database.Database
import org.ktorm.support.mysql.MySqlDialect
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import org.springframework.vault.annotation.VaultPropertySource

@Configuration
@ComponentScan("com.projectronin.interop")
@EnableConfigurationProperties(KafkaConfig::class)
@VaultPropertySource("interop-mirth-connector/\${ENVIRONMENT}")
class SpringConfig {

    @Bean
    // allows placeholder values like ${aidbox.url} to work.
    fun property(): PropertySourcesPlaceholderConfigurer {
        val ret = PropertySourcesPlaceholderConfigurer()
        ret.setNullValue("null") // allow easy null defaults
        return ret
    }

    /**
     * The returns [Database] for the interop-queue.
     * See Also: [Bean] and [Qualifier] annotation.
     */
    @Bean
    @Qualifier("queue")
    fun queueDatabase(
        @Value("\${queue.db.url}") url: String,
        @Value("\${queue.db.username:null}") userName: String?,
        @Value("\${queue.db.password:null}") password: String?
    ): Database = Database.connect(
        url = url,
        user = userName,
        password = password,
        dialect = MySqlDialect()
    )

    /**
     * The returns [Database] for the interop-ehr.
     * See Also: [Bean] and [Qualifier] annotation.
     */
    @Bean
    @Qualifier("ehr")
    fun ehrDatabase(
        @Value("\${tenant.db.url}") url: String,
        @Value("\${tenant.db.username:null}") userName: String?,
        @Value("\${tenant.db.password:null}") password: String?
    ): Database = Database.connect(
        url = url,
        user = userName,
        password = password,
        dialect = MySqlDialect()
    )
}

object SpringUtil {
    val applicationContext by lazy {
        AnnotationConfigApplicationContext(SpringConfig::class.java)
    }
}
