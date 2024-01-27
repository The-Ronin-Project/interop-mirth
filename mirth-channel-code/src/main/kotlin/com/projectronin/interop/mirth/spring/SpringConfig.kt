package com.projectronin.interop.mirth.spring

import com.projectronin.ehr.dataauthority.client.spring.EHRDataAuthorityClientSpringConfig
import com.projectronin.interop.aidbox.spring.AidboxSpringConfig
import com.projectronin.interop.backfill.client.spring.BackfillClientSpringConfig
import com.projectronin.interop.common.http.spring.HttpSpringConfig
import com.projectronin.interop.ehr.cerner.spring.CernerSpringConfig
import com.projectronin.interop.ehr.epic.spring.EpicSpringConfig
import com.projectronin.interop.kafka.spring.KafkaConfig
import com.projectronin.interop.publishers.spring.PublishersSpringConfig
import com.projectronin.interop.queue.db.spring.DbQueueSpringConfig
import com.projectronin.interop.queue.kafka.spring.KafkaQueueSpringConfig
import com.projectronin.interop.rcdm.transform.spring.TransformSpringConfig
import com.projectronin.interop.tenant.config.spring.TenantSpringConfig
import org.ktorm.database.Database
import org.ktorm.support.mysql.MySqlDialect
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.task.TaskExecutorBuilder
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.vault.annotation.VaultPropertySource

@Configuration
@ComponentScan(
    basePackages = [
        "com.projectronin.interop.mirth",
        "com.projectronin.clinical.trial.client", // INT-2452
    ],
)
@Import(
    HttpSpringConfig::class,
    EHRDataAuthorityClientSpringConfig::class,
    EpicSpringConfig::class,
    CernerSpringConfig::class,
    TenantSpringConfig::class,
    PublishersSpringConfig::class,
    KafkaQueueSpringConfig::class,
    DbQueueSpringConfig::class,
    TransformSpringConfig::class,
    AidboxSpringConfig::class,
    BackfillClientSpringConfig::class,
)
@EnableConfigurationProperties(KafkaConfig::class)
@VaultPropertySource("interop-mirth-connector/\${ENVIRONMENT}")
class SpringConfig {
    // allows placeholder values like ${aidbox.url} to work.
    @Bean
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
        @Value("\${queue.db.password:null}") password: String?,
    ): Database =
        Database.connect(
            url = url,
            user = userName,
            password = password,
            dialect = MySqlDialect(),
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
        @Value("\${tenant.db.password:null}") password: String?,
    ): Database =
        Database.connect(
            url = url,
            user = userName,
            password = password,
            dialect = MySqlDialect(),
        )

    @Bean
    fun threadPoolTaskExecutor(
        @Value("\${task.executor.pool.core:8}") corePoolSize: Int,
        @Value("\${task.executor.pool.max:8}") maxPoolSize: Int,
        @Value("\${task.executor.thread-prefix:interop-task-pool}") threadNamePrefix: String,
    ): ThreadPoolTaskExecutor =
        TaskExecutorBuilder().corePoolSize(corePoolSize).maxPoolSize(maxPoolSize).threadNamePrefix(threadNamePrefix)
            .build()
}

object SpringUtil {
    val applicationContext by lazy {
        AnnotationConfigApplicationContext(SpringConfig::class.java)
    }
}
