package io.github.kostack.spring_tools.utils

import com.fasterxml.jackson.annotation.JsonIgnore
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull

class DeepCloneTest {
  private val mapper = jacksonObjectMapper()

  data class Address(
    val street: String,
    val tags: MutableList<String> = mutableListOf()
  )

  data class User(
    val name: String,
    val age: Int?,
    val address: Address,
    val attributes: MutableMap<String, String> = mutableMapOf()
  )

  data class WithIgnored(
    val kept: String,
    @get:JsonIgnore val ignored: String = "default"
  )

  private val user =
    User(
      name = "Ada",
      age = 36,
      address = Address("Main St", mutableListOf("home")),
      attributes = mutableMapOf("role" to "admin")
    )

  @Test
  fun `clone is equal to the original but a different instance`() {
    val clone = deepClone(user, mapper)

    assertEquals(user, clone)
    assertNotSame(user, clone)
  }

  @Test
  fun `nested objects and collections are copied, not shared`() {
    val clone = deepClone(user, mapper)

    assertNotSame(user.address, clone.address)
    assertNotSame(user.address.tags, clone.address.tags)
    assertNotSame(user.attributes, clone.attributes)
  }

  @Test
  fun `mutating the clone does not affect the original`() {
    val clone = deepClone(user, mapper)

    clone.address.tags += "work"
    clone.attributes["role"] = "guest"

    assertEquals(listOf("home"), user.address.tags)
    assertEquals("admin", user.attributes["role"])
  }

  @Test
  fun `null properties are preserved`() {
    val withoutAge = user.copy(age = null)

    assertNull(deepClone(withoutAge, mapper).age)
  }

  @Test
  fun `null input returns null`() {
    assertNull(deepClone<User?>(null, mapper))
  }

  @Test
  fun `simple values are cloned`() {
    assertEquals("text", deepClone("text", mapper))
    assertEquals(42, deepClone(42, mapper))
  }

  @Test
  fun `list elements keep their type`() {
    val list = listOf(Address("Main St", mutableListOf("home")), Address("Second St"))

    val clone: List<Address> = deepClone(list, mapper)

    assertEquals(list, clone)
    assertIs<Address>(clone[0])
    assertNotSame(list[0], clone[0])
  }

  @Test
  fun `map values keep their type`() {
    val map = mapOf("ada" to user)

    val clone: Map<String, User> = deepClone(map, mapper)

    assertEquals(map, clone)
    assertIs<User>(clone.getValue("ada"))
    assertNotSame(map.getValue("ada"), clone.getValue("ada"))
  }

  @Test
  fun `nested generic types keep their type`() {
    val nested = mapOf("group" to listOf(Address("Main St")))

    val clone: Map<String, List<Address>> = deepClone(nested, mapper)

    assertEquals(nested, clone)
    assertIs<Address>(clone.getValue("group")[0])
  }

  @Test
  fun `set elements keep their type`() {
    val set = setOf(Address("Main St"), Address("Second St"))

    val clone: Set<Address> = deepClone(set, mapper)

    assertEquals(set, clone)
    clone.forEach { assertIs<Address>(it) }
  }

  @Test
  fun `generic class keeps its type argument`() {
    val box = Box(Address("Main St"))

    val clone: Box<Address> = deepClone(box, mapper)

    assertEquals(box, clone)
    assertIs<Address>(clone.value)
  }

  data class Box<T>(
    val value: T
  )

  @Test
  fun `properties ignored by Jackson are not copied`() {
    val clone = deepClone(WithIgnored(kept = "a", ignored = "b"), mapper)

    assertEquals("a", clone.kept)
    assertEquals("default", clone.ignored)
  }
}
