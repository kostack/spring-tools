package io.github.kostack.spring_tools.controller

import io.github.kostack.spring_tools.Entity
import io.github.kostack.spring_tools.controller.resolver.ResolvedArgument
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class EntityController {
  @GetMapping("/entity-with-path/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
  suspend fun entityWithPathAction(
    @PathVariable id: String,
    @ResolvedArgument entity: Entity
  ): Entity? = entity
}
