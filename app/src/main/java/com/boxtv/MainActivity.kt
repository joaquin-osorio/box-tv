package com.boxtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.darkColorScheme
import com.boxtv.menu.MenuScreen
import com.boxtv.player.PlayerScreen
import com.boxtv.player.PlayerViewModel
import com.boxtv.source.tvf90.Tvf90Source

class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels {
        viewModelFactory { initializer { PlayerViewModel(Tvf90Source()) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BoxTvApp(playerViewModel)
                }
            }
        }
    }
}

/**
 * Two screens, switched by whether something is playing: the home menu, or the player on top of
 * it. The menu's saveable state (tab, scroll, last pick) is kept while the player is showing.
 */
@Composable
private fun BoxTvApp(player: PlayerViewModel) {
    val state by player.uiState.collectAsStateWithLifecycle()
    val screens = rememberSaveableStateHolder()
    val channel = state.currentChannel

    if (channel == null) {
        screens.SaveableStateProvider("menu") {
            MenuScreen(channels = ChannelLineup, onPlay = player::play)
        }
    } else {
        PlayerScreen(
            channel = channel,
            playback = state.playback,
            onRetry = player::retry,
            onPlaybackStarted = player::onPlaybackStarted,
            onPlaybackError = player::onPlaybackError,
            onBack = player::stop
        )
    }
}
