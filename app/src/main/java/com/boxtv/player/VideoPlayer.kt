package com.boxtv.player

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.boxtv.source.ResolvedStream

/**
 * Full-screen live HLS playback of [stream], autoplaying. Owns the ExoPlayer instance: a new one is
 * built per [stream] and released when leaving composition. Fatal errors are reported through
 * [onPlaybackError]; falling behind the live window is recovered locally.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    stream: ResolvedStream,
    onPlaybackStarted: () -> Unit,
    onPlaybackError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentOnStarted by rememberUpdatedState(onPlaybackStarted)
    val currentOnError by rememberUpdatedState(onPlaybackError)

    val player = remember(stream) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(stream.headers)
            .setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
            .apply {
                setMediaItem(
                    MediaItem.Builder()
                        .setUri(stream.url)
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                        .build()
                )
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) currentOnStarted()
            }

            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition()
                    player.prepare()
                } else {
                    currentOnError(error.errorCodeName)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Pause while the app is in the background; on return jump back to the live edge.
    LifecycleStartEffect(player) {
        player.seekToDefaultPosition()
        player.play()
        onStopOrDispose { player.pause() }
    }

    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                keepScreenOn = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                setShowFastForwardButton(false)
                setShowRewindButton(false)
                setShowPreviousButton(false)
                setShowNextButton(false)
                // Receives D-pad keys so pressing any remote button reveals the controls.
                isFocusable = true
                requestFocus()
            }
        },
        update = { view -> view.player = player },
        modifier = modifier
    )
}
