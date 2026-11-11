package io.github.kostack.spring_tools.utils

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObjectChangeSetTest {
  private val original = Profile("Alice", "Al", 30, listOf("admin"))

  @Test
  fun `no selected properties produces no changes`() {
    val updated = original.copy(name = "Bob")

    assertTrue(ObjectChangeSet.compare(original, updated).isEmpty())
  }

  @Test
  fun `unchanged selected properties produce no changes`() {
    val changes =
      ObjectChangeSet.compare(
        original,
        original.copy(),
        Profile::name,
        Profile::nickname,
        Profile::age,
        Profile::roles
      )

    assertTrue(changes.isEmpty())
  }

  @Test
  fun `only changed selected properties are reported`() {
    val updated = original.copy(name = "Bob", age = 31)

    val changes = ObjectChangeSet.compare(original, updated, Profile::name, Profile::nickname)

    assertEquals(listOf(ObjectChangeSet.Change("name", "Alice", "Bob")), changes)
  }

  @Test
  fun `changes preserve selected property order and skip unchanged values`() {
    val updated = original.copy(name = "Bob", age = 31)

    val changes =
      ObjectChangeSet.compare(
        original,
        updated,
        Profile::age,
        Profile::nickname,
        Profile::name
      )

    assertEquals(
      listOf(
        ObjectChangeSet.Change("age", 30, 31),
        ObjectChangeSet.Change("name", "Alice", "Bob")
      ),
      changes
    )
  }

  @Test
  fun `nullable property changing to null is reported`() {
    val changes = ObjectChangeSet.compare(original, original.copy(nickname = null), Profile::nickname)

    assertEquals(listOf(ObjectChangeSet.Change("nickname", "Al", null)), changes)
  }

  @Test
  fun `nullable property changing from null is reported`() {
    val changes = ObjectChangeSet.compare(original.copy(nickname = null), original, Profile::nickname)

    assertEquals(listOf(ObjectChangeSet.Change("nickname", null, "Al")), changes)
  }

  @Test
  fun `null remaining null produces no change`() {
    val profile = original.copy(nickname = null)

    assertTrue(ObjectChangeSet.compare(profile, profile.copy(), Profile::nickname).isEmpty())
  }

  @Test
  fun `equal collections produce no change even with different instances`() {
    val updated = original.copy(roles = arrayListOf("admin"))

    assertTrue(ObjectChangeSet.compare(original, updated, Profile::roles).isEmpty())
  }

  @Test
  fun `changed collections retain the original and updated values`() {
    val updated = original.copy(roles = listOf("admin", "editor"))

    val changes = ObjectChangeSet.compare(original, updated, Profile::roles)

    assertEquals(
      listOf(ObjectChangeSet.Change("roles", listOf("admin"), listOf("admin", "editor"))),
      changes
    )
  }

  private data class Profile(
    val name: String,
    val nickname: String?,
    val age: Int,
    val roles: List<String>
  )
}
