package io.github.kostack.spring_tools.exception

import io.github.kostack.spring_tools.dto.ApiValidation

data class ValidationException(
  val violations: List<ApiValidation.Violation>,
  override val message: String = "Validation Failed"
) : RuntimeException(message) {
  override fun toString(): String = "ValidationException(violations=$violations, message=$message)"
}
