package com.boxtv.source.tvf90

import com.boxtv.source.Channel
import com.boxtv.source.ScheduleException
import com.boxtv.source.ScheduledEvent
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class Tvf90ScheduleTest {

    private val server = MockWebServer()
    private lateinit var schedule: Tvf90Schedule

    @Before
    fun setUp() {
        server.start()
        schedule = Tvf90Schedule(
            url = server.url("/diaries.json"),
            clock = Clock.fixed(Instant.parse("2026-09-25T20:00:00Z"), ZoneOffset.UTC)
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `returns events sorted by start time`() = runTest {
        val events = loadFixture()

        assertEquals(listOf(40033L, 40038L, 40049L, 40037L, 40025L), events.map { it.id })
    }

    @Test
    fun `splits description into competition and match`() = runTest {
        val event = loadFixture().byId(40025)

        assertEquals("Liga MX", event.competition)
        assertEquals("Tijuana vs Atlas", event.title)
    }

    @Test
    fun `keeps whole description as title when there is no competition`() = runTest {
        val event = loadFixture().byId(40049)

        assertNull(event.competition)
        assertEquals("MLB – Boston Red Sox vs. Chicago Cubs", event.title)
    }

    @Test
    fun `interprets agenda times as Lima time`() = runTest {
        // 22:00 in Lima (UTC-5) is 03:00 UTC the next day.
        assertEquals(Instant.parse("2026-09-26T03:00:00Z"), loadFixture().byId(40025).startsAt)
    }

    @Test
    fun `collapses mirrors of one signal into a single channel`() = runTest {
        val events = loadFixture()

        // OP2, OP3 and HD embeds all carry the same stream id.
        assertEquals(listOf(tvf90("tudn", "TUDN USA")), events.byId(40025).channels)
        assertEquals(listOf(tvf90("tntsportschile", "TNT Sports Premiun CL")), events.byId(40038).channels)
    }

    @Test
    fun `decodes stream id with trailing newline`() = runTest {
        assertEquals(listOf(tvf90("even1", "Entel TV PLUS+")), loadFixture().byId(40037).channels)
    }

    @Test
    fun `event without embeds has no channels`() = runTest {
        assertEquals(emptyList<Channel>(), loadFixture().byId(40033).channels)
    }

    @Test
    fun `builds absolute flag url and tolerates missing country`() = runTest {
        val events = loadFixture()

        assertEquals(
            "https://img.wqxag.com/uploads/bandera_fbc29cb609_69a4b8272a.png",
            events.byId(40025).flagUrl
        )
        assertNull(events.byId(40033).flagUrl)
    }

    @Test
    fun `keeps distinct signals in order and ignores foreign or broken embeds`() = runTest {
        server.enqueue(
            json(
                diary(
                    embed("ESPN", "https://tvf90.com/1.php?stream=espn"),
                    embed("Elsewhere", "https://other.example/1.php?stream=nope"),
                    """{"attributes":{"embed_name":"Broken","embed_iframe":"/embed/eventos.html?r=%%%"}}""",
                    embed("Disney+ | HD", "https://tvf90.com/hd.php?stream=disney"),
                    embed("ESPN | OP2", "https://tvf90.com/2.php?stream=espn")
                )
            )
        )

        val channels = schedule.events().single().channels

        assertEquals(listOf(tvf90("espn", "ESPN"), tvf90("disney", "Disney+")), channels)
    }

    @Test
    fun `uses today's Lima date when the entry has none`() = runTest {
        server.enqueue(json("""{"id":1,"attributes":{"diary_description":"A vs B","diary_hour":"21:30:00"}}"""))

        // The fixed clock is 2026-09-25 15:00 in Lima.
        assertEquals(Instant.parse("2026-09-26T02:30:00Z"), schedule.events().single().startsAt)
    }

    @Test
    fun `drops entries without a parseable time`() = runTest {
        server.enqueue(json("""{"id":1,"attributes":{"diary_description":"A vs B","diary_hour":"TBD"}}"""))

        assertEquals(emptyList<ScheduledEvent>(), schedule.events())
    }

    @Test
    fun `fails on http error`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).build())

        assertScheduleFails()
    }

    @Test
    fun `fails on unexpected body`() = runTest {
        server.enqueue(MockResponse.Builder().body("<html>maintenance</html>").build())

        assertScheduleFails()
    }

    private suspend fun loadFixture(): List<ScheduledEvent> {
        server.enqueue(MockResponse.Builder().body(fixture("tvf90/diaries.json")).build())
        return schedule.events()
    }

    private fun List<ScheduledEvent>.byId(id: Long) = single { it.id == id }

    private fun tvf90(id: String, title: String) = Channel(Tvf90Source.ID, id, title)

    private fun json(vararg entries: String) =
        MockResponse.Builder().body("""{"data":[${entries.joinToString(",")}]}""").build()

    private fun diary(vararg embeds: String) =
        """{"id":1,"attributes":{"diary_description":"A vs B","diary_hour":"20:00:00","date_diary":"2026-09-25",""" +
            """"embeds":{"data":[${embeds.joinToString(",")}]}}}"""

    /** An embed in the site's format: the player page URL, base64-encoded in the `r` parameter. */
    private fun embed(name: String, playerPage: String): String {
        val encoded = java.util.Base64.getEncoder().encodeToString(playerPage.toByteArray())
        return """{"attributes":{"embed_name":"$name","embed_iframe":"/embed/eventos.html?r=$encoded"}}"""
    }

    private suspend fun assertScheduleFails() {
        try {
            schedule.events()
            fail("Expected ScheduleException")
        } catch (_: ScheduleException) {
            // Expected.
        }
    }

    private fun fixture(path: String): String =
        checkNotNull(javaClass.classLoader?.getResource(path)) { "Missing fixture $path" }.readText()
}
