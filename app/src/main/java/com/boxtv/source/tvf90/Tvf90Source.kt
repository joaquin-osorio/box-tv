package com.boxtv.source.tvf90

import com.boxtv.source.Channel
import com.boxtv.source.ResolvedStream
import com.boxtv.source.StreamResolutionException
import com.boxtv.source.StreamSource
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Resolves tvf90 channels. The public page (`online.php`) only embeds the `5.php` player page in an
 * iframe; `5.php` carries the signed HLS URL in an inline `playbackURL` JS variable, but only when
 * the request's Referer is the embedding page. See docs/adapters/tvf90.md.
 */
class Tvf90Source(
    private val client: OkHttpClient = OkHttpClient(),
    private val baseUrl: HttpUrl = "https://tvf90.com/".toHttpUrl()
) : StreamSource {

    override suspend fun resolve(channel: Channel): ResolvedStream = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(pageUrl("5.php", channel))
            .header("User-Agent", USER_AGENT)
            .header("Referer", pageUrl("online.php", channel).toString())
            .build()

        val html = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw StreamResolutionException("tvf90 player page returned HTTP ${response.code}")
                }
                response.body.string()
            }
        } catch (e: IOException) {
            throw StreamResolutionException("Could not reach tvf90", e)
        }

        val url = PLAYBACK_URL_REGEX.find(html)?.groupValues?.get(1)
            ?: throw StreamResolutionException("Stream URL not found in tvf90 player page")
        ResolvedStream(url = url, headers = mapOf("User-Agent" to USER_AGENT))
    }

    private fun pageUrl(page: String, channel: Channel): HttpUrl = baseUrl.newBuilder()
        .addPathSegment(page)
        .addQueryParameter("stream", channel.id)
        .build()

    private companion object {
        val PLAYBACK_URL_REGEX = Regex("""playbackURL\s*=\s*"([^"]+)"""")
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/128.0.0.0 Safari/537.36"
    }
}
