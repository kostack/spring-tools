package io.github.kostack.spring_tools.utils

import kotlinx.coroutines.reactor.asCoroutineContext
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import reactor.util.context.Context
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReactorContextUtilsTest {
  private suspend fun <R> withReactorContext(
    vararg entries: Pair<Any, Any>,
    block: suspend () -> R
  ): R {
    val context = entries.fold(Context.empty()) { ctx, (key, value) -> ctx.put(key, value) }
    return withContext(context.asCoroutineContext()) { block() }
  }

  // --- no Reactor context ---

  @Test
  fun `reactorContext returns null without Reactor context`() =
    runTest {
      assertNull(ReactorContextUtils.reactorContext())
    }

  @Test
  fun `has returns false without Reactor context`() =
    runTest {
      assertFalse(ReactorContextUtils.has("key"))
    }

  @Test
  fun `getOrNull returns null without Reactor context`() =
    runTest {
      assertNull(ReactorContextUtils.getOrNull<String>("key"))
    }

  @Test
  fun `get throws without Reactor context`() =
    runTest {
      val error = assertFailsWith<IllegalStateException> { ReactorContextUtils.get<String>("key") }
      assertEquals("No Reactor context available", error.message)
    }

  // --- key present ---

  @Test
  fun `reactorContext exposes the propagated context`() =
    runTest {
      withReactorContext("key" to "value") {
        val context = assertNotNull(ReactorContextUtils.reactorContext())
        assertEquals("value", context.get<String>("key"))
      }
    }

  @Test
  fun `has returns true for present key`() =
    runTest {
      withReactorContext("key" to "value") {
        assertTrue(ReactorContextUtils.has("key"))
      }
    }

  @Test
  fun `get and getOrNull return value for present key`() =
    runTest {
      withReactorContext("key" to "value") {
        assertEquals("value", ReactorContextUtils.get<String>("key"))
        assertEquals("value", ReactorContextUtils.getOrNull<String>("key"))
      }
    }

  @Test
  fun `get accepts a supertype of the stored value`() =
    runTest {
      withReactorContext("count" to 42) {
        assertEquals(42, ReactorContextUtils.get<Number>("count"))
      }
    }

  @Test
  fun `non string keys are supported`() =
    runTest {
      val key = Any()
      withReactorContext(key to "value") {
        assertTrue(ReactorContextUtils.has(key))
        assertEquals("value", ReactorContextUtils.get<String>(key))
      }
    }

  // --- key absent ---

  @Test
  fun `has returns false for absent key`() =
    runTest {
      withReactorContext("other" to "value") {
        assertFalse(ReactorContextUtils.has("key"))
      }
    }

  @Test
  fun `getOrNull returns null for absent key`() =
    runTest {
      withReactorContext("other" to "value") {
        assertNull(ReactorContextUtils.getOrNull<String>("key"))
      }
    }

  @Test
  fun `get throws for absent key`() =
    runTest {
      withReactorContext("other" to "value") {
        val error = assertFailsWith<IllegalStateException> { ReactorContextUtils.get<String>("key") }
        assertEquals("Missing context key: key", error.message)
      }
    }

  // --- wrong type ---

  @Test
  fun `getOrNull throws for value of wrong type`() =
    runTest {
      withReactorContext("key" to 42) {
        val error = assertFailsWith<IllegalStateException> { ReactorContextUtils.getOrNull<String>("key") }
        assertEquals("Context key 'key' is kotlin.Int, expected kotlin.String", error.message)
      }
    }

  @Test
  fun `get throws for value of wrong type`() =
    runTest {
      withReactorContext("key" to 42) {
        assertFailsWith<IllegalStateException> { ReactorContextUtils.get<String>("key") }
      }
    }

  // --- propagation from Reactor ---

  @Test
  fun `reads context written by a downstream Reactor subscriber`() {
    val result = mono { ReactorContextUtils.get<String>("userId") }.contextWrite(Context.of("userId", "u-1"))

    StepVerifier.create(result).expectNext("u-1").verifyComplete()
  }
}
