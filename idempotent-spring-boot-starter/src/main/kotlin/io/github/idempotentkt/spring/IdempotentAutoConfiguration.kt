package io.github.idempotentkt.spring

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.idempotentkt.core.IdempotencyMetrics
import io.github.idempotentkt.core.IdempotencyStore
import io.github.idempotentkt.core.IdempotentExecutor
import io.github.idempotentkt.core.InMemoryIdempotencyStore
import io.github.idempotentkt.storage.jdbc.JdbcIdempotencyStore
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import javax.sql.DataSource

@AutoConfiguration(after = [DataSourceAutoConfiguration::class])
@EnableConfigurationProperties(IdempotentProperties::class)
class IdempotentAutoConfiguration {

    private fun mapper(provider: ObjectProvider<ObjectMapper>): ObjectMapper =
        provider.ifAvailable ?: jacksonObjectMapper()

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "idempotent", name = ["storage"], havingValue = "JDBC", matchIfMissing = true)
    fun jdbcIdempotencyStore(
        dataSource: DataSource,
        objectMappers: ObjectProvider<ObjectMapper>,
        props: IdempotentProperties,
    ): IdempotencyStore {
        val store = JdbcIdempotencyStore(dataSource, mapper(objectMappers), props.tableName)
        if (props.initializeSchema) store.initializeSchema()
        return store
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "idempotent", name = ["storage"], havingValue = "IN_MEMORY")
    fun inMemoryIdempotencyStore(): IdempotencyStore = InMemoryIdempotencyStore()

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "idempotent", name = ["metrics-enabled"], havingValue = "true", matchIfMissing = true)
    fun micrometerIdempotencyMetrics(registries: ObjectProvider<MeterRegistry>): IdempotencyMetrics =
        registries.ifAvailable?.let(::MicrometerIdempotencyMetrics) ?: IdempotencyMetrics.NoOp

    @Bean
    @ConditionalOnMissingBean(IdempotencyMetrics::class)
    fun noopIdempotencyMetrics(): IdempotencyMetrics = IdempotencyMetrics.NoOp

    @Bean
    @ConditionalOnMissingBean
    fun idempotentExecutor(store: IdempotencyStore, metrics: IdempotencyMetrics): IdempotentExecutor =
        IdempotentExecutor(store, metrics)

    @Bean
    fun idempotencyFilter(props: IdempotentProperties): IdempotencyFilter =
        IdempotencyFilter(props.maxBodyBytes)

    @Bean
    fun responseCapturingFilter(): ResponseCapturingFilter = ResponseCapturingFilter()

    @Bean
    fun idempotencyInterceptor(executor: IdempotentExecutor): IdempotencyInterceptor =
        IdempotencyInterceptor(executor)

    @Bean
    fun idempotencyWebMvcConfigurer(interceptor: IdempotencyInterceptor): WebMvcConfigurer =
        object : WebMvcConfigurer {
            override fun addInterceptors(registry: InterceptorRegistry) {
                registry.addInterceptor(interceptor)
            }
        }
}
