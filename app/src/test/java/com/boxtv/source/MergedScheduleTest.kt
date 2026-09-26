package com.boxtv.source

import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class MergedScheduleTest {

    private val start = Instant.parse("2026-09-26T18:00:00Z")

    private fun event(id: String, minutesAfterStart: Long) = ScheduledEvent(
        id = id,
        competition = null,
        title = id,
        startsAt = start.plusSeconds(minutesAfterStart * 60),
        flagUrl = null,
        channels = emptyList()
    )

    /** Answers each fetch with the next queued result: a list of events, or null to fail. */
    private class FakeSchedule(vararg results: List<ScheduledEvent>?) : EventSchedule {
        private val queue = ArrayDeque(results.toList())

        override suspend fun events(): List<ScheduledEvent> = queue.removeFirst() ?: throw ScheduleException("boom")
    }

    @Test
    fun `merges every site sorted by start time`() = runTest {
        val merged = MergedSchedule(
            listOf(
                FakeSchedule(listOf(event("a1", 0), event("a2", 60))),
                FakeSchedule(listOf(event("b1", 30), event("b2", 90)))
            )
        )

        assertEquals(listOf("a1", "b1", "a2", "b2"), merged.events().map { it.id })
    }

    @Test
    fun `keeps the other sites when one fails`() = runTest {
        val merged = MergedSchedule(listOf(FakeSchedule(null), FakeSchedule(listOf(event("b1", 0)))))

        assertEquals(listOf("b1"), merged.events().map { it.id })
    }

    @Test
    fun `keeps a failing site's last agenda`() = runTest {
        val merged = MergedSchedule(
            listOf(
                FakeSchedule(listOf(event("a1", 0)), null),
                FakeSchedule(listOf(event("b1", 30)), listOf(event("b2", 30)))
            )
        )
        merged.events()

        assertEquals(listOf("a1", "b2"), merged.events().map { it.id })
    }

    @Test
    fun `fails while no site has ever loaded`() = runTest {
        val merged = MergedSchedule(listOf(FakeSchedule(null), FakeSchedule(null)))

        try {
            merged.events()
            fail("Expected ScheduleException")
        } catch (_: ScheduleException) {
            // Expected.
        }
    }
}
