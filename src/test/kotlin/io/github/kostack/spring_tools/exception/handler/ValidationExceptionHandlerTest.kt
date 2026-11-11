package io.github.kostack.spring_tools.exception.handler

import io.github.kostack.spring_tools.dto.ApiValidation
import io.github.kostack.spring_tools.exception.ValidationException
import io.github.kostack.spring_tools.handler.ValidationExceptionHandler
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class ValidationExceptionHandlerTest {
  private val handler = ValidationExceptionHandler()

  @Test
  fun `handleValidationException returns BAD_REQUEST with mapped errors`() =
    runTest {
      val errors =
        listOf(
          ApiValidation.Violation(
            fieldId = "email",
            message = "invalid email",
            metadata = mapOf("format" to "wrong")
          ),
          ApiValidation.Violation(
            fieldId = "age",
            message = "must be >= 18",
            metadata = emptyMap()
          )
        )

      val exception = ValidationException(errors)

      val response = handler.handleValidationException(exception)

      assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
      assertNotNull(response.body)

      val body = response.body!!
      assertEquals(2, body.violations.size)

      val first = body.violations[0]
      assertEquals("email", first.fieldId)
      assertEquals("invalid email", first.message)
      assertEquals(mapOf("format" to "wrong"), first.metadata)

      val second = body.violations[1]
      assertEquals("age", second.fieldId)
      assertEquals("must be >= 18", second.message)
      assertEquals(emptyMap<String, Any>(), second.metadata)
    }

  @Test
  fun `handleValidationException returns BAD_REQUEST with empty errors`() =
    runTest {
      val exception = ValidationException(emptyList())

      val response = handler.handleValidationException(exception)

      assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
      assertNotNull(response.body)
      assertTrue(response.body!!.violations.isEmpty())
    }

  @Test
  fun `handleValidationException preserves ApiError values`() =
    runTest {
      val errors =
        listOf(
          ApiValidation.Violation(
            fieldId = "name",
            message = "required",
            metadata = mapOf("required" to true)
          )
        )

      val exception = ValidationException(errors)

      val response = handler.handleValidationException(exception)

      assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
      assertEquals(errors, response.body!!.violations)
    }
}
