package io.github.kostack.spring_tools.handler

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import io.github.kostack.spring_tools.dto.ApiValidation
import org.slf4j.LoggerFactory
import org.springframework.beans.TypeMismatchException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.MissingRequestValueException
import org.springframework.web.server.ServerWebInputException
import tools.jackson.core.JacksonException
import tools.jackson.core.exc.StreamReadException
import tools.jackson.databind.exc.InvalidFormatException
import tools.jackson.databind.exc.MismatchedInputException
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Field
import java.lang.reflect.Method

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class ServerWebInputExceptionHandler {
  @ExceptionHandler(ServerWebInputException::class)
  fun handleServerWebInputException(ex: ServerWebInputException): ResponseEntity<ApiValidation> {
    log.debug("Invalid web input", ex)
    return ResponseEntity.status(ex.statusCode).body(ApiValidation(violations = violationsOf(ex)))
  }

  private fun violationsOf(ex: ServerWebInputException): List<ApiValidation.Violation> {
    if (ex is WebExchangeBindException) {
      return ex.bindingResult.allErrors.map { it.toViolation() }
    }

    val causes = generateSequence(ex.cause) { it.cause }.toList()

    causes.filterIsInstance<InvalidFormatException>().firstOrNull()?.let { return listOf(it.toViolation()) }
    causes.filterIsInstance<MismatchedInputException>().firstOrNull()?.let { return listOf(it.toViolation()) }
    causes.filterIsInstance<StreamReadException>().firstOrNull()?.let {
      return listOf(ApiValidation.Violation(fieldId = BODY_FIELD, message = "Malformed request body"))
    }

    if (ex is MissingRequestValueException) {
      return listOf(ApiValidation.Violation(fieldId = ex.name, message = "Required ${ex.label} is missing"))
    }

    causes.filterIsInstance<TypeMismatchException>().firstOrNull()?.let { mismatch ->
      val field = mismatch.propertyName ?: ex.methodParameter?.parameterName ?: UNKNOWN_FIELD
      val expected = mismatch.requiredType?.simpleName
      val message = "Invalid value '${mismatch.value}'" + (expected?.let { ", expected $it" } ?: "")
      return listOf(ApiValidation.Violation(fieldId = field, message = message))
    }

    return listOf(
      ApiValidation.Violation(
        fieldId = ex.methodParameter?.parameterName ?: BODY_FIELD,
        message = ex.reason ?: "Invalid request"
      )
    )
  }

  private fun ObjectError.toViolation(): ApiValidation.Violation =
    ApiValidation.Violation(
      // Errors not bound to a field (class-level constraints) are reported against the object.
      fieldId = if (this is FieldError) field else objectName,
      message = defaultMessage ?: UNKNOWN_ERROR
    )

  private fun InvalidFormatException.toViolation(): ApiValidation.Violation {
    val target = targetType
    val message =
      if (target != null && target.isEnum) {
        "Invalid value '$value'. Valid values: ${jsonNames(target).joinToString()}"
      } else {
        "Invalid value '$value'" + (target?.let { ", expected ${it.simpleName}" } ?: "")
      }
    return ApiValidation.Violation(fieldId = path.toFieldPath(), message = message)
  }

  private fun MismatchedInputException.toViolation(): ApiValidation.Violation =
    ApiValidation.Violation(
      fieldId = path.toFieldPath(),
      message = "Invalid or missing value" + (targetType?.let { ", expected ${it.simpleName}" } ?: "")
    )

  companion object {
    private val log = LoggerFactory.getLogger(ServerWebInputExceptionHandler::class.java)
    private const val UNKNOWN_ERROR = "unknown error"
    private const val UNKNOWN_FIELD = "unknown"
    private const val BODY_FIELD = "body"

    /** Builds a path such as `address.type` or `roles[0]` from a Jackson reference chain. */
    private fun List<JacksonException.Reference>.toFieldPath(): String =
      buildString {
        for (ref in this@toFieldPath) {
          val name = ref.propertyName
          when {
            name != null -> {
              if (isNotEmpty()) append('.')
              append(name)
            }

            ref.index >= 0 -> {
              append('[').append(ref.index).append(']')
            }
          }
        }
      }.ifEmpty { UNKNOWN_FIELD }

    /**
     * The values a client may send for [enumType], honouring `@JsonValue` and `@JsonProperty`
     * so the message lists the JSON representation rather than Kotlin constant names.
     */
    private fun jsonNames(enumType: Class<*>): List<String> {
      val constants = enumType.enumConstants.map { it as Enum<*> }
      val jsonValue = jsonValueAccessor(enumType)
      return constants.map { constant ->
        jsonValue?.let { accessor -> runCatching { accessor.read(constant) }.getOrNull()?.toString() }
          ?: enumType
            .getField(constant.name)
            .getAnnotation(JsonProperty::class.java)
            ?.value
            ?.takeIf { it.isNotEmpty() }
          ?: constant.name
      }
    }

    private fun jsonValueAccessor(enumType: Class<*>): AccessibleObject? =
      (enumType.declaredMethods.asSequence() + enumType.declaredFields.asSequence())
        .map { it as AccessibleObject }
        .firstOrNull { it.getAnnotation(JsonValue::class.java)?.value == true }
        ?.also { runCatching { it.isAccessible = true } }

    private fun AccessibleObject.read(target: Any): Any? =
      when (this) {
        is Method -> invoke(target)
        is Field -> get(target)
        else -> null
      }
  }
}
