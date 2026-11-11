# Spring Tools

A small Kotlin library of tools for Spring WebFlux applications:

- **Consistent JSON error responses** for validation failures and bad request input (auto-configured)
- **Argument resolver helpers** for reading path variables and query parameters in custom resolvers
- **HTTP helpers** for retrying transient failures and polling until a result is ready
- **Coroutine utilities** for reading the Reactor context and diffing objects

## Requirements

- Java 25
- Spring Boot 4 with Spring WebFlux
- Kotlin coroutines with Reactor support (`kotlinx-coroutines-reactor`)

## Installation

The library does not bring Spring or coroutines with it: your application provides them, at the versions its own
Spring Boot manages. Add the library next to them.

### Gradle Kotlin DSL

```kotlin
dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("io.github.kostack:spring-tools:<version>")
}
```

### Gradle Groovy DSL

```groovy
dependencies {
  implementation 'org.springframework.boot:spring-boot-starter-webflux'
  implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-reactor'
  implementation 'io.github.kostack:spring-tools:<version>'
}
```

### Maven

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
<dependency>
  <groupId>org.jetbrains.kotlinx</groupId>
  <artifactId>kotlinx-coroutines-reactor</artifactId>
</dependency>
<dependency>
  <groupId>io.github.kostack</groupId>
  <artifactId>spring-tools</artifactId>
  <version>VERSION</version>
</dependency>
```

> If either of these is missing, the build still succeeds but the application fails at runtime with
> `NoClassDefFoundError`. `retryHttpCall` also needs Reactor Netty, which the WebFlux starter includes by default.

The library is a Spring Boot auto-configuration module: once it is on the classpath, the error handlers are registered
automatically.

## Error responses

All errors are returned in the same `ApiValidation` format:

```json
{
  "message": "Validation Failed",
  "violations": [
    {
      "fieldId": "email",
      "message": "must not be blank",
      "metadata": {}
    }
  ]
}
```

### Domain validation

Throw `ValidationException` to return `400 BAD_REQUEST` with your own violations:

```kotlin
import io.github.kostack.spring_tools.dto.ApiValidation
import io.github.kostack.spring_tools.exception.ValidationException

fun validateCreateUser(request: CreateUserRequest) {
  val violations =
    buildList {
      if (request.email.isBlank()) {
        add(ApiValidation.Violation(fieldId = "email", message = "must not be blank"))
      }
      if (request.age < 18) {
        add(
          ApiValidation.Violation(
            fieldId = "age",
            message = "must be greater than or equal to 18",
            metadata = mapOf("minimum" to 18)
          )
        )
      }
    }

  if (violations.isNotEmpty()) {
    throw ValidationException(violations)
  }
}
```

Response:

```json
{
  "message": "Validation Failed",
  "violations": [
    { "fieldId": "email", "message": "must not be blank", "metadata": {} },
    { "fieldId": "age", "message": "must be greater than or equal to 18", "metadata": { "minimum": 18 } }
  ]
}
```

### Bad request input

`ServerWebInputExceptionHandler` turns every `ServerWebInputException` (invalid body, parameters or path variables)
into an `ApiValidation` response, so clients always get the same format:

| Cause                                                     | `fieldId`                           | Message                                        |
|-----------------------------------------------------------|-------------------------------------|------------------------------------------------|
| Bean validation (`@Valid`) failure                        | field, or object name for class-level constraints | constraint message               |
| Unknown enum value in the body                            | full path, e.g. `roles[0]`          | `Invalid value 'x'. Valid values: …`           |
| Other wrongly formatted body value                        | full path                           | `Invalid value 'abc', expected Integer`        |
| Missing or mismatched body value (e.g. non-null field)    | full path                           | `Invalid or missing value, expected String`    |
| Malformed JSON                                            | `body`                              | `Malformed request body`                       |
| Missing query parameter, header or path variable          | its name                            | `Required query parameter is missing`          |
| Parameter of the wrong type                               | parameter name                      | `Invalid value 'abc', expected Integer`        |
| Anything else                                             | parameter name, or `body`           | Spring's error reason                          |

Enum errors list the values clients can actually send, honouring `@JsonValue` and `@JsonProperty`:

```json
{
  "message": "Validation Failed",
  "violations": [
    {
      "fieldId": "address.status",
      "message": "Invalid value 'disabled'. Valid values: active, inactive",
      "metadata": {}
    }
  ]
}
```

## Argument resolvers

`MethodArgumentResolver` adds helpers for reading request values inside your own `HandlerMethodArgumentResolver`, and
`@ResolvedArgument` marks the controller parameters it should fill. Declare the resolver as a bean and Spring Boot
registers it with WebFlux automatically:

```kotlin
import io.github.kostack.spring_tools.controller.resolver.MethodArgumentResolver
import io.github.kostack.spring_tools.controller.resolver.ResolvedArgument
import kotlinx.coroutines.reactor.mono

@Component
class UserArgumentResolver(
  private val users: UserRepository
) : HandlerMethodArgumentResolver,
  MethodArgumentResolver {
  override fun supportsParameter(parameter: MethodParameter): Boolean =
    parameter.hasParameterAnnotation(ResolvedArgument::class.java) && parameter.parameterType == User::class.java

  override fun resolveArgument(
    parameter: MethodParameter,
    bindingContext: BindingContext,
    exchange: ServerWebExchange
  ): Mono<Any> =
    mono {
      val id = getPathParameter<String>("id", exchange)
      val include = getRequestParameterOrNull("include", exchange)
      users.findById(id, include)
    }
}

@RestController
class UserController {
  @GetMapping("/users/{id}")
  suspend fun findUser(@ResolvedArgument user: User): User = user
}
```

| Function                    | Returns                         | When missing                                   |
|-----------------------------|---------------------------------|------------------------------------------------|
| `getPathParameter<T>`       | the path variable               | `IllegalArgumentException` (500)               |
| `getRequestParameter`       | first value of a query parameter | `ServerWebInputException` (400)               |
| `getRequestParameterOrNull` | first value of a query parameter | `null`                                        |

## HTTP helpers

### Retrying transient failures

`retryHttpCall` retries a call on `429`, `502`, `503` and `504` responses, request transport errors, connection resets
and premature closes, with exponential backoff. Any other error is rethrown immediately, and coroutine cancellation is
never retried.

```kotlin
import io.github.kostack.spring_tools.http.retryHttpCall

val user =
  retryHttpCall(maxRetries = 5) {
    webClient.get().uri("/users/{id}", id).retrieve().awaitBody<User>()
  }
```

| Parameter               | Default  | Description                                                          |
|-------------------------|----------|----------------------------------------------------------------------|
| `initialBackoff`        | `500ms`  | Wait before the first retry                                          |
| `backoffMultiplier`     | `1.5`    | Factor applied to the wait after each retry                          |
| `maxBackoff`            | `1m`     | Upper limit for any wait, including `Retry-After`                    |
| `maxRetries`            | `3`      | Retries after the first attempt                                      |
| `defaultRateLimitDelay` | `150ms`  | Wait after a `429` without a `Retry-After` header (in seconds)       |

A `429` waits for the `Retry-After` header and does not advance the backoff.

> Transport errors are retried for every HTTP method. For non-idempotent requests (e.g. `POST`), the server may already
> have processed the request, so only wrap calls that are safe to repeat.

### Polling until done

`pollHttpCall` calls `supplier` until `isDone` returns `true`, waiting `delay` between attempts, and throws
`TimeoutCancellationException` if `timeout` is reached first:

```kotlin
import io.github.kostack.spring_tools.http.pollHttpCall
import java.time.Duration

val job =
  pollHttpCall(
    delay = Duration.ofSeconds(2),
    timeout = Duration.ofMinutes(1),
    isDone = { it.status == JobStatus.DONE }
  ) {
    jobClient.getJob(jobId)
  }
```

## Utilities

### Reactor context

`ReactorContextUtils` reads the Reactor context that WebFlux propagates into `suspend` functions, e.g. values written
by a `WebFilter` with `contextWrite`:

```kotlin
import io.github.kostack.spring_tools.utils.ReactorContextUtils

val tenantId: String = ReactorContextUtils.get("tenantId")        // throws if missing or of another type
val traceId: String? = ReactorContextUtils.getOrNull("traceId")   // null if missing
val hasUser: Boolean = ReactorContextUtils.has("userId")
```

### Deep clone

`deepClone` copies an object by serializing it with Jackson and reading it back. Generic types such as
`List<Address>` keep their element type. Only what the mapper serializes is copied: properties it ignores get their
default values.

```kotlin
import io.github.kostack.spring_tools.utils.deepClone

val copy: List<Address> = deepClone(addresses, objectMapper)
```

### Object change sets

`ObjectChangeSet.compare` lists the properties that differ between two instances:

```kotlin
import io.github.kostack.spring_tools.utils.ObjectChangeSet

val changes = ObjectChangeSet.compare(original, updated, User::email, User::name)
// [Change(property=email, oldValue=old@example.com, newValue=new@example.com)]
```

## Customization

Each handler backs off when your application defines a bean of the same type:

```kotlin
@Configuration(proxyBeanMethods = false)
class SpringToolsConfiguration {
  @Bean
  fun serverWebInputExceptionHandler(): ServerWebInputExceptionHandler = MyServerWebInputExceptionHandler()
}
```

For different response behavior, define your own `@RestControllerAdvice` for the exception type you want to handle.
`ServerWebInputExceptionHandler` runs at `Ordered.LOWEST_PRECEDENCE`, so give your advice an `@Order` with a higher
precedence (e.g. `@Order(0)`) to make sure it wins.

### Configuration properties

| Property                                         | Default | Description                                     |
|--------------------------------------------------|---------|-------------------------------------------------|
| `kostack.spring-tools.web-input-handler.enabled` | `true`  | Register `ServerWebInputExceptionHandler`       |

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
