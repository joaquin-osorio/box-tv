package com.boxtv.player

import com.boxtv.source.Channel
import com.boxtv.source.ResolvedStream
import com.boxtv.source.StreamResolutionException
import com.boxtv.source.StreamSource
import kotlinx.coroutines.CompletableDeferred
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

    private val espn = Channel(source = "tvf90", id = "espn", title = "ESPN")
    private val dsports = Channel(source = "tvf90", id = "dsports", title = "DSports")

    /** Answers each resolve with the next queued result; each success yields a distinct URL. */
    private class FakeSource(vararg results: Boolean) : StreamSource {
        private val queue = ArrayDeque(results.toList())
        val resolvedIds = mutableListOf<String>()
        val calls get() = resolvedIds.size

        override suspend fun resolve(channel: Channel): ResolvedStream {
            resolvedIds += channel.id
            if (!queue.removeFirst()) throw StreamResolutionException("boom")
            return ResolvedStream("https://example.com/$calls.m3u8")
        }
    }

    /** Never answers until [release] is called, to observe in-flight loads. */
    private class SuspendingSource : StreamSource {
        private val gate = CompletableDeferred<Unit>()
        val resolvedIds = mutableListOf<String>()

        override suspend fun resolve(channel: Channel): ResolvedStream {
            resolvedIds += channel.id
            gate.await()
            return ResolvedStream("https://example.com/${channel.id}.m3u8")
        }

        fun release() = gate.complete(Unit)
    }

    private fun ready(n: Int) = PlaybackState.Ready(ResolvedStream("https://example.com/$n.m3u8"))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts idle without resolving anything`() {
        val source = FakeSource()
        val viewModel = PlayerViewModel(source)

        assertEquals(PlayerUiState(), viewModel.uiState.value)
        assertEquals(0, source.calls)
    }

    @Test
    fun `play resolves the channel`() {
        val source = FakeSource(true)
        val viewModel = PlayerViewModel(source)

        viewModel.play(espn)

        assertEquals(PlayerUiState(espn, ready(1)), viewModel.uiState.value)
        assertEquals(listOf("espn"), source.resolvedIds)
    }

    @Test
    fun `stop cancels an in-flight load and goes idle`() {
        val source = SuspendingSource()
        val viewModel = PlayerViewModel(source)
        viewModel.play(espn)
        assertEquals(PlaybackState.Loading, viewModel.uiState.value.playback)

        viewModel.stop()
        source.release()

        assertEquals(PlayerUiState(), viewModel.uiState.value)
    }

    @Test
    fun `playing another channel during a load replaces it`() {
        val source = SuspendingSource()
        val viewModel = PlayerViewModel(source)

        viewModel.play(espn)
        viewModel.play(dsports)
        source.release()

        val state = viewModel.uiState.value
        assertEquals(dsports, state.currentChannel)
        assertEquals(PlaybackState.Ready(ResolvedStream("https://example.com/dsports.m3u8")), state.playback)
    }

    @Test
    fun `shows error when resolution fails and recovers on retry`() {
        val viewModel = PlayerViewModel(FakeSource(false, true))
        viewModel.play(espn)
        assertEquals(PlaybackState.Error("boom"), viewModel.uiState.value.playback)

        viewModel.retry()

        assertEquals(ready(2), viewModel.uiState.value.playback)
    }

    @Test
    fun `first playback error re-resolves silently, second one surfaces`() {
        val source = FakeSource(true, true)
        val viewModel = PlayerViewModel(source)
        viewModel.play(espn)

        viewModel.onPlaybackError("ERROR_CODE_IO_BAD_HTTP_STATUS")
        assertEquals(ready(2), viewModel.uiState.value.playback)

        viewModel.onPlaybackError("ERROR_CODE_IO_BAD_HTTP_STATUS")
        assertEquals(PlaybackState.Error("ERROR_CODE_IO_BAD_HTTP_STATUS"), viewModel.uiState.value.playback)
        assertEquals(2, source.calls)
    }

    @Test
    fun `successful playback re-arms silent recovery`() {
        val source = FakeSource(true, true, true)
        val viewModel = PlayerViewModel(source)
        viewModel.play(espn)

        viewModel.onPlaybackError("expired")
        viewModel.onPlaybackStarted()
        viewModel.onPlaybackError("expired again")

        assertEquals(ready(3), viewModel.uiState.value.playback)
    }

    @Test
    fun `playing a new channel re-arms silent recovery`() {
        val source = FakeSource(true, true, true, true)
        val viewModel = PlayerViewModel(source)
        viewModel.play(espn)
        viewModel.onPlaybackError("expired")

        viewModel.play(dsports)
        viewModel.onPlaybackError("expired")

        assertEquals(ready(4), viewModel.uiState.value.playback)
    }
}
