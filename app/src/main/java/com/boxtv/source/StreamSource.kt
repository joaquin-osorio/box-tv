package com.boxtv.source

/** A watchable live channel exposed by a [StreamSource]. [id] is the site-specific channel key. */
data class Channel(val id: String, val title: String)

/**
 * A playable stream. [url] is typically short-lived (signed tokens), so it must be resolved right
 * before playback instead of being stored. [headers] must be sent on every media request.
 */
data class ResolvedStream(val url: String, val headers: Map<String, String> = emptyMap())

/**
 * Common contract for every streaming site. The UI layer only talks to this interface, so a site
 * changing its HTML/API only breaks its own adapter.
 */
interface StreamSource {
    /** @throws StreamResolutionException when the playable URL can't be obtained. */
    suspend fun resolve(channel: Channel): ResolvedStream
}

class StreamResolutionException(message: String, cause: Throwable? = null) : Exception(message, cause)
