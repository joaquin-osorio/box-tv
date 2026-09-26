package com.boxtv.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.boxtv.R
import com.boxtv.source.Channel

/**
 * Full-screen playback of [channel]. The root box holds focus (or the Retry button, on error) so no
 * D-pad key reaches the video view; Back leaves through [onBack].
 */
@Composable
fun PlayerScreen(
    channel: Channel,
    playback: PlaybackState,
    onRetry: () -> Unit,
    onPlaybackStarted: () -> Unit,
    onPlaybackError: (String) -> Unit,
    onBack: () -> Unit
) {
    val rootFocus = remember { FocusRequester() }
    val isError = playback is PlaybackState.Error

    // The error screen focuses its own Retry button; otherwise the root takes focus back.
    LaunchedEffect(isError) {
        if (!isError) rootFocus.requestFocus()
    }
    BackHandler(onBack = onBack)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(rootFocus)
            .focusable()
    ) {
        when (playback) {
            PlaybackState.Idle, PlaybackState.Loading -> Text(
                text = stringResource(R.string.player_loading, channel.title),
                style = MaterialTheme.typography.titleLarge
            )

            is PlaybackState.Ready -> VideoPlayer(
                stream = playback.stream,
                onPlaybackStarted = onPlaybackStarted,
                onPlaybackError = onPlaybackError,
                modifier = Modifier.fillMaxSize()
            )

            is PlaybackState.Error -> PlaybackError(
                channelTitle = channel.title,
                message = playback.message,
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
