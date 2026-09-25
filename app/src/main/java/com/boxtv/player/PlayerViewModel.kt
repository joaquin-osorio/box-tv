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
import kotlinx.coroutines.launch

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Ready(val stream: ResolvedStream) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}

class PlayerViewModel(private val source: StreamSource, val channel: Channel) : ViewModel() {

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

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
            _uiState.value = PlayerUiState.Error(message)
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading
            _uiState.value = try {
                PlayerUiState.Ready(source.resolve(channel))
            } catch (e: StreamResolutionException) {
                PlayerUiState.Error(e.message ?: "Could not load the stream")
            }
        }
    }
}
