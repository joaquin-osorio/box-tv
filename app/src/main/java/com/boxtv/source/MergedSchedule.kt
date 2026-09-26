package com.boxtv.source

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Several sites' agendas shown as one, sorted by start time (ties keep the order of [schedules]).
 * Sites are fetched concurrently. A site whose fetch fails keeps contributing its last successful
 * list, so its events don't vanish on a transient error; this only fails while no site has ever loaded.
 * The same match listed by two sites stays as two events.
 */
class MergedSchedule(private val schedules: List<EventSchedule>) : EventSchedule {

    private val lastEvents = MutableList<List<ScheduledEvent>?>(schedules.size) { null }

    override suspend fun events(): List<ScheduledEvent> {
        val results = coroutineScope {
            schedules.map { schedule -> async { runCatchingSchedule { schedule.events() } } }.awaitAll()
        }
        results.forEachIndexed { i, result -> result.getOrNull()?.let { lastEvents[i] = it } }

        val known = lastEvents.filterNotNull()
        if (known.isEmpty()) {
            throw ScheduleException("No agenda could be loaded", results.firstNotNullOfOrNull { it.exceptionOrNull() })
        }
        return known.flatten().sortedBy { it.startsAt }
    }

    /** Like [runCatching], but only for [ScheduleException]: anything else is a bug and propagates. */
    private inline fun <T> runCatchingSchedule(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: ScheduleException) {
        Result.failure(e)
    }
}
