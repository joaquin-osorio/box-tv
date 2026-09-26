package com.boxtv.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.boxtv.R
import com.boxtv.source.Channel

/**
 * One event of the agenda: start time, flag, competition and match, then its status and signals.
 * Finished events are dimmed unless focused. [expanded] only flips the signals chevron; the signal
 * rows themselves are separate list items ([SignalRow]).
 */
@Composable
fun EventRow(
    item: EventItem,
    startTime: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val event = item.event

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(RowShape),
        colors = rowColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (item.status == EventStatus.Finished && !focused) 0.5f else 1f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = RowPadding, vertical = 12.dp)
        ) {
            Text(
                text = startTime,
                style = MaterialTheme.typography.titleLarge.tabular(),
                modifier = Modifier.width(TimeWidth)
            )
            Flag(event.flagUrl)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                event.competition?.let { competition ->
                    Text(
                        text = competition.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp)
            ) {
                StatusBadge(item.status)
                SignalsSummary(event.channels, expanded)
            }
        }
    }
}

/** One signal of an expanded event, indented under the event's flag column. */
@Composable
fun SignalRow(channel: Channel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RowShape),
        colors = rowColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = RowPadding + TimeWidth)
    ) {
        Text(
            text = "▶  ${channel.title}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun Flag(url: String?) {
    val flagModifier = Modifier
        .size(28.dp)
        .clip(RoundedCornerShape(6.dp))
    if (url == null) {
        Spacer(flagModifier)
    } else {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = flagModifier)
    }
}

@Composable
private fun StatusBadge(status: EventStatus) {
    when {
        status == EventStatus.Live -> Text(
            text = "● " + stringResource(R.string.events_live),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier
                .background(LiveRed, RoundedCornerShape(percent = 50))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        )

        status is EventStatus.Upcoming && status.minutes <= SOON_MINUTES -> Text(
            text = stringResource(R.string.events_starts_in, status.minutes),
            style = MaterialTheme.typography.labelLarge,
            color = LocalContentColor.current.copy(alpha = 0.8f)
        )
    }
}

/** No signal yet / the only signal's name / "N signals" with a chevron that flips when expanded. */
@Composable
private fun SignalsSummary(channels: List<Channel>, expanded: Boolean) {
    val text = when (channels.size) {
        0 -> stringResource(R.string.events_no_signal)
        1 -> channels.single().title
        else -> pluralStringResource(R.plurals.events_signal_count, channels.size, channels.size) +
            if (expanded) "  ▴" else "  ▾"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = LocalContentColor.current.copy(alpha = if (channels.isEmpty()) 0.5f else 0.8f),
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(SummaryWidth)
    )
}

/** Translucent cards that turn light when focused (tv-material's inverse colors), as the channel list does. */
@Composable
private fun rowColors() = ClickableSurfaceDefaults.colors(
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
    contentColor = MaterialTheme.colorScheme.onSurface,
    focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
    focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
    pressedContainerColor = MaterialTheme.colorScheme.inverseSurface,
    pressedContentColor = MaterialTheme.colorScheme.inverseOnSurface
)

private val RowShape = RoundedCornerShape(12.dp)
private val RowPadding = 20.dp
private val TimeWidth = 88.dp
private val SummaryWidth = 160.dp
private val LiveRed = Color(0xFFE53935)

/** Upcoming events show a countdown only within this many minutes; later ones just show their time. */
private const val SOON_MINUTES = 60
