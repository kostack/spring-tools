package io.github.kostack.spring_tools.http

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.netty.http.client.PrematureCloseException
import java.io.IOException
import java.net.URI
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** Uses the coroutine test scheduler, so backoff delays run in virtual time. */
class RetryHttpCallTest {
  // --- success and retry decisions ---

  @Test
  fun `returns result without retrying on success`() =
    runTest {
      val attempts = Attempts()

      val result = retryHttpCall { attempts.record(this) { "ok" } }

      assertEquals("ok", result)
      assertEquals(listOf(0L), attempts.times)
    }

  @Test
  fun `retries retryable failure and returns eventual success`() =
    runTest {
      val attempts = Attempts()

      val result =
        retryHttpCall {
          attempts.record(this) {
            if (attempts.count < 3) throw responseError(HttpStatus.SERVICE_UNAVAILABLE)
            "ok"
          }
        }

      assertEquals("ok", result)
      assertEquals(3, attempts.count)
    }

  @Test
  fun `retries each retryable status`() =
    runTest {
      listOf(
        HttpStatus.TOO_MANY_REQUESTS,
        HttpStatus.BAD_GATEWAY,
        HttpStatus.SERVICE_UNAVAILABLE,
        HttpStatus.GATEWAY_TIMEOUT
      ).forEach { status ->
        val attempts = Attempts()

        retryHttpCall {
          attempts.record(this) { if (attempts.count == 1) throw responseError(status) }
        }

        assertEquals(2, attempts.count, "status $status should be retried")
      }
    }

  @Test
  fun `does not retry non retryable status`() =
    runTest {
      listOf(HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND, HttpStatus.INTERNAL_SERVER_ERROR).forEach { status ->
        val attempts = Attempts()
        val error = responseError(status)

        val thrown =
          assertFailsWith<WebClientResponseException> { retryHttpCall { attempts.record(this) { throw error } } }

        assertSame(error, thrown)
        assertEquals(1, attempts.count, "status $status should not be retried")
      }
    }

  @Test
  fun `does not retry non http exceptions`() =
    runTest {
      val attempts = Attempts()

      assertFailsWith<IllegalStateException> {
        retryHttpCall { attempts.record(this) { error("boom") } }
      }

      assertEquals(1, attempts.count)
    }

  @Test
  fun `retries transport failures`() =
    runTest {
      val failures =
        listOf(
          WebClientRequestException(IOException("refused"), HttpMethod.GET, URI("http://x"), HttpHeaders()),
          RuntimeException("wrapper", IOException("Connection reset by peer")),
          RuntimeException("recvAddress(..) failed with error(-104): Connection reset"),
          RuntimeException("wrapper", PrematureCloseException.TEST_EXCEPTION)
        )

      failures.forEach { failure ->
        val attempts = Attempts()

        retryHttpCall { attempts.record(this) { if (attempts.count == 1) throw failure } }

        assertEquals(2, attempts.count, "${failure.message} should be retried")
      }
    }

  @Test
  fun `stops walking cause chain on cycles`() =
    runTest {
      val first = RuntimeException("first")
      val second = RuntimeException("second", first)
      first.initCause(second)

      assertFailsWith<RuntimeException> { retryHttpCall { throw first } }
    }

  // --- exhaustion ---

  @Test
  fun `rethrows last error after max retries`() =
    runTest {
      val attempts = Attempts()
      val error = responseError(HttpStatus.BAD_GATEWAY)

      val thrown =
        assertFailsWith<WebClientResponseException> {
          retryHttpCall(maxRetries = 2) { attempts.record(this) { throw error } }
        }

      assertSame(error, thrown)
      assertEquals(3, attempts.count)
    }

  @Test
  fun `zero max retries makes a single attempt`() =
    runTest {
      val attempts = Attempts()

      assertFailsWith<WebClientResponseException> {
        retryHttpCall(maxRetries = 0) { attempts.record(this) { throw responseError(HttpStatus.BAD_GATEWAY) } }
      }

      assertEquals(1, attempts.count)
    }

  // --- backoff ---

  @Test
  fun `backs off exponentially`() =
    runTest {
      val attempts = Attempts()

      assertFailsWith<WebClientResponseException> {
        retryHttpCall(
          initialBackoff = Duration.ofMillis(100),
          backoffMultiplier = 2.0,
          maxRetries = 3
        ) { attempts.record(this) { throw responseError(HttpStatus.BAD_GATEWAY) } }
      }

      // Waits of 100, 200 and 400 ms.
      assertEquals(listOf(0L, 100L, 300L, 700L), attempts.times)
    }

  @Test
  fun `caps backoff at max backoff`() =
    runTest {
      val attempts = Attempts()

      assertFailsWith<WebClientResponseException> {
        retryHttpCall(
          initialBackoff = Duration.ofMillis(100),
          maxBackoff = Duration.ofMillis(150),
          backoffMultiplier = 2.0,
          maxRetries = 3
        ) { attempts.record(this) { throw responseError(HttpStatus.BAD_GATEWAY) } }
      }

      // Waits of 100, 150 and 150 ms.
      assertEquals(listOf(0L, 100L, 250L, 400L), attempts.times)
    }

  // --- rate limiting ---

  @Test
  fun `waits default rate limit delay on 429 without Retry-After`() =
    runTest {
      val attempts = Attempts()

      retryHttpCall(defaultRateLimitDelay = Duration.ofMillis(150)) {
        attempts.record(this) { if (attempts.count == 1) throw responseError(HttpStatus.TOO_MANY_REQUESTS) }
      }

      assertEquals(listOf(0L, 150L), attempts.times)
    }

  @Test
  fun `honours Retry-After seconds on 429`() =
    runTest {
      val attempts = Attempts()

      retryHttpCall {
        attempts.record(this) {
          if (attempts.count ==
            1
          ) {
            throw responseError(HttpStatus.TOO_MANY_REQUESTS, retryAfter = "2")
          }
        }
      }

      assertEquals(listOf(0L, 2_000L), attempts.times)
    }

  @Test
  fun `caps Retry-After at max backoff`() =
    runTest {
      val attempts = Attempts()

      retryHttpCall(maxBackoff = Duration.ofSeconds(5)) {
        attempts.record(this) {
          if (attempts.count == 1) throw responseError(HttpStatus.TOO_MANY_REQUESTS, retryAfter = "120")
        }
      }

      assertEquals(listOf(0L, 5_000L), attempts.times)
    }

  @Test
  fun `falls back to default rate limit delay for unparseable Retry-After`() =
    runTest {
      listOf("Wed, 21 Oct 2026 07:28:00 GMT", "-1", "soon").forEach { header ->
        val attempts = Attempts()

        retryHttpCall(defaultRateLimitDelay = Duration.ofMillis(150)) {
          attempts.record(this) {
            if (attempts.count == 1) throw responseError(HttpStatus.TOO_MANY_REQUESTS, retryAfter = header)
          }
        }

        assertEquals(150L, attempts.times[1] - attempts.times[0], "Retry-After '$header'")
      }
    }

  @Test
  fun `rate limiting does not advance the backoff`() =
    runTest {
      val attempts = Attempts()
      val failures =
        listOf(
          responseError(HttpStatus.TOO_MANY_REQUESTS),
          responseError(HttpStatus.TOO_MANY_REQUESTS),
          responseError(HttpStatus.BAD_GATEWAY)
        )

      retryHttpCall(
        initialBackoff = Duration.ofMillis(100),
        defaultRateLimitDelay = Duration.ofMillis(10),
        backoffMultiplier = 2.0
      ) { attempts.record(this) { failures.getOrNull(attempts.count - 1)?.let { throw it } } }

      // Two 10 ms rate limit waits, then the first (unadvanced) 100 ms backoff.
      assertEquals(listOf(0L, 10L, 20L, 120L), attempts.times)
    }

  // --- cancellation and validation ---

  @Test
  fun `does not retry cancellation`() =
    runTest {
      val attempts = Attempts()

      assertFailsWith<CancellationException> {
        retryHttpCall { attempts.record(this) { throw CancellationException("cancelled") } }
      }

      assertEquals(1, attempts.count)
    }

  @Test
  fun `rejects invalid arguments`() =
    runTest {
      assertInvalid("maxRetries must be >= 0, was -1") { retryHttpCall(maxRetries = -1) {} }
      assertInvalid("backoffMultiplier must be >= 1.0, was 0.5") { retryHttpCall(backoffMultiplier = 0.5) {} }
      assertInvalid("initialBackoff must not be negative, was PT-1S") {
        retryHttpCall(initialBackoff = Duration.ofSeconds(-1)) {}
      }
      assertInvalid("defaultRateLimitDelay must not be negative, was PT-1S") {
        retryHttpCall(defaultRateLimitDelay = Duration.ofSeconds(-1)) {}
      }
      assertInvalid("maxBackoff (PT1S) must be >= initialBackoff (PT2S)") {
        retryHttpCall(initialBackoff = Duration.ofSeconds(2), maxBackoff = Duration.ofSeconds(1)) {}
      }
    }

  // --- helpers ---

  /** Records the virtual time of each attempt. */
  private class Attempts {
    val times = mutableListOf<Long>()
    val count get() = times.size

    inline fun <T> record(
      scope: TestScope,
      block: () -> T
    ): T {
      times += scope.currentTime
      return block()
    }
  }

  private fun responseError(
    status: HttpStatus,
    retryAfter: String? = null
  ): WebClientResponseException {
    val headers = HttpHeaders()
    if (retryAfter != null) headers.set(HttpHeaders.RETRY_AFTER, retryAfter)
    return WebClientResponseException.create(status.value(), status.reasonPhrase, headers, ByteArray(0), null)
  }

  private suspend fun assertInvalid(
    message: String,
    block: suspend () -> Unit
  ) {
    val error = assertFailsWith<IllegalArgumentException> { block() }
    assertEquals(message, error.message)
  }
}
