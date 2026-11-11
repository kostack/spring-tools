package io.github.kostack.spring_tools.http

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Uses the coroutine test scheduler, so delays and timeouts run in virtual time. */
class PollHttpCallTest {
  private val delay = Duration.ofSeconds(1)
  private val timeout = Duration.ofSeconds(10)

  @Test
  fun `returns first result immediately when already done`() =
    runTest {
      var calls = 0

      val result = pollHttpCall(delay, timeout, isDone = { true }) { ++calls }

      assertEquals(1, result)
      assertEquals(1, calls)
      assertEquals(0, currentTime)
    }

  @Test
  fun `polls until done and returns the matching result`() =
    runTest {
      var calls = 0

      val result = pollHttpCall(delay, timeout, isDone = { it == 3 }) { ++calls }

      assertEquals(3, result)
      assertEquals(3, calls)
    }

  @Test
  fun `waits the configured delay between attempts`() =
    runTest {
      val callTimes = mutableListOf<Long>()

      pollHttpCall(delay, timeout, isDone = { callTimes.size == 3 }) { callTimes += currentTime }

      assertEquals(listOf(0L, 1_000L, 2_000L), callTimes)
    }

  @Test
  fun `throws TimeoutCancellationException when never done`() =
    runTest {
      var calls = 0

      assertFailsWith<TimeoutCancellationException> {
        pollHttpCall(delay, Duration.ofMillis(3_500), isDone = { false }) { ++calls }
      }

      // Calls at 0, 1000, 2000 and 3000 ms; the timeout fires during the next delay.
      assertEquals(4, calls)
      assertEquals(3_500, currentTime)
    }

  @Test
  fun `timeout includes time spent in the supplier`() =
    runTest {
      assertFailsWith<TimeoutCancellationException> {
        pollHttpCall(delay, Duration.ofSeconds(2), isDone = { false }) { delay(5_000) }
      }

      assertEquals(2_000, currentTime)
    }

  @Test
  fun `supports suspending isDone`() =
    runTest {
      var calls = 0

      val result =
        pollHttpCall(
          delay,
          timeout,
          isDone = {
            delay(10)
            it >= 2
          }
        ) { ++calls }

      assertEquals(2, result)
    }

  @Test
  fun `supports nullable results`() =
    runTest {
      val result = pollHttpCall<String?>(delay, timeout, isDone = { it == null }) { null }

      assertNull(result)
    }

  @Test
  fun `propagates supplier exceptions without retrying`() =
    runTest {
      var calls = 0

      val error =
        assertFailsWith<IllegalStateException> {
          pollHttpCall<Int>(delay, timeout, isDone = { false }) {
            calls++
            error("boom")
          }
        }

      assertEquals("boom", error.message)
      assertEquals(1, calls)
    }

  @Test
  fun `rejects non positive delay`() =
    runTest {
      assertInvalid("delay must be positive, was PT0S") {
        pollHttpCall(Duration.ZERO, timeout, isDone = { true }) { 1 }
      }
      assertInvalid("delay must be positive, was PT-1S") {
        pollHttpCall(Duration.ofSeconds(-1), timeout, isDone = { true }) { 1 }
      }
    }

  @Test
  fun `rejects non positive timeout`() =
    runTest {
      assertInvalid("timeout must be positive, was PT0S") {
        pollHttpCall(delay, Duration.ZERO, isDone = { true }) { 1 }
      }
    }

  private suspend fun TestScope.assertInvalid(
    message: String,
    block: suspend () -> Unit
  ) {
    val error = assertFailsWith<IllegalArgumentException> { block() }
    assertEquals(message, error.message)
  }
}
