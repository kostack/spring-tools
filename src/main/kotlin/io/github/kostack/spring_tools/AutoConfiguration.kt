package io.github.kostack.spring_tools

import io.github.kostack.spring_tools.handler.ServerWebInputExceptionHandler
import io.github.kostack.spring_tools.handler.ValidationExceptionHandler
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean

@AutoConfiguration
class AutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  fun validationExceptionHandler(): ValidationExceptionHandler = ValidationExceptionHandler()

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
    prefix = "kostack.spring-tools.web-input-handler",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
  )
  fun serverWebInputExceptionHandler(): ServerWebInputExceptionHandler = ServerWebInputExceptionHandler()
}
