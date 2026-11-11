package io.github.kostack.spring_tools

import io.github.kostack.spring_tools.handler.ServerWebInputExceptionHandler
import io.github.kostack.spring_tools.handler.ValidationExceptionHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class AutoConfigurationTest {
  private val contextRunner =
    ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(AutoConfiguration::class.java))

  @Test
  fun `auto configuration registers default handlers`() {
    contextRunner.run { context ->
      assertThat(context).hasSingleBean(ValidationExceptionHandler::class.java)
      assertThat(context).hasSingleBean(ServerWebInputExceptionHandler::class.java)
    }
  }

  @Test
  fun `auto configuration backs off when handler bean exists`() {
    contextRunner
      .withUserConfiguration(CustomHandlerConfiguration::class.java)
      .run { context ->
        assertThat(context).hasSingleBean(ServerWebInputExceptionHandler::class.java)
        assertThat(context).getBean(ServerWebInputExceptionHandler::class.java).isSameAs(customWebInputHandler)
      }
  }

  @Test
  fun `auto configuration is listed in Boot imports metadata`() {
    val resource =
      checkNotNull(
        javaClass.classLoader.getResource(
          "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
        )
      )

    assertThat(resource.readText().lines()).contains(AutoConfiguration::class.qualifiedName)
  }

  @Configuration(proxyBeanMethods = false)
  private class CustomHandlerConfiguration {
    @Bean
    fun customWebInputHandler(): ServerWebInputExceptionHandler = customWebInputHandler
  }

  private companion object {
    val customWebInputHandler = ServerWebInputExceptionHandler()
  }
}
