package com.boxtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.darkColorScheme
import com.boxtv.player.PlayerRoute
import com.boxtv.player.PlayerViewModel
import com.boxtv.source.tvf90.Tvf90Source

class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels {
        viewModelFactory { initializer { PlayerViewModel(Tvf90Source(), DefaultChannel) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PlayerRoute(playerViewModel)
                }
            }
        }
    }
}
