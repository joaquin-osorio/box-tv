package com.boxtv.menu

import com.boxtv.source.Channel
import com.boxtv.source.EventSchedule
import com.boxtv.source.ScheduleException
import com.boxtv.source.ScheduledEvent
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MenuViewModelTest {

    private val start = Instant.parse("2026-09-25T20:00:00Z")

    private fun event(id: Long, startsAt: Instant) = ScheduledEvent(
        id = id,
        competition = "Liga MX",
        title = "Match $id",
        startsAt = startsAt,
        flagUrl = null,
        channels = listOf(Channel("tvf90", "tudn", "TUDN"))
    )

    /** Answers each fetch with the next queued result: a list of events, or null to fail. */
    private class FakeSchedule(vararg results: List<ScheduledEvent>?) : EventSchedule {
        private val queue = ArrayDeque(results.toList())
        var calls = 0

        override suspend fun events(): List<ScheduledEvent> {
            calls++
            return queue.removeFirst() ?: throw ScheduleException("down")
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A clock that follows the test's virtual time, starting at [start]. */
    private fun TestScope.virtualClock(): () -> Instant = { start.plusMillis(testScheduler.currentTime) }

    private fun MenuViewModel.statuses() = (uiState.value.schedule as ScheduleState.Loaded).items.map { it.status }

    @Test
    fun `classifies events around the live window`() {
        val window = MenuViewModel.LIVE_WINDOW.inWholeMinutes
        val events = listOf(
            event(1, start.minusSeconds(window * 60)),
            event(2, start.minusSeconds(window * 60 - 1)),
            event(3, start),
            event(4, start.plusSeconds(25 * 60)),
            event(5, start.plusSeconds(30))
        )

        val statuses = MenuViewModel.classify(events, start).map { it.status }

        assertEquals(
            listOf(
                EventStatus.Finished,
                EventStatus.Live,
                EventStatus.Live,
                EventStatus.Upcoming(25),
                EventStatus.Upcoming(1)
            ),
            statuses
        )
    }

    @Test
    fun `starts loading and fetches when kept fresh`() = runTest {
        val schedule = FakeSchedule(listOf(event(1, start.plusSeconds(600))))
        val viewModel = MenuViewModel(schedule, virtualClock())
        assertEquals(ScheduleState.Loading, viewModel.uiState.value.schedule)

        backgroundScope.launch { viewModel.keepFresh() }
        runCurrent()

        assertEquals(listOf(EventStatus.Upcoming(10)), viewModel.statuses())
    }

    @Test
    fun `refreshes periodically and re-evaluates statuses`() = runTest {
        val events = listOf(event(1, start.plusSeconds(60)))
        val schedule = FakeSchedule(events, events)
        val viewModel = MenuViewModel(schedule, virtualClock())

        backgroundScope.launch { viewModel.keepFresh() }
        runCurrent()
        advanceTimeBy(MenuViewModel.REFRESH_INTERVAL.inWholeMilliseconds)
        runCurrent()

        assertEquals(2, schedule.calls)
        assertEquals(listOf(EventStatus.Live), viewModel.statuses())
    }

    @Test
    fun `failed refresh keeps the last agenda`() = runTest {
        val schedule = FakeSchedule(listOf(event(1, start.plusSeconds(60))), null)
        val viewModel = MenuViewModel(schedule, virtualClock())

        backgroundScope.launch { viewModel.keepFresh() }
        runCurrent()
        advanceTimeBy(MenuViewModel.REFRESH_INTERVAL.inWholeMilliseconds)
        runCurrent()

        assertEquals(2, schedule.calls)
        assertEquals(listOf(EventStatus.Live), viewModel.statuses())
    }

    @Test
    fun `shows error only when nothing was ever loaded, and retry recovers`() = runTest {
        val schedule = FakeSchedule(null, listOf(event(1, start)))
        val viewModel = MenuViewModel(schedule, virtualClock())

        backgroundScope.launch { viewModel.keepFresh() }
        runCurrent()
        assertEquals(ScheduleState.Error, viewModel.uiState.value.schedule)

        viewModel.retry()
        runCurrent()

        assertEquals(listOf(EventStatus.Live), viewModel.statuses())
    }

    @Test
    fun `expands one event at a time`() {
        val viewModel = MenuViewModel(FakeSchedule())

        viewModel.toggleEvent(1)
        assertEquals(1L, viewModel.uiState.value.expandedEventId)

        viewModel.toggleEvent(2)
        assertEquals(2L, viewModel.uiState.value.expandedEventId)

        viewModel.toggleEvent(2)
        assertNull(viewModel.uiState.value.expandedEventId)

        viewModel.toggleEvent(3)
        viewModel.collapse()
        assertNull(viewModel.uiState.value.expandedEventId)
    }
}
