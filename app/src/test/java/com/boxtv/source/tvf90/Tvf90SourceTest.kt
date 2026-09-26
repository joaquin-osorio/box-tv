package com.boxtv.source.tvf90

import com.boxtv.source.Channel
import com.boxtv.source.StreamResolutionException
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class Tvf90SourceTest {

    private val server = MockWebServer()
    private val channel = Channel(source = "tvf90", id = "dsports", title = "DSports")
    private lateinit var source: Tvf90Source

    @Before
    fun setUp() {
        server.start()
        source = Tvf90Source(baseUrl = server.url("/"))
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `extracts playback url from player page`() = runTest {
        server.enqueue(MockResponse.Builder().body(fixture("tvf90/player_page.html")).build())

        val stream = source.resolve(channel)

        assertEquals(
            "https://cdn.example.com:443/dsports/mono.m3u8?token=abc123-de-1790379102-1790361102",
            stream.url
        )
    }

    @Test
    fun `requests player page with embedding page as referer`() = runTest {
        server.enqueue(MockResponse.Builder().body(fixture("tvf90/player_page.html")).build())

        source.resolve(channel)

        val request = server.takeRequest()
        assertEquals("/5.php?stream=dsports", request.target)
        assertEquals(server.url("/online.php?stream=dsports").toString(), request.headers["Referer"])
    }

    @Test
    fun `fails when page has no playback url`() = runTest {
        // What the site serves when the Referer check fails.
        server.enqueue(MockResponse.Builder().body("<html><body>blocked</body></html>").build())

        assertResolutionFails()
    }

    @Test
    fun `fails on http error`() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())

        assertResolutionFails()
    }

    private suspend fun assertResolutionFails() {
        try {
            source.resolve(channel)
            fail("Expected StreamResolutionException")
        } catch (_: StreamResolutionException) {
            // Expected.
        }
    }

    private fun fixture(path: String): String =
        checkNotNull(javaClass.classLoader?.getResource(path)) { "Missing fixture $path" }.readText()
}
