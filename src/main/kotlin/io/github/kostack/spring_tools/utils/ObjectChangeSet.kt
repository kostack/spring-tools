package io.github.kostack.spring_tools.utils

import kotlin.reflect.KProperty1

object ObjectChangeSet {
  data class Change(
    val property: String,
    val oldValue: Any?,
    val newValue: Any?
  )

  fun <T : Any> compare(
    original: T,
    updated: T,
    vararg properties: KProperty1<T, *>
  ): List<Change> =
    properties.mapNotNull { property ->
      val oldValue = property.get(original)
      val newValue = property.get(updated)
      if (oldValue == newValue) null else Change(property.name, oldValue, newValue)
    }
}
