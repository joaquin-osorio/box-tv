package com.boxtv.player

import com.boxtv.source.Channel
import com.boxtv.source.ResolvedStream
import com.boxtv.source.StreamResolutionException
import com.boxtv.source.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val channel = Channel(id = "dsports", title = "DSports")

    /** Answers each resolve with the next queued result; each success yields a distinct URL. */
    private class FakeSource(vararg results: Boolean) : StreamSource {
        private val queue = ArrayDeque(results.toList())
        var calls = 0
            private set

        override suspend fun resolve(channel: Channel): ResolvedStream {
            calls++
            if (!queue.removeFirst()) throw StreamResolutionException("boom")
            return ResolvedStream("https://example.com/$calls.m3u8")
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `resolves the channel on start`() {
        val viewModel = PlayerViewModel(FakeSource(true), channel)

        assertEquals(PlayerUiState.Ready(ResolvedStream("https://example.com/1.m3u8")), viewModel.uiState.value)
    }

    @Test
    fun `shows error when resolution fails and recovers on retry`() {
        val viewModel = PlayerViewModel(FakeSource(false, true), channel)
        assertEquals(PlayerUiState.Error("boom"), viewModel.uiState.value)

        viewModel.retry()

        assertEquals(PlayerUiState.Ready(ResolvedStream("https://example.com/2.m3u8")), viewModel.uiState.value)
    }

    @Test
    fun `first playback error re-resolves silently, second one surfaces`() {
        val source = FakeSource(true, true)
        val viewModel = PlayerViewModel(source, channel)

        viewModel.onPlaybackError("ERROR_CODE_IO_BAD_HTTP_STATUS")
        assertEquals(PlayerUiState.Ready(ResolvedStream("https://example.com/2.m3u8")), viewModel.uiState.value)

        viewModel.onPlaybackError("ERROR_CODE_IO_BAD_HTTP_STATUS")
        assertEquals(PlayerUiState.Error("ERROR_CODE_IO_BAD_HTTP_STATUS"), viewModel.uiState.value)
        assertEquals(2, source.calls)
    }

    @Test
    fun `successful playback re-arms silent recovery`() {
        val source = FakeSource(true, true, true)
        val viewModel = PlayerViewModel(source, channel)

        viewModel.onPlaybackError("expired")
        viewModel.onPlaybackStarted()
        viewModel.onPlaybackError("expired again")

        assertEquals(PlayerUiState.Ready(ResolvedStream("https://example.com/3.m3u8")), viewModel.uiState.value)
    }
}
