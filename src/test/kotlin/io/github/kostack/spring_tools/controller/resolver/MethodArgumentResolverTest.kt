package io.github.kostack.spring_tools.controller.resolver

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.server.ServerWebInputException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MethodArgumentResolverTest {
  private val resolver = object : MethodArgumentResolver {}

  private fun exchange(
    uri: String = "/",
    vararg uriVariables: Any,
    pathVariables: Map<String, String>? = null
  ): MockServerWebExchange =
    MockServerWebExchange.from(MockServerHttpRequest.get(uri, *uriVariables)).apply {
      if (pathVariables != null) attributes[HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE] = pathVariables
    }

  // --- getPathParameter ---

  @Test
  fun `getPathParameter returns path variable`() {
    val exchange = exchange(pathVariables = mapOf("id" to "abc-123"))

    assertEquals("abc-123", resolver.getPathParameter<String>("id", exchange))
  }

  @Test
  fun `getPathParameter throws when path variables are not set`() {
    val error =
      assertFailsWith<IllegalArgumentException> { resolver.getPathParameter<String>("id", exchange()) }

    assertEquals("Missing path variables", error.message)
  }

  @Test
  fun `getPathParameter throws when path variable is absent`() {
    val exchange = exchange(pathVariables = mapOf("other" to "value"))

    val error =
      assertFailsWith<IllegalArgumentException> { resolver.getPathParameter<String>("id", exchange) }

    assertEquals("Missing path variable: id", error.message)
  }

  // --- getRequestParameter ---

  @Test
  fun `getRequestParameter returns query parameter`() {
    assertEquals("active", resolver.getRequestParameter("status", exchange("/items?status=active")))
  }

  @Test
  fun `getRequestParameter returns first value of repeated parameter`() {
    assertEquals("a", resolver.getRequestParameter("status", exchange("/items?status=a&status=b")))
  }

  @Test
  fun `getRequestParameter returns empty string for parameter without value`() {
    assertEquals("", resolver.getRequestParameter("flag", exchange("/items?flag=")))
  }

  @Test
  fun `getRequestParameter decodes encoded values`() {
    // The URI variable is percent-encoded once (to a%20b) and decoded again when query params are parsed.
    assertEquals("a b", resolver.getRequestParameter("q", exchange("/items?q={q}", "a b")))
  }

  @Test
  fun `getRequestParameter throws 400 when parameter is absent`() {
    val error =
      assertFailsWith<ServerWebInputException> {
        resolver.getRequestParameter("status", exchange("/items?other=x"))
      }

    assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
    assertEquals("Missing request parameter: status", error.reason)
  }

  // --- getRequestParameterOrNull ---

  @Test
  fun `getRequestParameterOrNull returns query parameter`() {
    assertEquals("active", resolver.getRequestParameterOrNull("status", exchange("/items?status=active")))
  }

  @Test
  fun `getRequestParameterOrNull returns null when parameter is absent`() {
    assertNull(resolver.getRequestParameterOrNull("status", exchange("/items")))
  }
}
