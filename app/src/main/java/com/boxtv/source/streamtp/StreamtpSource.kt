package com.boxtv.source.streamtp

import com.boxtv.source.Channel
import com.boxtv.source.ResolvedStream
import com.boxtv.source.StreamResolutionException
import com.boxtv.source.StreamSource
import com.boxtv.source.USER_AGENT
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Resolves streamtp channels. The `global1.php` player page carries the signed HLS URL in an inline
 * `playbackURL` JS variable, written as a JSON string (`\/` for `/`). The token is bound to the
 * requester's public IP, so the URL only plays on the device that resolved it.
 *
 * The site's domain is blocked by some ISPs' DNS: [client] should resolve through a fallback DNS
 * (see [com.boxtv.source.dnsWithDohFallback]). See docs/adapters/streamtp.md.
 */
class StreamtpSource(private val client: OkHttpClient = OkHttpClient(), private val baseUrl: HttpUrl = SITE_URL) :
    StreamSource {

    override suspend fun resolve(channel: Channel): ResolvedStream = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(
                baseUrl.newBuilder()
                    .addPathSegment("global1.php")
                    .addQueryParameter("stream", channel.id)
                    .build()
            )
            .header("User-Agent", USER_AGENT)
            .build()

        val html = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw StreamResolutionException("streamtp player page returned HTTP ${response.code}")
                }
                response.body.string()
            }
        } catch (e: IOException) {
            throw StreamResolutionException("Could not reach streamtp", e)
        }

        val url = PLAYBACK_URL_REGEX.find(html)?.groupValues?.get(1)?.replace("\\/", "/")
            ?: throw StreamResolutionException("Stream URL not found in streamtp player page")
        ResolvedStream(url = url, headers = mapOf("User-Agent" to USER_AGENT))
    }

    companion object {
        /** [Channel.source] of streamtp channels. */
        const val ID = "streamtp"

        /** The site's current domain; it rotates (`streamtp-golden<n>`), so this is the one thing to update. */
        internal val SITE_URL = "https://streamtp-golden1.click/".toHttpUrl()

        private val PLAYBACK_URL_REGEX = Regex("""playbackURL\s*=\s*"([^"]+)"""")
    }
}
