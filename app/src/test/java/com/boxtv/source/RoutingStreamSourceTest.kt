package com.boxtv.source

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class RoutingStreamSourceTest {

    /** Answers with a URL naming the site, to tell which source resolved the channel. */
    private class SiteSource(private val site: String) : StreamSource {
        override suspend fun resolve(channel: Channel) = ResolvedStream("https://$site/${channel.id}.m3u8")
    }

    private val router = RoutingStreamSource(mapOf("a" to SiteSource("a.example"), "b" to SiteSource("b.example")))

    @Test
    fun `resolves each channel with its own site`() = runTest {
        assertEquals("https://a.example/espn.m3u8", router.resolve(Channel("a", "espn", "ESPN")).url)
        assertEquals("https://b.example/espn.m3u8", router.resolve(Channel("b", "espn", "ESPN")).url)
    }

    @Test
    fun `fails for a channel of an unknown site`() = runTest {
        try {
            router.resolve(Channel("c", "espn", "ESPN"))
            fail("Expected StreamResolutionException")
        } catch (_: StreamResolutionException) {
            // Expected.
        }
    }
}
