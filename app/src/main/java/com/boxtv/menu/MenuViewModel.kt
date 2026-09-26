package com.boxtv.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxtv.source.EventSchedule
import com.boxtv.source.ScheduleException
import com.boxtv.source.ScheduledEvent
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface EventStatus {
    /** Starts in [minutes] (rounded up). */
    data class Upcoming(val minutes: Long) : EventStatus
    data object Live : EventStatus
    data object Finished : EventStatus
}

data class EventItem(val event: ScheduledEvent, val status: EventStatus)

sealed interface ScheduleState {
    data object Loading : ScheduleState
    data class Loaded(val items: List<EventItem>) : ScheduleState
    data object Error : ScheduleState
}

/** [expandedEventId] is the event whose signals are listed (at most one at a time). */
data class MenuUiState(val schedule: ScheduleState = ScheduleState.Loading, val expandedEventId: String? = null)

/**
 * Holds today's events for the menu's Events tab. The agenda is fetched by [keepFresh], which the UI
 * runs only while the menu is on screen. The site gives no end times, so "live" is a guess: the first
 * [LIVE_WINDOW] after the start.
 */
class MenuViewModel(private val schedule: EventSchedule, private val clock: () -> Instant = Instant::now) :
    ViewModel() {

    private val _uiState = MutableStateFlow(MenuUiState())
    val uiState: StateFlow<MenuUiState> = _uiState.asStateFlow()

    /** Last successfully fetched agenda; kept when a later refresh fails. */
    private var events: List<ScheduledEvent>? = null

    /** Refreshes now and then every [REFRESH_INTERVAL] until cancelled. */
    suspend fun keepFresh() {
        while (true) {
            refresh()
            delay(REFRESH_INTERVAL)
        }
    }

    fun retry() {
        _uiState.update { it.copy(schedule = ScheduleState.Loading) }
        viewModelScope.launch { refresh() }
    }

    /** Expands [eventId]'s signals, collapsing any other event; collapses it if it was already expanded. */
    fun toggleEvent(eventId: String) {
        _uiState.update { it.copy(expandedEventId = if (it.expandedEventId == eventId) null else eventId) }
    }

    fun collapse() {
        _uiState.update { it.copy(expandedEventId = null) }
    }

    private suspend fun refresh() {
        try {
            events = schedule.events()
        } catch (_: ScheduleException) {
            // Keep showing the last agenda, if any; statuses are still re-evaluated below.
        }
        val current = events
        val state = if (current == null) ScheduleState.Error else ScheduleState.Loaded(classify(current, clock()))
        _uiState.update { it.copy(schedule = state) }
    }

    companion object {
        val REFRESH_INTERVAL = 60.seconds
        val LIVE_WINDOW = 150.minutes

        internal fun classify(events: List<ScheduledEvent>, now: Instant): List<EventItem> = events.map { event ->
            val sinceStart = Duration.between(event.startsAt, now)
            val status = when {
                sinceStart.isNegative -> EventStatus.Upcoming(minutes = ceilMinutes(sinceStart.negated()))
                sinceStart.toMillis() < LIVE_WINDOW.inWholeMilliseconds -> EventStatus.Live
                else -> EventStatus.Finished
            }
            EventItem(event, status)
        }

        private fun ceilMinutes(duration: Duration): Long = (duration.toMillis() + 59_999) / 60_000
    }
}
