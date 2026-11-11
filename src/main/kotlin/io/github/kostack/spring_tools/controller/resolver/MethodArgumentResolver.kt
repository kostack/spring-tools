package io.github.kostack.spring_tools.controller.resolver

import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebInputException

interface MethodArgumentResolver {
  fun <T> getPathParameter(
    name: String,
    exchange: ServerWebExchange
  ): T {
    val pathVars =
      exchange.getAttribute<Map<String, T>>(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE
      ) ?: throw IllegalArgumentException("Missing path variables")

    return pathVars[name] ?: throw IllegalArgumentException("Missing path variable: $name")
  }

  fun getRequestParameter(
    name: String,
    exchange: ServerWebExchange
  ): String =
    getRequestParameterOrNull(name, exchange)
      ?: throw ServerWebInputException("Missing request parameter: $name")

  fun getRequestParameterOrNull(
    name: String,
    exchange: ServerWebExchange
  ): String? = exchange.request.queryParams.getFirst(name)
}
