package io.github.kostack.spring_tools.controller.resolver

import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebInputException

fun <T> ServerWebExchange.getPathParameter(name: String): T {
  val pathVars =
    getAttribute<Map<String, T>>(
      HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE
    ) ?: throw IllegalArgumentException("Missing path variables")

  return pathVars[name] ?: throw IllegalArgumentException("Missing path variable: $name")
}

fun ServerWebExchange.getRequestParameter(name: String): String =
  getRequestParameterOrNull(name)
    ?: throw ServerWebInputException("Missing request parameter: $name")

fun ServerWebExchange.getRequestParameterOrNull(name: String): String? = request.queryParams.getFirst(name)
