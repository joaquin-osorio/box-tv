package com.boxtv.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxtv.source.Channel
import com.boxtv.source.ResolvedStream
import com.boxtv.source.StreamResolutionException
import com.boxtv.source.StreamSource
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PlaybackState {
    data object Loading : PlaybackState
    data class Ready(val stream: ResolvedStream) : PlaybackState
    data class Error(val message: String) : PlaybackState
}

data class PlayerUiState(
    val channels: List<Channel>,
    val currentChannel: Channel,
    val playback: PlaybackState = PlaybackState.Loading,
    val isMenuOpen: Boolean = false
)

/** Plays one channel of [channels] at a time (the first on start) and owns the channel menu state. */
class PlayerViewModel(private val source: StreamSource, channels: List<Channel>) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState(channels = channels, currentChannel = channels.first()))
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var menuTimeoutJob: Job? = null

    // Stream URLs carry expiring tokens, so the first playback failure is answered with a silent
    // re-resolve. Re-armed once playback succeeds, so a later expiry can recover again.
    private var autoRecoveryAvailable = true

    init {
        load()
    }

    fun retry() {
        autoRecoveryAvailable = true
        load()
    }

    fun onPlaybackStarted() {
        autoRecoveryAvailable = true
    }

    fun onPlaybackError(message: String) {
        if (autoRecoveryAvailable) {
            autoRecoveryAvailable = false
            load()
        } else {
            _uiState.update { it.copy(playback = PlaybackState.Error(message)) }
        }
    }

    /** Closes the menu and switches to [channel]. Re-picking the current channel only reloads it if it failed. */
    fun selectChannel(channel: Channel) {
        closeMenu()
        val state = _uiState.value
        if (channel == state.currentChannel && state.playback !is PlaybackState.Error) return
        _uiState.update { it.copy(currentChannel = channel) }
        retry()
    }

    fun openMenu() {
        _uiState.update { it.copy(isMenuOpen = true) }
        restartMenuTimeout()
    }

    fun closeMenu() {
        menuTimeoutJob?.cancel()
        _uiState.update { it.copy(isMenuOpen = false) }
    }

    /** Any key press inside the menu: postpones the auto-close. */
    fun onMenuInteraction() {
        if (_uiState.value.isMenuOpen) restartMenuTimeout()
    }

    private fun restartMenuTimeout() {
        menuTimeoutJob?.cancel()
        menuTimeoutJob = viewModelScope.launch {
            delay(MENU_TIMEOUT)
            closeMenu()
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val channel = _uiState.value.currentChannel
            _uiState.update { it.copy(playback = PlaybackState.Loading) }
            val playback = try {
                PlaybackState.Ready(source.resolve(channel))
            } catch (e: StreamResolutionException) {
                PlaybackState.Error(e.message ?: "Could not load the stream")
            }
            _uiState.update { it.copy(playback = playback) }
        }
    }

    companion object {
        val MENU_TIMEOUT = 10.seconds
    }
}
