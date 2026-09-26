package com.boxtv.player

import com.boxtv.source.Channel
import com.boxtv.source.ResolvedStream
import com.boxtv.source.StreamResolutionException
import com.boxtv.source.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val espn = Channel(id = "espn", title = "ESPN")
    private val dsports = Channel(id = "dsports", title = "DSports")
    private val channels = listOf(espn, dsports)

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
    fun `resolves the first channel on start`() {
        val source = FakeSource(true)
        val viewModel = PlayerViewModel(source, channels)

        assertEquals(espn, viewModel.uiState.value.currentChannel)
        assertEquals(ready(1), viewModel.uiState.value.playback)
        assertEquals(listOf("espn"), source.resolvedIds)
    }

    @Test
    fun `shows error when resolution fails and recovers on retry`() {
        val viewModel = PlayerViewModel(FakeSource(false, true), channels)
        assertEquals(PlaybackState.Error("boom"), viewModel.uiState.value.playback)

        viewModel.retry()

        assertEquals(ready(2), viewModel.uiState.value.playback)
    }

    @Test
    fun `first playback error re-resolves silently, second one surfaces`() {
        val source = FakeSource(true, true)
        val viewModel = PlayerViewModel(source, channels)

        viewModel.onPlaybackError("ERROR_CODE_IO_BAD_HTTP_STATUS")
        assertEquals(ready(2), viewModel.uiState.value.playback)

        viewModel.onPlaybackError("ERROR_CODE_IO_BAD_HTTP_STATUS")
        assertEquals(PlaybackState.Error("ERROR_CODE_IO_BAD_HTTP_STATUS"), viewModel.uiState.value.playback)
        assertEquals(2, source.calls)
    }

    @Test
    fun `successful playback re-arms silent recovery`() {
        val source = FakeSource(true, true, true)
        val viewModel = PlayerViewModel(source, channels)

        viewModel.onPlaybackError("expired")
        viewModel.onPlaybackStarted()
        viewModel.onPlaybackError("expired again")

        assertEquals(ready(3), viewModel.uiState.value.playback)
    }

    @Test
    fun `selecting another channel resolves it and closes the menu`() {
        val source = FakeSource(true, true)
        val viewModel = PlayerViewModel(source, channels)
        viewModel.openMenu()

        viewModel.selectChannel(dsports)

        val state = viewModel.uiState.value
        assertEquals(dsports, state.currentChannel)
        assertEquals(ready(2), state.playback)
        assertFalse(state.isMenuOpen)
        assertEquals(listOf("espn", "dsports"), source.resolvedIds)
    }

    @Test
    fun `selecting the current channel only reloads it when it failed`() {
        val source = FakeSource(true, false, true)
        val viewModel = PlayerViewModel(source, channels)

        viewModel.selectChannel(espn)
        assertEquals(1, source.calls)

        viewModel.onPlaybackError("silent")
        viewModel.onPlaybackError("surfaced")
        viewModel.selectChannel(espn)

        assertEquals(ready(3), viewModel.uiState.value.playback)
    }

    @Test
    fun `switching channel re-arms silent recovery`() {
        val source = FakeSource(true, true, true, true)
        val viewModel = PlayerViewModel(source, channels)
        viewModel.onPlaybackError("expired")

        viewModel.selectChannel(dsports)
        viewModel.onPlaybackError("expired")

        assertEquals(ready(4), viewModel.uiState.value.playback)
    }

    @Test
    fun `menu closes itself after the inactivity timeout`() = runTest {
        val viewModel = PlayerViewModel(FakeSource(true), channels)

        viewModel.openMenu()
        advanceTimeBy(PlayerViewModel.MENU_TIMEOUT.inWholeMilliseconds - 1)
        assertTrue(viewModel.uiState.value.isMenuOpen)

        advanceTimeBy(1)
        runCurrent()
        assertFalse(viewModel.uiState.value.isMenuOpen)
    }

    @Test
    fun `interaction postpones the menu timeout`() = runTest {
        val viewModel = PlayerViewModel(FakeSource(true), channels)
        val timeout = PlayerViewModel.MENU_TIMEOUT.inWholeMilliseconds

        viewModel.openMenu()
        advanceTimeBy(timeout - 1_000)
        viewModel.onMenuInteraction()
        advanceTimeBy(timeout - 1)
        assertTrue(viewModel.uiState.value.isMenuOpen)

        advanceTimeBy(1)
        runCurrent()
        assertFalse(viewModel.uiState.value.isMenuOpen)
    }

    @Test
    fun `interaction does not reopen a closed menu`() = runTest {
        val viewModel = PlayerViewModel(FakeSource(true), channels)

        viewModel.onMenuInteraction()

        assertFalse(viewModel.uiState.value.isMenuOpen)
    }
}
