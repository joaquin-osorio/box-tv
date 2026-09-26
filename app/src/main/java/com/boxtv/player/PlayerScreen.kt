package com.boxtv.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.boxtv.R

@Composable
fun PlayerRoute(viewModel: PlayerViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlayerScreen(
        state = state.playback,
        channelTitle = state.currentChannel.title,
        onRetry = viewModel::retry,
        onPlaybackStarted = viewModel::onPlaybackStarted,
        onPlaybackError = viewModel::onPlaybackError
    )
}

@Composable
fun PlayerScreen(
    state: PlaybackState,
    channelTitle: String,
    onRetry: () -> Unit,
    onPlaybackStarted: () -> Unit,
    onPlaybackError: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state) {
            PlaybackState.Loading -> Text(
                text = stringResource(R.string.player_loading, channelTitle),
                style = MaterialTheme.typography.titleLarge
            )

            is PlaybackState.Ready -> VideoPlayer(
                stream = state.stream,
                onPlaybackStarted = onPlaybackStarted,
                onPlaybackError = onPlaybackError,
                modifier = Modifier.fillMaxSize()
            )

            is PlaybackState.Error -> PlaybackError(
                channelTitle = channelTitle,
                message = state.message,
                onRetry = onRetry
            )
        }
    }
}

@Composable
private fun PlaybackError(channelTitle: String, message: String, onRetry: () -> Unit) {
    val retryFocus = remember { FocusRequester() }
    // The only interactive element: focus it so a single OK press on the remote retries.
    LaunchedEffect(Unit) { retryFocus.requestFocus() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.player_error_title, channelTitle),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry, modifier = Modifier.focusRequester(retryFocus)) {
            Text(text = stringResource(R.string.player_retry))
        }
    }
}
