package io.github.kostack.spring_tools.exception.handler

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import io.github.kostack.spring_tools.handler.ServerWebInputExceptionHandler
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.TypeMismatchException
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.MissingRequestValueException
import org.springframework.web.server.ServerWebInputException
import tools.jackson.core.JacksonException
import tools.jackson.core.exc.StreamReadException
import tools.jackson.databind.exc.InvalidFormatException
import tools.jackson.databind.exc.MismatchedInputException

class ServerWebInputExceptionHandlerTest {
  private val handler = ServerWebInputExceptionHandler()

  @Test
  fun `handleServerWebInputException returns BAD_REQUEST with violations for WebExchangeBindException`() =
    runTest {
      val bindingResult = BeanPropertyBindingResult(TestRequest(), "testRequest")
      bindingResult.addError(
        FieldError("testRequest", "email", "must not be blank")
      )
      bindingResult.addError(
        FieldError("testRequest", "age", "must be greater than 18")
      )

      val exception = WebExchangeBindException(methodParameter(), bindingResult)

      val response = handler.handleServerWebInputException(exception)

      assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
      assertEquals(2, response.body?.violations?.size)

      val first = response.body!!.violations[0]
      assertEquals("email", first.fieldId)
      assertEquals("must not be blank", first.message)

      val second = response.body!!.violations[1]
      assertEquals("age", second.fieldId)
      assertEquals("must be greater than 18", second.message)
    }

  @Test
  fun `handleServerWebInputException uses unknown error when field error message is null`() =
    runTest {
      val bindingResult = BeanPropertyBindingResult(TestRequest(), "testRequest")
      bindingResult.addError(
        FieldError("testRequest", "email", null, false, null, null, null)
      )

      val exception = WebExchangeBindException(methodParameter(), bindingResult)

      val response = handler.handleServerWebInputException(exception)

      assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
      assertEquals(1, response.body?.violations?.size)
      assertEquals("email", response.body!!.violations[0].fieldId)
      assertEquals("unknown error", response.body!!.violations[0].message)
    }

  @Test
  fun `handleServerWebInputException falls back to reason for other input errors`() =
    runTest {
      val exception = ServerWebInputException("Invalid input")

      val response = handler.handleServerWebInputException(exception)

      assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
      assertEquals("body", response.body!!.violations[0].fieldId)
      assertEquals("Invalid input", response.body!!.violations[0].message)
    }

  @Test
  fun `handleServerWebInputException reports missing request value`() =
    runTest {
      val exception = MissingRequestValueException("page", Int::class.java, "query parameter", methodParameter())

      val response = handler.handleServerWebInputException(exception)

      assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
      assertEquals("page", response.body!!.violations[0].fieldId)
      assertEquals("Required query parameter is missing", response.body!!.violations[0].message)
    }

  @Test
  fun `handleServerWebInputException reports parameter type mismatch`() =
    runTest {
      val parameter = methodParameter().apply { initParameterNameDiscovery(DefaultParameterNameDiscoverer()) }
      val exception =
        ServerWebInputException("Type mismatch", parameter, TypeMismatchException("abc", Integer::class.java))

      val response = handler.handleServerWebInputException(exception)

      assertEquals("request", response.body!!.violations[0].fieldId)
      assertEquals("Invalid value 'abc', expected Integer", response.body!!.violations[0].message)
    }

  @Test
  fun `handleServerWebInputException reports missing or mismatched body value`() =
    runTest {
      val mismatch = MismatchedInputException.from(null, String::class.java, "missing")
      mismatch.prependPath(JacksonException.Reference(TestRequest::class.java, "email"))
      val exception = ServerWebInputException("Invalid input", null, mismatch)

      val response = handler.handleServerWebInputException(exception)

      assertEquals("email", response.body!!.violations[0].fieldId)
      assertEquals("Invalid or missing value, expected String", response.body!!.violations[0].message)
    }

  @Test
  fun `handleServerWebInputException reports malformed body`() =
    runTest {
      val exception = ServerWebInputException("Invalid input", null, StreamReadException(null, "bad json"))

      val response = handler.handleServerWebInputException(exception)

      assertEquals("body", response.body!!.violations[0].fieldId)
      assertEquals("Malformed request body", response.body!!.violations[0].message)
    }

  @Test
  fun `handleServerWebInputException returns BAD_REQUEST for invalid enum value`() =
    runTest {
      val exception =
        ServerWebInputException(
          "Invalid input",
          null,
          invalidFormatException(
            targetType = Status::class.java,
            value = "disabled",
            propertyName = "status"
          )
        )

      val response = handler.handleServerWebInputException(exception)

      assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
      assertEquals(1, response.body?.violations?.size)

      val violation = response.body!!.violations[0]
      assertEquals("status", violation.fieldId)
      assertEquals("Invalid value 'disabled'. Valid values: ACTIVE, INACTIVE", violation.message)
    }

  @Test
  fun `handleServerWebInputException uses unknown field when invalid enum path is empty`() =
    runTest {
      val exception =
        ServerWebInputException(
          "Invalid input",
          null,
          invalidFormatException(targetType = Status::class.java, value = "disabled")
        )

      val response = handler.handleServerWebInputException(exception)

      assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
      assertEquals("unknown", response.body!!.violations[0].fieldId)
    }

  @Test
  fun `handleServerWebInputException finds InvalidFormatException in nested cause chain`() =
    runTest {
      val exception =
        ServerWebInputException(
          "Invalid input",
          null,
          RuntimeException(
            "wrapper",
            invalidFormatException(
              targetType = Status::class.java,
              value = "disabled",
              propertyName = "status"
            )
          )
        )

      val response = handler.handleServerWebInputException(exception)

      assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
      assertEquals("status", response.body!!.violations[0].fieldId)
    }

  @Test
  fun `handleServerWebInputException reports expected type when invalid format target is not enum`() =
    runTest {
      val exception =
        ServerWebInputException(
          "Invalid input",
          null,
          invalidFormatException(targetType = Integer::class.java, value = "abc", propertyName = "age")
        )

      val response = handler.handleServerWebInputException(exception)

      assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
      assertEquals("age", response.body!!.violations[0].fieldId)
      assertEquals("Invalid value 'abc', expected Integer", response.body!!.violations[0].message)
    }

  @Test
  fun `handleServerWebInputException returns empty violations when no field errors exist`() =
    runTest {
      val bindingResult = BeanPropertyBindingResult(TestRequest(), "testRequest")
      val exception = WebExchangeBindException(methodParameter(), bindingResult)

      val response = handler.handleServerWebInputException(exception)

      assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
      assertTrue(response.body!!.violations.isEmpty())
    }

  @Test
  fun `handleServerWebInputException includes class level errors`() =
    runTest {
      val bindingResult = BeanPropertyBindingResult(TestRequest(), "testRequest")
      bindingResult.addError(ObjectError("testRequest", "passwords must match"))
      val exception = WebExchangeBindException(methodParameter(), bindingResult)

      val response = handler.handleServerWebInputException(exception)

      assertEquals(1, response.body!!.violations.size)
      assertEquals("testRequest", response.body!!.violations[0].fieldId)
      assertEquals("passwords must match", response.body!!.violations[0].message)
    }

  @Test
  fun `handleServerWebInputException reports full path for nested and indexed enum values`() =
    runTest {
      val invalid = InvalidFormatException(null, "Invalid value", "BAD", Status::class.java)
      invalid.prependPath(JacksonException.Reference(listOf<Any>(), 1))
      invalid.prependPath(JacksonException.Reference(TestRequest::class.java, "statuses"))
      invalid.prependPath(JacksonException.Reference(TestRequest::class.java, "nested"))
      val exception = ServerWebInputException("Invalid input", null, invalid)

      val response = handler.handleServerWebInputException(exception)

      assertEquals("nested.statuses[1]", response.body!!.violations[0].fieldId)
    }

  @Test
  fun `handleServerWebInputException lists JsonProperty names for enum`() =
    runTest {
      val exception =
        ServerWebInputException(
          "Invalid input",
          null,
          invalidFormatException(PropertyStatus::class.java, "x", "status")
        )

      val response = handler.handleServerWebInputException(exception)

      assertEquals(
        "Invalid value 'x'. Valid values: in-progress, DONE",
        response.body!!.violations[0].message
      )
    }

  @Test
  fun `handleServerWebInputException lists JsonValue values for enum`() =
    runTest {
      val exception =
        ServerWebInputException(
          "Invalid input",
          null,
          invalidFormatException(ValueStatus::class.java, "x", "status")
        )

      val response = handler.handleServerWebInputException(exception)

      assertEquals(
        "Invalid value 'x'. Valid values: open, closed",
        response.body!!.violations[0].message
      )
    }

  @Suppress("unused")
  private enum class PropertyStatus {
    @JsonProperty("in-progress")
    IN_PROGRESS,
    DONE
  }

  @Suppress("unused")
  private enum class ValueStatus(
    @get:JsonValue val code: String
  ) {
    OPEN("open"),
    CLOSED("closed")
  }

  private fun methodParameter(): MethodParameter {
    val method = TestController::class.java.getDeclaredMethod("handle", TestRequest::class.java)
    return MethodParameter(method, 0)
  }

  private fun invalidFormatException(
    targetType: Class<*>,
    value: Any,
    propertyName: String? = null
  ): InvalidFormatException {
    val exception = InvalidFormatException(null, "Invalid value", value, targetType)
    if (propertyName != null) {
      exception.prependPath(JacksonException.Reference(TestRequest::class.java, propertyName))
    }
    return exception
  }

  private class TestController {
    @Suppress("unused")
    fun handle(request: TestRequest) = Unit
  }

  private enum class Status {
    ACTIVE,
    INACTIVE
  }

  private data class TestRequest(
    val email: String? = null,
    val age: Int? = null,
    val status: Status? = null
  )
}
