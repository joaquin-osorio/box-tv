package com.boxtv.menu

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.boxtv.R
import com.boxtv.source.Channel

/**
 * Today's agenda, grouped into Earlier / Live / Coming up. OK on an event plays its only signal, or
 * expands its signals when it has several (focus jumps to the first one); OK again or Back collapses.
 *
 * [entryFocus] goes to the item with [entryKey] (the last one played from here) or, the first time,
 * to the first event that hasn't finished; the list initially scrolls to it, so finished events sit
 * above. [onPlay] receives the key of the item that was clicked, to be handed back as [entryKey].
 */
@Composable
fun EventsTab(
    state: ScheduleState,
    expandedEventId: String?,
    entryKey: String?,
    entryFocus: FocusRequester,
    onToggleEvent: (String) -> Unit,
    onCollapse: () -> Unit,
    onPlay: (key: String, channel: Channel) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Retry replaces the focused button with a loading message; once a result is in, focus the new content.
    var retried by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (retried && state != ScheduleState.Loading) {
            withFrameNanos { }
            entryFocus.requestFocus()
            retried = false
        }
    }

    when (state) {
        ScheduleState.Loading -> Message(stringResource(R.string.events_loading), modifier)

        ScheduleState.Error -> Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = modifier.padding(vertical = 24.dp)
        ) {
            Text(text = stringResource(R.string.events_error), style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = {
                    retried = true
                    onRetry()
                },
                modifier = Modifier.focusRequester(entryFocus)
            ) {
                Text(text = stringResource(R.string.player_retry))
            }
        }

        is ScheduleState.Loaded -> if (state.items.isEmpty()) {
            Message(stringResource(R.string.events_empty), modifier)
        } else {
            EventList(
                items = state.items,
                expandedEventId = expandedEventId,
                entryKey = entryKey,
                entryFocus = entryFocus,
                onToggleEvent = onToggleEvent,
                onCollapse = onCollapse,
                onPlay = onPlay,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun EventList(
    items: List<EventItem>,
    expandedEventId: String?,
    entryKey: String?,
    entryFocus: FocusRequester,
    onToggleEvent: (String) -> Unit,
    onCollapse: () -> Unit,
    onPlay: (key: String, channel: Channel) -> Unit,
    modifier: Modifier
) {
    val rows = remember(items, expandedEventId) { buildRows(items, expandedEventId) }
    val entryIndex = rows.indexOfFirst { it.key == entryKey }.takeIf { it >= 0 } ?: defaultEntryIndex(rows)
    val timeFormat = rememberTimeFormatter()

    val listState = rememberLazyListState()
    var positioned by rememberSaveable { mutableStateOf(false) }
    val expandedEventFocus = remember { FocusRequester() }
    val firstSignalFocus = remember { FocusRequester() }
    var focusFirstSignal by remember { mutableStateOf(false) }
    var listFocused by remember { mutableStateOf(false) }

    // First time only: start at the current part of the day, keeping one item of context above.
    LaunchedEffect(Unit) {
        if (!positioned) {
            listState.scrollToItem((entryIndex - 1).coerceAtLeast(0))
            positioned = true
        }
    }
    LaunchedEffect(focusFirstSignal, expandedEventId) {
        if (focusFirstSignal && expandedEventId != null) {
            withFrameNanos { }
            firstSignalFocus.requestFocus()
            focusFirstSignal = false
        }
    }
    // Focus the event before collapsing it: its signal rows (likely focused) are about to disappear.
    BackHandler(enabled = expandedEventId != null && listFocused) {
        expandedEventFocus.requestFocus()
        onCollapse()
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .onFocusChanged { listFocused = it.hasFocus }
            .focusRestorer(entryFocus)
    ) {
        itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
            var rowModifier = Modifier.animateItem()
            if (index == entryIndex) rowModifier = rowModifier.focusRequester(entryFocus)

            when (row) {
                is ListRow.Header -> Text(
                    text = stringResource(row.section.title),
                    style = MaterialTheme.typography.titleSmall,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                    modifier = rowModifier.padding(start = 8.dp, top = if (index == 0) 0.dp else 16.dp)
                )

                is ListRow.Event -> {
                    val event = row.item.event
                    val expanded = event.id == expandedEventId
                    EventRow(
                        item = row.item,
                        startTime = timeFormat.format(event.startsAt),
                        expanded = expanded,
                        onClick = {
                            when (event.channels.size) {
                                0 -> Unit
                                1 -> onPlay(row.key, event.channels.single())
                                else -> {
                                    focusFirstSignal = !expanded
                                    onToggleEvent(event.id)
                                }
                            }
                        },
                        modifier = if (expanded) rowModifier.focusRequester(expandedEventFocus) else rowModifier
                    )
                }

                is ListRow.Signal -> SignalRow(
                    channel = row.channel,
                    onClick = { onPlay(row.key, row.channel) },
                    modifier = if (row.isFirst) rowModifier.focusRequester(firstSignalFocus) else rowModifier
                )
            }
        }
    }
}

@Composable
private fun Message(text: String, modifier: Modifier) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(vertical = 24.dp))
}

private enum class Section(@StringRes val title: Int) {
    Earlier(R.string.events_section_earlier),
    Live(R.string.events_section_live),
    Upcoming(R.string.events_section_upcoming)
}

private val EventStatus.section
    get() = when (this) {
        EventStatus.Finished -> Section.Earlier
        EventStatus.Live -> Section.Live
        is EventStatus.Upcoming -> Section.Upcoming
    }

/** The flattened list: section headers, events, and the expanded event's signals right below it. */
private sealed interface ListRow {
    val key: String

    data class Header(val section: Section) : ListRow {
        override val key = "header:${section.name}"
    }

    data class Event(val item: EventItem) : ListRow {
        override val key = "event:${item.event.id}"
    }

    data class Signal(val eventId: String, val channel: Channel, val isFirst: Boolean) : ListRow {
        override val key = "signal:$eventId:${channel.id}"
    }
}

/** Items come sorted by start time, so statuses (and thus sections) are already contiguous. */
private fun buildRows(items: List<EventItem>, expandedEventId: String?): List<ListRow> = buildList {
    var section: Section? = null
    for (item in items) {
        if (item.status.section != section) {
            section = item.status.section
            add(ListRow.Header(section))
        }
        add(ListRow.Event(item))
        if (item.event.id == expandedEventId) {
            item.event.channels.forEachIndexed { i, channel ->
                add(ListRow.Signal(item.event.id, channel, isFirst = i == 0))
            }
        }
    }
}

/** The first event that hasn't finished, or the last event when they all have. */
private fun defaultEntryIndex(rows: List<ListRow>): Int {
    val events = rows.withIndex().filter { it.value is ListRow.Event }
    return (
        events.firstOrNull { (it.value as ListRow.Event).item.status != EventStatus.Finished }
            ?: events.last()
        ).index
}
