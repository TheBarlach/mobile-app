package dk.sdu.expenseshare.data.local

import dk.sdu.expenseshare.data.InMemoryTextStore
import dk.sdu.expenseshare.data.summerTripRow
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BalancesCacheTest {
    private val cached = CachedGroups(groups = listOf(summerTripRow), fetchedAtMillis = 42)

    @Test
    fun `saved groups can be loaded again`() = runTest {
        val store = InMemoryTextStore()
        BalancesCache(store).save(cached)

        assertEquals(cached, BalancesCache(store).load())
    }

    @Test
    fun `an empty or broken cache loads as nothing`() = runTest {
        assertNull(BalancesCache(InMemoryTextStore()).load())
        assertNull(BalancesCache(InMemoryTextStore("{\"groups\": 5}")).load())
    }

    @Test
    fun `a failing disk is reported without throwing`() = runTest {
        val store = InMemoryTextStore().apply { failWrites = true }

        assertFalse(BalancesCache(store).save(cached))
    }

    @Test
    fun `file store replaces the file and creates missing folders`() = runTest {
        val folder = Files.createTempDirectory("balances-cache").toFile()
        try {
            val store = FileTextStore(File(folder, "user/groups.json"))
            assertNull(store.read())

            store.write("first")
            store.write("second")

            assertEquals("second", store.read())
            assertEquals(listOf("groups.json"), File(folder, "user").list()?.toList())
        } finally {
            folder.deleteRecursively()
        }
    }
}
