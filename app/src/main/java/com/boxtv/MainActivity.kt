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
import com.boxtv.menu.MenuRoute
import com.boxtv.menu.MenuViewModel
import com.boxtv.player.PlayerScreen
import com.boxtv.player.PlayerViewModel
import com.boxtv.source.RoutingStreamSource
import com.boxtv.source.tvf90.Tvf90Schedule
import com.boxtv.source.tvf90.Tvf90Source

class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels {
        viewModelFactory {
            initializer { PlayerViewModel(RoutingStreamSource(mapOf(Tvf90Source.ID to Tvf90Source()))) }
        }
    }
    private val menuViewModel: MenuViewModel by viewModels {
        viewModelFactory { initializer { MenuViewModel(Tvf90Schedule()) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BoxTvApp(playerViewModel, menuViewModel)
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
private fun BoxTvApp(player: PlayerViewModel, menu: MenuViewModel) {
    val state by player.uiState.collectAsStateWithLifecycle()
    val screens = rememberSaveableStateHolder()
    val channel = state.currentChannel

    if (channel == null) {
        screens.SaveableStateProvider("menu") {
            MenuRoute(viewModel = menu, channels = ChannelLineup, onPlay = player::play)
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
