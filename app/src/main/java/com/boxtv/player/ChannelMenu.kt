package com.boxtv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.boxtv.R
import com.boxtv.source.Channel

/**
 * Floating channel list anchored to the left edge. Grabs focus on [current] when shown. Every key
 * press inside it is reported through [onInteraction] (to postpone the auto-close); Right dismisses it,
 * mirroring the Left press that opens it. Back is handled by the caller.
 */
@Composable
fun ChannelMenu(
    channels: List<Channel>,
    current: Channel,
    onSelect: (Channel) -> Unit,
    onInteraction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { currentFocus.requestFocus() }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .padding(horizontal = 16.dp, vertical = 32.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                onInteraction()
                when (event.key) {
                    Key.DirectionRight -> {
                        onDismiss()
                        true
                    }
                    // Nothing to the left of the menu; swallow it so focus can't wander.
                    Key.DirectionLeft -> true
                    else -> false
                }
            }
    ) {
        Text(
            text = stringResource(R.string.channel_menu_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
        )
        channels.forEach { channel ->
            val isCurrent = channel == current
            ListItem(
                selected = isCurrent,
                onClick = { onSelect(channel) },
                headlineContent = { Text(channel.title) },
                modifier = if (isCurrent) Modifier.focusRequester(currentFocus) else Modifier
            )
        }
    }
}
