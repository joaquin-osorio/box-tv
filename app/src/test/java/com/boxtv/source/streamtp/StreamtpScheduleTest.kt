package com.boxtv.source.streamtp

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class StreamtpScheduleTest {

    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `groups the signals of each event and sorts by start time`() = runTest {
        val events = loadFixture()

        assertEquals(
            listOf(
                "Carrera",
                "Race HUB",
                "Inglaterra vs España",
                "Raúl Rosas Jr. vs Raoni Barcelos",
                "Peñarol vs Boston River",
                "LA Galaxy vs Colorado Rapids"
            ),
            events.map { it.title }
        )
        assertEquals(
            listOf(streamtp("disney11", "DISNEY 11"), streamtp("espn", "ESPN"), streamtp("vix1", "VIX 1")),
            events.byTitle("Inglaterra vs España").channels
        )
    }

    @Test
    fun `turns language variants into signals of one event`() = runTest {
        val event = loadFixture().byTitle("Raúl Rosas Jr. vs Raoni Barcelos")

        assertEquals(
            listOf(
                streamtp("paramount1", "PARAMOUNT 1 · Español"),
                streamtp("paramount2", "PARAMOUNT 2 · English"),
                streamtp("paramount3", "PARAMOUNT 3 · Portugues")
            ),
            event.channels
        )
    }

    @Test
    fun `uses the category as competition and has no flag`() = runTest {
        val event = loadFixture().byTitle("Carrera")

        assertEquals("F1 | GP de Azerbaiyán", event.competition)
        assertNull(event.flagUrl)
        assertEquals(listOf("disney1", "fox1ar"), event.channels.map { it.id })
    }

    @Test
    fun `interprets agenda times as Panama time on the site's current day`() = runTest {
        // 13:45 in Panama (UTC-5) is 18:45 UTC.
        assertEquals(Instant.parse("2026-09-26T18:45:00Z"), loadFixture().byTitle("Inglaterra vs España").startsAt)
    }

    @Test
    fun `takes the date from Panama even when it is already tomorrow in UTC`() = runTest {
        // 02:00 UTC on the 27th is still the 26th, 21:00, in Panama.
        val events = loadFixture(now = "2026-09-27T02:00:00Z")

        assertEquals(Instant.parse("2026-09-27T02:30:00Z"), events.byTitle("LA Galaxy vs Colorado Rapids").startsAt)
    }

    @Test
    fun `keeps event ids stable across refreshes`() = runTest {
        assertEquals(loadFixture().map { it.id }, loadFixture().map { it.id })
        assertEquals(
            "streamtp:13:45|Liga de Naciones de la UEFA|inglaterra vs españa",
            loadFixture().byTitle("Inglaterra vs España").id
        )
    }

    @Test
    fun `drops entries without a parseable time or a stream id`() = runTest {
        val titles = loadFixture().map { it.title }

        assertFalse("Placeholder vs TBD" in titles)
        assertFalse("No Signal FC vs Nobody" in titles)
    }

    @Test
    fun `fails on http error`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).build())

        assertScheduleFails()
    }

    @Test
    fun `fails on unexpected format`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"events":[]}""").build())

        assertScheduleFails()
    }

    private suspend fun assertScheduleFails() {
        try {
            schedule().events()
            fail("Expected ScheduleException")
        } catch (_: ScheduleException) {
            // Expected.
        }
    }

    private fun schedule(now: String = "2026-09-26T10:00:00Z") = StreamtpSchedule(
        url = server.url("/events.json"),
        clock = Clock.fixed(Instant.parse(now), ZoneOffset.UTC)
    )

    private suspend fun loadFixture(now: String = "2026-09-26T10:00:00Z"): List<ScheduledEvent> {
        server.enqueue(MockResponse.Builder().body(fixture("streamtp/events.json")).build())
        return schedule(now).events()
    }

    private fun List<ScheduledEvent>.byTitle(title: String) = single { it.title == title }

    private fun streamtp(id: String, title: String) = Channel(StreamtpSource.ID, id, title)

    private fun fixture(path: String): String =
        checkNotNull(javaClass.classLoader?.getResource(path)) { "Missing fixture $path" }.readText()
}
