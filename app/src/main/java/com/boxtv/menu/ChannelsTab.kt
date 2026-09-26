package com.boxtv.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.boxtv.source.Channel

/**
 * The fixed channel lineup, numbered like TV channels. [entryFocus] is attached to the channel with
 * [entryChannelId] (or the first one): it takes focus when the list is shown and whenever focus
 * enters the list for the first time; afterwards the list restores its last focused row.
 */
@Composable
fun ChannelsTab(
    channels: List<Channel>,
    entryChannelId: String?,
    entryFocus: FocusRequester,
    onPlay: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val entryIndex = channels.indexOfFirst { it.id == entryChannelId }.coerceAtLeast(0)

    // Foundation's LazyColumn: TvLazyColumn is deprecated, and D-pad bring-into-view is built in now.
    LazyColumn(
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .widthIn(max = 560.dp)
            .focusRestorer(entryFocus)
    ) {
        itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
            ListItem(
                selected = false,
                onClick = { onPlay(channel) },
                leadingContent = {
                    Text(
                        text = "%02d".format(index + 1),
                        style = MaterialTheme.typography.titleMedium.tabular(),
                        // Derived from the row's content color so it stays readable on the focused (light) row.
                        color = LocalContentColor.current.copy(alpha = 0.6f)
                    )
                },
                headlineContent = { Text(channel.title, style = MaterialTheme.typography.titleMedium) },
                modifier = if (index == entryIndex) Modifier.focusRequester(entryFocus) else Modifier
            )
        }
    }
}

/** Fixed-width digits, so numbers and times line up down a list. */
internal fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")
