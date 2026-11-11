package io.github.kostack.spring_tools.dto

data class ApiValidation(
  val message: String = "Validation Failed",
  val violations: List<Violation> = emptyList()
) {
  data class Violation(
    val fieldId: String,
    val message: String,
    val metadata: Map<String, Any?> = emptyMap()
  )
}
