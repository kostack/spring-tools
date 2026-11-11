package io.github.kostack.spring_tools.http

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.time.Duration
import kotlin.time.toKotlinDuration

suspend fun <T> pollHttpCall(
  delay: Duration,
  timeout: Duration,
  isDone: suspend (T) -> Boolean,
  supplier: suspend () -> T
): T {
  require(delay.isPositive) { "delay must be positive, was $delay" }
  require(timeout.isPositive) { "timeout must be positive, was $timeout" }

  val pause = delay.toKotlinDuration()
  return withTimeout(timeout.toKotlinDuration()) {
    var result = supplier()
    while (!isDone(result)) {
      delay(pause)
      result = supplier()
    }
    result
  }
}
