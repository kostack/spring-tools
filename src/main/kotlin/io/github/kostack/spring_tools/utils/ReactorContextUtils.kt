package io.github.kostack.spring_tools.utils

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.reactor.ReactorContext
import reactor.util.context.ContextView

object ReactorContextUtils {
  suspend fun reactorContext(): ContextView? = currentCoroutineContext()[ReactorContext]?.context

  suspend fun has(key: Any): Boolean = reactorContext()?.hasKey(key) == true

  suspend inline fun <reified T : Any> getOrNull(key: Any): T? {
    val value = reactorContext()?.getOrDefault<Any>(key, null) ?: return null
    return value as? T
      ?: error("Context key '$key' is ${value::class.qualifiedName}, expected ${T::class.qualifiedName}")
  }

  suspend inline fun <reified T : Any> get(key: Any): T {
    checkNotNull(reactorContext()) { "No Reactor context available" }
    check(has(key)) { "Missing context key: $key" }
    return getOrNull(key)!!
  }
}
