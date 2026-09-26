package com.boxtv.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxtv.source.Channel
import com.boxtv.source.ResolvedStream
import com.boxtv.source.StreamResolutionException
import com.boxtv.source.StreamSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data object Loading : PlaybackState
    data class Ready(val stream: ResolvedStream) : PlaybackState
    data class Error(val message: String) : PlaybackState
}

/** [currentChannel] is null while nothing is playing (the home menu is showing). */
data class PlayerUiState(val currentChannel: Channel? = null, val playback: PlaybackState = PlaybackState.Idle)

/** Plays one channel at a time, from [play] until [stop]. */
class PlayerViewModel(private val source: StreamSource) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    // Stream URLs carry expiring tokens, so the first playback failure is answered with a silent
    // re-resolve. Re-armed once playback succeeds, so a later expiry can recover again.
    private var autoRecoveryAvailable = true

    fun play(channel: Channel) {
        _uiState.value = PlayerUiState(currentChannel = channel)
        retry()
    }

    fun stop() {
        loadJob?.cancel()
        _uiState.value = PlayerUiState()
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

    private fun load() {
        loadJob?.cancel()
        val channel = _uiState.value.currentChannel ?: return
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(playback = PlaybackState.Loading) }
            val playback = try {
                PlaybackState.Ready(source.resolve(channel))
            } catch (e: StreamResolutionException) {
                PlaybackState.Error(e.message ?: "Could not load the stream")
            }
            _uiState.update { it.copy(playback = playback) }
        }
    }
}
