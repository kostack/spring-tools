package io.github.kostack.spring_tools.utils

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

inline fun <reified T> deepClone(
  obj: T,
  mapper: ObjectMapper
): T = mapper.readValue(mapper.writeValueAsBytes(obj), object : TypeReference<T>() {})
