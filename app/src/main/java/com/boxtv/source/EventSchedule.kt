package com.boxtv.source

import java.time.Instant

/**
 * A scheduled live event (typically a match). [id] is unique across sites (prefixed with the site's
 * key) and stable across refreshes, so the UI can keep an event expanded or focused. [competition] is null when the site doesn't name one.
 * [channels] are the signals broadcasting it, playable through the matching [StreamSource]; empty
 * when the site hasn't published a signal yet.
 */
data class ScheduledEvent(
    val id: String,
    val competition: String?,
    val title: String,
    val startsAt: Instant,
    val flagUrl: String?,
    val channels: List<Channel>
)

/** Today's agenda of a site, kept separate from [StreamSource] since agendas and streams may come from different sites. */
interface EventSchedule {
    /**
     * @return the events sorted by start time.
     * @throws ScheduleException when the agenda can't be obtained.
     */
    suspend fun events(): List<ScheduledEvent>
}

class ScheduleException(message: String, cause: Throwable? = null) : Exception(message, cause)
