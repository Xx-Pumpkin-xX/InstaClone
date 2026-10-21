package vn.edu.usth.instaclone.data.repository.fake

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeUserRepositoryTest {

    private val repository = FakeUserRepository(myId = { "me" }, latencyMillis = 0)

    @Test
    fun getUser_returnsSeededProfile() = runBlocking {
        val user = repository.getUser("user-tram")

        assertNotNull(user)
        assertEquals("tram", user?.username)
    }

    @Test
    fun getUser_returnsCurrentMemberDemoProfile() = runBlocking {
        val user = repository.getUser("me")

        assertNotNull(user)
        assertEquals("you", user?.username)
    }

    @Test
    fun getUser_returnsNullForUnknownMember() = runBlocking {
        assertNull(repository.getUser("missing"))
    }

    @Test
    fun searchByPrefix_ignoresCaseAndHonoursLimit() = runBlocking {
        val results = repository.searchByPrefix("TR", limit = 1)

        assertTrue(results.size <= 1)
        assertEquals("tram", results.firstOrNull()?.username)
    }
}
