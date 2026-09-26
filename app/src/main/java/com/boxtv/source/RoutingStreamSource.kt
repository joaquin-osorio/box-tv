package com.boxtv.source

/** Plays each channel with the [StreamSource] registered under its [Channel.source]. */
class RoutingStreamSource(private val sources: Map<String, StreamSource>) : StreamSource {

    override suspend fun resolve(channel: Channel): ResolvedStream {
        val source = sources[channel.source]
            ?: throw StreamResolutionException("No stream source for '${channel.source}'")
        return source.resolve(channel)
    }
}
