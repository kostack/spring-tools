package io.github.kostack.spring_tools.handler

import io.github.kostack.spring_tools.dto.ApiValidation
import io.github.kostack.spring_tools.exception.ValidationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ValidationExceptionHandler {
  @ExceptionHandler(ValidationException::class)
  fun handleValidationException(ex: ValidationException): ResponseEntity<ApiValidation> =
    ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
      ApiValidation(
        violations =
          ex.violations.map {
            ApiValidation.Violation(fieldId = it.fieldId, message = it.message, metadata = it.metadata)
          }
      )
    )
}
