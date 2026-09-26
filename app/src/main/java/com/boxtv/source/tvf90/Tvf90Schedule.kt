package com.boxtv.source.tvf90

import com.boxtv.source.Channel
import com.boxtv.source.EventSchedule
import com.boxtv.source.ScheduleException
import com.boxtv.source.ScheduledEvent
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString.Companion.decodeBase64

/**
 * tvf90's agenda, read from the JSON API its `agenda.html` page loads (a Strapi backend). Each event's
 * signals point at tvf90 player pages with a `stream` id, which [Tvf90Source] resolves. The several
 * mirrors of one signal (OP2, OP3, HD) share that id, so they collapse into one channel.
 * See docs/adapters/tvf90.md.
 */
class Tvf90Schedule(
    private val client: OkHttpClient = OkHttpClient(),
    private val url: HttpUrl = "https://api.wqxag.com/diaries.json".toHttpUrl(),
    private val clock: Clock = Clock.systemUTC()
) : EventSchedule {

    override suspend fun events(): List<ScheduledEvent> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", SITE_URL.toString())
            .build()

        val body = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ScheduleException("tvf90 agenda returned HTTP ${response.code}")
                }
                response.body.string()
            }
        } catch (e: IOException) {
            throw ScheduleException("Could not reach the tvf90 agenda", e)
        }

        val diaries = try {
            JSON.decodeFromString<Diaries>(body)
        } catch (e: SerializationException) {
            throw ScheduleException("Unexpected tvf90 agenda format", e)
        } catch (e: IllegalArgumentException) {
            throw ScheduleException("Unexpected tvf90 agenda format", e)
        }

        diaries.data.mapNotNull { it.toEvent() }.sortedBy { it.startsAt }
    }

    /** Entries without a description or a parseable time are dropped; the site shows placeholders for them. */
    private fun Node<Diary>.toEvent(): ScheduledEvent? {
        val description = attributes.description?.replace(WHITESPACE, " ")?.trim()
        if (description.isNullOrEmpty()) return null
        // "Liga MX: Tijuana vs Atlas" → competition + match. Some entries have no competition prefix.
        val competition = description.substringBefore(':', missingDelimiterValue = "").trim()
        val title = description.substringAfter(':').trim()

        return ScheduledEvent(
            id = id,
            competition = competition.ifEmpty { null },
            title = title.ifEmpty { description },
            startsAt = startsAt(attributes) ?: return null,
            flagUrl = attributes.country?.data?.attributes?.image?.data?.attributes?.url?.let(::imageUrl),
            channels = attributes.embeds?.data.orEmpty()
                .mapNotNull { it.attributes.toChannel() }
                .distinctBy { it.id }
        )
    }

    /** The agenda's times are Lima wall-clock times (the site converts them to the viewer's zone in JS). */
    private fun startsAt(diary: Diary): Instant? = try {
        val time = LocalTime.parse(diary.hour ?: return null)
        val date = diary.date?.let(LocalDate::parse) ?: LocalDate.now(clock.withZone(SITE_ZONE))
        date.atTime(time).atZone(SITE_ZONE).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }

    /**
     * Embeds link to `/embed/eventos.html?r=<base64>`, where the base64 decodes to a player page such
     * as `https://tvf90.com/1.php?stream=<id>` (sometimes with a trailing newline). Only the `stream`
     * id matters: every tvf90 player page variant plays the same stream.
     */
    private fun Embed.toChannel(): Channel? {
        val target = SITE_URL.resolve(iframe ?: return null)
            ?.queryParameter("r")
            // Query decoding turns base64 '+' into spaces.
            ?.replace(' ', '+')
            ?.decodeBase64()
            ?.utf8()
            ?.trim()
            ?.toHttpUrlOrNull()
            ?.takeIf { it.host == SITE_URL.host }
            ?: return null
        val streamId = target.queryParameter("stream")?.trim()?.ifEmpty { null } ?: return null
        // Mirrors are named "<channel> | OP2", "<channel> | HD"; keep the channel part.
        val channelName = name?.substringBefore(" | ")?.trim()?.ifEmpty { null } ?: streamId
        return Channel(id = streamId, title = channelName)
    }

    private fun imageUrl(path: String): String = when {
        path.startsWith("http://") || path.startsWith("https://") -> path
        else -> "$IMAGE_BASE_URL/${path.removePrefix("/")}"
    }

    @Serializable
    private class Diaries(val data: List<Node<Diary>> = emptyList())

    /** Strapi wraps every entity as `{ id, attributes }` and every relation as `{ data }`. */
    @Serializable
    private class Node<T>(val id: Long = 0, val attributes: T)

    @Serializable
    private class Relation<T>(val data: T? = null)

    @Serializable
    private class Diary(
        @SerialName("diary_description") val description: String? = null,
        @SerialName("diary_hour") val hour: String? = null,
        @SerialName("date_diary") val date: String? = null,
        val country: Relation<Node<Country>>? = null,
        val embeds: Relation<List<Node<Embed>>>? = null
    )

    @Serializable
    private class Country(val image: Relation<Node<Image>>? = null)

    @Serializable
    private class Image(val url: String? = null)

    @Serializable
    private class Embed(
        @SerialName("embed_name") val name: String? = null,
        @SerialName("embed_iframe") val iframe: String? = null
    )

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
        val SITE_URL = "https://tvf90.com/".toHttpUrl()
        const val IMAGE_BASE_URL = "https://img.wqxag.com"
        val SITE_ZONE: ZoneId = ZoneId.of("America/Lima")
        val WHITESPACE = Regex("""\s+""")
    }
}
