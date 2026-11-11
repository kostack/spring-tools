package io.github.kostack.spring_tools.http

import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.netty.http.client.PrematureCloseException
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.toKotlinDuration

private val log = LoggerFactory.getLogger("retryHttpCall")

private val RETRYABLE_STATUSES =
  setOf(
    HttpStatus.TOO_MANY_REQUESTS.value(),
    HttpStatus.BAD_GATEWAY.value(),
    HttpStatus.SERVICE_UNAVAILABLE.value(),
    HttpStatus.GATEWAY_TIMEOUT.value()
  )

private val CONNECTION_RESET_MARKERS = listOf("Connection reset by peer", "error(-104)", "io_uring read")

suspend fun <T> retryHttpCall(
  initialBackoff: Duration = Duration.ofMillis(500),
  defaultRateLimitDelay: Duration = Duration.ofMillis(150),
  maxBackoff: Duration = Duration.ofMinutes(1),
  maxRetries: Long = 3,
  backoffMultiplier: Double = 1.5,
  block: suspend () -> T
): T {
  require(maxRetries >= 0) { "maxRetries must be >= 0, was $maxRetries" }
  require(backoffMultiplier >= 1.0) { "backoffMultiplier must be >= 1.0, was $backoffMultiplier" }
  require(!initialBackoff.isNegative) { "initialBackoff must not be negative, was $initialBackoff" }
  require(
    !defaultRateLimitDelay.isNegative
  ) { "defaultRateLimitDelay must not be negative, was $defaultRateLimitDelay" }
  require(maxBackoff >= initialBackoff) { "maxBackoff ($maxBackoff) must be >= initialBackoff ($initialBackoff)" }

  var retries = 0L
  var backoff = initialBackoff

  while (true) {
    try {
      return block()
    } catch (error: CancellationException) {
      throw error
    } catch (error: Throwable) {
      if (!error.isRetryableHttpFailure()) throw error

      if (retries >= maxRetries) {
        log.warn("Retries exhausted after {} retries", maxRetries)
        throw error
      }

      val rateLimited = error.isTooManyRequests()
      val wait =
        if (rateLimited) {
          error.retryAfter()?.coerceAtMost(maxBackoff) ?: defaultRateLimitDelay
        } else {
          backoff
        }

      retries++
      log.warn(
        "Retry attempt {}/{} in {} ms due to {}: {}",
        retries,
        maxRetries,
        wait.toMillis(),
        error::class.simpleName,
        error.message
      )

      delay(wait.toKotlinDuration())

      if (!rateLimited) {
        backoff = backoff.multipliedBy(backoffMultiplier).coerceAtMost(maxBackoff)
      }
    }
  }
}

private fun Duration.multipliedBy(factor: Double): Duration = Duration.ofMillis((toMillis() * factor).toLong())

private fun Throwable.isTooManyRequests(): Boolean =
  this is WebClientResponseException && statusCode.value() == HttpStatus.TOO_MANY_REQUESTS.value()

private fun Throwable.retryAfter(): Duration? =
  (this as? WebClientResponseException)
    ?.headers
    ?.getFirst(HttpHeaders.RETRY_AFTER)
    ?.trim()
    ?.toLongOrNull()
    ?.takeIf { it >= 0 }
    ?.let(Duration::ofSeconds)

private fun Throwable.isRetryableHttpFailure(): Boolean =
  isRetryableHttpStatus() ||
    this is WebClientRequestException ||
    isConnectionReset() ||
    isPrematureClose()

private fun Throwable.isRetryableHttpStatus(): Boolean =
  this is WebClientResponseException && statusCode.value() in RETRYABLE_STATUSES

private fun Throwable.isPrematureClose(): Boolean = anyCause { it is PrematureCloseException }

private fun Throwable.isConnectionReset(): Boolean =
  anyCause { cause ->
    val message = cause.message ?: return@anyCause false
    CONNECTION_RESET_MARKERS.any { message.contains(it, ignoreCase = true) }
  }

private fun Throwable.anyCause(predicate: (Throwable) -> Boolean): Boolean {
  val seen = mutableSetOf<Throwable>()
  var current: Throwable? = this

  while (current != null && seen.add(current)) {
    if (predicate(current)) return true
    current = current.cause
  }

  return false
}
