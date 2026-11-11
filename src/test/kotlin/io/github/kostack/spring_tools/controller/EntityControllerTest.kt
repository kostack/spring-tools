package io.github.kostack.spring_tools.controller

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

class EntityControllerTest {
  private val client =
    WebTestClient
      .bindToController(EntityController())
      .argumentResolvers { it.addCustomResolver(EntityArgumentResolver()) }
      .build()

  @Test
  fun `resolves entity argument from path variable`() {
    client
      .get()
      .uri("/entity-with-path/{id}", "abc-123")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus()
      .isOk
      .expectHeader()
      .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
      .expectBody()
      .jsonPath("$.id")
      .isEqualTo("abc-123")
  }

  @Test
  fun `returns 404 for unknown route`() {
    client
      .get()
      .uri("/entity-with-path")
      .exchange()
      .expectStatus()
      .isNotFound
  }
}
