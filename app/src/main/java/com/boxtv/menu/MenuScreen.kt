package com.boxtv.menu

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.boxtv.R
import com.boxtv.source.Channel
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private enum class MenuTab(@StringRes val title: Int) {
    Channels(R.string.menu_tab_channels),
    Events(R.string.menu_tab_events)
}

/** Connects [MenuScreen] to [viewModel]; the agenda is refreshed only while the menu is started. */
@Composable
fun MenuRoute(viewModel: MenuViewModel, channels: List<Channel>, onPlay: (Channel) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.keepFresh() }
    }
    MenuScreen(
        channels = channels,
        state = state,
        onToggleEvent = viewModel::toggleEvent,
        onCollapseEvent = viewModel::collapse,
        onRetrySchedule = viewModel::retry,
        onPlay = onPlay
    )
}

/**
 * Home screen: a header, a row of tabs and the selected tab's list. Focusing a tab selects it (TV
 * convention); Down enters its list and Up from the top of the list goes back to the tabs. Back
 * inside a list returns to the tabs; Back on the tabs isn't handled, so it leaves the app.
 *
 * Tab choice, scroll positions and the last picked item are saved, so coming back from the player
 * lands exactly where the user left.
 */
@Composable
fun MenuScreen(
    channels: List<Channel>,
    state: MenuUiState,
    onToggleEvent: (Long) -> Unit,
    onCollapseEvent: () -> Unit,
    onRetrySchedule: () -> Unit,
    onPlay: (Channel) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(MenuTab.Channels) }
    var lastChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastEventKey by rememberSaveable { mutableStateOf<String?>(null) }
    val tabStates = rememberSaveableStateHolder()
    val tabFocus = remember { MenuTab.entries.associateWith { FocusRequester() } }
    val listEntry = remember { FocusRequester() }
    var listHasFocus by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Lazy list items attach during layout, so wait a frame before focusing one.
        withFrameNanos { }
        listEntry.requestFocus()
    }
    BackHandler(enabled = listHasFocus) { tabFocus.getValue(selectedTab).requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.background)
                )
            )
            .padding(horizontal = 48.dp, vertical = 27.dp)
    ) {
        MenuHeader()
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            modifier = Modifier
                .padding(top = 20.dp)
                // Entering the row from the list must land on the selected tab, not the one nearest
                // to the focused row: focusing a tab switches to it.
                .focusRestorer(tabFocus.getValue(selectedTab))
        ) {
            MenuTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selectedTab,
                    onFocus = { selectedTab = tab },
                    modifier = Modifier.focusRequester(tabFocus.getValue(tab))
                ) {
                    Text(
                        text = stringResource(tab.title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onFocusChanged { listHasFocus = it.hasFocus }
        ) {
            tabStates.SaveableStateProvider(selectedTab.name) {
                when (selectedTab) {
                    MenuTab.Channels -> ChannelsTab(
                        channels = channels,
                        entryChannelId = lastChannelId,
                        entryFocus = listEntry,
                        onPlay = { channel ->
                            lastChannelId = channel.id
                            onPlay(channel)
                        }
                    )

                    MenuTab.Events -> EventsTab(
                        state = state.schedule,
                        expandedEventId = state.expandedEventId,
                        entryKey = lastEventKey,
                        entryFocus = listEntry,
                        onToggleEvent = onToggleEvent,
                        onCollapse = onCollapseEvent,
                        onPlay = { key, channel ->
                            lastEventKey = key
                            onPlay(channel)
                        },
                        onRetry = onRetrySchedule
                    )
                }
            }
        }
    }
}

/** App name on the left; today's date and a minute-accurate clock on the right. */
@Composable
private fun MenuHeader() {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay(MILLIS_PER_MINUTE - System.currentTimeMillis() % MILLIS_PER_MINUTE)
            value = LocalDateTime.now()
        }
    }
    val locale = Locale.getDefault()
    val dateFormat = remember(locale) { localizedFormatter(locale, "EEEEdMMMM") }
    val timeFormat = rememberTimeFormatter()

    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                text = now.format(dateFormat).replaceFirstChar { it.titlecase(locale) },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = now.format(timeFormat), style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Hours and minutes in the device's locale and 12/24-hour setting, in the device's time zone. */
@Composable
internal fun rememberTimeFormatter(): DateTimeFormatter {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val is24Hour = DateFormat.is24HourFormat(context)
    return remember(locale, is24Hour) {
        localizedFormatter(locale, if (is24Hour) "Hm" else "hm").withZone(ZoneId.systemDefault())
    }
}

/** A formatter with the locale's own field order for [skeleton] (e.g. "Friday 25 September" vs "Friday, September 25"). */
internal fun localizedFormatter(locale: Locale, skeleton: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

private const val MILLIS_PER_MINUTE = 60_000L
