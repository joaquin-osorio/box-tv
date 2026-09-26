package com.boxtv.source.streamtp

import com.boxtv.source.Channel
import com.boxtv.source.EventSchedule
import com.boxtv.source.ScheduleException
import com.boxtv.source.ScheduledEvent
import com.boxtv.source.USER_AGENT
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * streamtp's agenda, read from the `events.json` its `eventos.html` page loads. The site lists one
 * entry per signal, so entries with the same time, category and title are grouped into one event.
 * Language variants are marked in the title (`"A vs B | English"`) and become part of the signal name.
 * See docs/adapters/streamtp.md.
 */
class StreamtpSchedule(
    private val client: OkHttpClient = OkHttpClient(),
    private val url: HttpUrl = StreamtpSource.SITE_URL.resolve("events.json")!!,
    private val clock: Clock = Clock.systemUTC()
) : EventSchedule {

    override suspend fun events(): List<ScheduledEvent> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        val body = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ScheduleException("streamtp agenda returned HTTP ${response.code}")
                }
                response.body.string()
            }
        } catch (e: IOException) {
            throw ScheduleException("Could not reach the streamtp agenda", e)
        }

        val entries = try {
            JSON.decodeFromString<List<Entry>>(body)
        } catch (e: SerializationException) {
            throw ScheduleException("Unexpected streamtp agenda format", e)
        } catch (e: IllegalArgumentException) {
            throw ScheduleException("Unexpected streamtp agenda format", e)
        }

        // The agenda has no dates: it only covers the site's current day.
        val today = LocalDate.now(clock.withZone(SITE_ZONE))
        entries.mapNotNull { it.toSignal(today) }
            .groupBy { it.eventId }
            .map { (eventId, signals) ->
                val first = signals.first()
                ScheduledEvent(
                    id = eventId,
                    competition = first.competition,
                    title = first.title,
                    startsAt = first.startsAt,
                    flagUrl = null,
                    channels = signals.map { it.channel }.distinctBy { it.id }
                )
            }
            .sortedBy { it.startsAt }
    }

    /** Entries without a title, a parseable time or a `stream` id are dropped. */
    private fun Entry.toSignal(today: LocalDate): Signal? {
        val fullTitle = title?.clean().orEmpty()
        // "Rosas Jr. vs Barcelos | English" → the event + the signal's language.
        val eventTitle = fullTitle.substringBefore(" | ").trim()
        if (eventTitle.isEmpty()) return null
        val language = fullTitle.substringAfter(" | ", missingDelimiterValue = "").trim().ifEmpty { null }
        val competition = category?.clean()?.ifEmpty { null }

        // Only the stream id matters, not the host: the site's domain rotates.
        val streamId = url?.trim()?.toHttpUrlOrNull()?.queryParameter("stream")?.trim()?.ifEmpty { null }
            ?: return null
        val startTime = try {
            LocalTime.parse(time?.trim() ?: return null)
        } catch (_: DateTimeParseException) {
            return null
        }

        return Signal(
            eventId = "${StreamtpSource.ID}:$startTime|${competition.orEmpty()}|${eventTitle.lowercase()}",
            competition = competition,
            title = eventTitle,
            startsAt = today.atTime(startTime).atZone(SITE_ZONE).toInstant(),
            channel = Channel(
                source = StreamtpSource.ID,
                id = streamId,
                title = channelName(streamId) + (language?.let { " · $it" } ?: "")
            )
        )
    }

    private class Signal(
        val eventId: String,
        val competition: String?,
        val title: String,
        val startsAt: Instant,
        val channel: Channel
    )

    @Serializable
    private class Entry(
        val title: String? = null,
        val category: String? = null,
        val time: String? = null,
        val url: String? = null
    )

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }

        /** The page shows its clock in this zone, and the agenda's times match it (checked against F1 start times). */
        val SITE_ZONE: ZoneId = ZoneId.of("America/Panama")
        val WHITESPACE = Regex("""\s+""")
        val TRAILING_NUMBER = Regex("""(?<=[A-Z])(?=\d+$)""")

        fun String.clean() = replace(WHITESPACE, " ").trim()

        /** The site only gives stream ids: `disney1` → "DISNEY 1", `tudn_usa` → "TUDN USA", `espn2mx` → "ESPN2MX". */
        fun channelName(streamId: String) = streamId.replace('_', ' ').uppercase().replace(TRAILING_NUMBER, " ")
    }
}
