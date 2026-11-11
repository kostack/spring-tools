package io.github.kostack.spring_tools.controller

import io.github.kostack.spring_tools.Entity
import io.github.kostack.spring_tools.controller.resolver.MethodArgumentResolver
import io.github.kostack.spring_tools.controller.resolver.ResolvedArgument
import kotlinx.coroutines.reactor.mono
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.reactive.BindingContext
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class EntityArgumentResolver :
  HandlerMethodArgumentResolver,
  MethodArgumentResolver {
  override fun supportsParameter(parameter: MethodParameter): Boolean =
    parameter.hasParameterAnnotation(ResolvedArgument::class.java) &&
      parameter.parameterType == Entity::class.java

  override fun resolveArgument(
    parameter: MethodParameter,
    bindingContext: BindingContext,
    exchange: ServerWebExchange
  ): Mono<Any> =
    mono {
      val id = getPathParameter<String>("id", exchange)
      return@mono Entity(id)
    }
}
