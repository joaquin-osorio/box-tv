package com.boxtv.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.boxtv.R
import com.boxtv.source.Channel

@Composable
fun PlayerRoute(viewModel: PlayerViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlayerScreen(
        state = state,
        onRetry = viewModel::retry,
        onPlaybackStarted = viewModel::onPlaybackStarted,
        onPlaybackError = viewModel::onPlaybackError,
        onOpenMenu = viewModel::openMenu,
        onCloseMenu = viewModel::closeMenu,
        onMenuInteraction = viewModel::onMenuInteraction,
        onSelectChannel = viewModel::selectChannel
    )
}

/**
 * Full-screen playback with the channel menu floating on top. While the menu is closed the root box
 * holds focus (or the Retry button, on error) and D-pad Left opens the menu. While it's open, only the
 * menu is focusable, so focus can't leak to the content underneath.
 */
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    onRetry: () -> Unit,
    onPlaybackStarted: () -> Unit,
    onPlaybackError: (String) -> Unit,
    onOpenMenu: () -> Unit,
    onCloseMenu: () -> Unit,
    onMenuInteraction: () -> Unit,
    onSelectChannel: (Channel) -> Unit
) {
    val rootFocus = remember { FocusRequester() }
    val isError = state.playback is PlaybackState.Error
    val contentFocusable = !state.isMenuOpen

    // The error screen focuses its own Retry button; otherwise the root takes focus back.
    LaunchedEffect(contentFocusable, isError) {
        if (contentFocusable && !isError) rootFocus.requestFocus()
    }
    BackHandler(enabled = state.isMenuOpen, onBack = onCloseMenu)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                val opensMenu = !state.isMenuOpen &&
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionLeft
                if (opensMenu) onOpenMenu()
                opensMenu
            }
            .focusProperties { canFocus = contentFocusable }
            .focusRequester(rootFocus)
            .focusable()
    ) {
        when (val playback = state.playback) {
            PlaybackState.Loading -> Text(
                text = stringResource(R.string.player_loading, state.currentChannel.title),
                style = MaterialTheme.typography.titleLarge
            )

            is PlaybackState.Ready -> VideoPlayer(
                stream = playback.stream,
                onPlaybackStarted = onPlaybackStarted,
                onPlaybackError = onPlaybackError,
                modifier = Modifier.fillMaxSize()
            )

            is PlaybackState.Error -> PlaybackError(
                channelTitle = state.currentChannel.title,
                message = playback.message,
                focusable = contentFocusable,
                onRetry = onRetry
            )
        }

        AnimatedVisibility(
            visible = state.isMenuOpen,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            ChannelMenu(
                channels = state.channels,
                current = state.currentChannel,
                onSelect = onSelectChannel,
                onInteraction = onMenuInteraction,
                onDismiss = onCloseMenu
            )
        }
    }
}

@Composable
private fun PlaybackError(channelTitle: String, message: String, focusable: Boolean, onRetry: () -> Unit) {
    val retryFocus = remember { FocusRequester() }
    // The only interactive element: focus it so a single OK press on the remote retries. Re-run when
    // the channel menu closes, since focus was inside the menu.
    LaunchedEffect(focusable) { if (focusable) retryFocus.requestFocus() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.player_error_title, channelTitle),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Button(
            onClick = onRetry,
            modifier = Modifier
                .focusProperties { canFocus = focusable }
                .focusRequester(retryFocus)
        ) {
            Text(text = stringResource(R.string.player_retry))
        }
    }
}
