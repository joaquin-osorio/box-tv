package com.boxtv.source.streamtp

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

class StreamtpSourceTest {

    private val server = MockWebServer()
    private val channel = Channel(source = StreamtpSource.ID, id = "espn", title = "ESPN")
    private lateinit var source: StreamtpSource

    @Before
    fun setUp() {
        server.start()
        source = StreamtpSource(baseUrl = server.url("/"))
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `extracts and unescapes playback url from player page`() = runTest {
        server.enqueue(MockResponse.Builder().body(fixture("streamtp/player_page.html")).build())

        val stream = source.resolve(channel)

        assertEquals(
            "https://cdn.example.com:443/global/espn/index.m3u8?token=abc123-84-1790462516-1790408516&ip=203.0.113.7",
            stream.url
        )
        assertEquals("/global1.php?stream=espn", server.takeRequest().target)
    }

    @Test
    fun `fails when page has no playback url`() = runTest {
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
