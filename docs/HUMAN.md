# Box TV — overview

## What it does
Android TV app that plays web live streams as if they were native TV channels. Current state (MVP):
opening the app shows a home menu with two tabs: **Channels** (a fixed lineup of six sports channels
from tvf90, `ChannelLineup`) and **Events** (today's sports agenda from two sites, tvf90 and streamtp,
merged into one list, each event linked to the channels broadcasting it). Picking a channel or an event's signal plays it full screen, and Back
returns to the menu.

## How it works
1. `MainActivity` shows the home menu (`MenuScreen`) while nothing is playing, and the player once a
   channel is picked. "What is playing" lives in `PlayerViewModel` (`currentChannel`, null = menu), so
   that single value is the whole navigation.
2. Picking a channel asks the adapter of the channel's site (tvf90 or streamtp) to **resolve** it into a
   playable stream URL. This happens on every play because the sites hand out signed URLs that expire
   (tvf90 after ~5 hours; streamtp's are also tied to the device's public IP).
3. The adapter downloads the site's internal player page and pulls the HLS (`.m3u8`) URL out of an inline
   script (tvf90 additionally requires pretending to be its own embedding page via the `Referer` header).
   None of the sites' JavaScript — ads, popups, geo checks — is ever executed.
4. The player screen plays that URL with Media3/ExoPlayer, full screen, starting immediately. There are
   no playback controls: channels behave like live TV. **Back** stops playback and returns to the menu.

## Home menu
- Header (app name, today's date, clock) over a row of tabs: **Channels** (the lineup, numbered like TV
  channels) and **Events** (below).
- D-pad: focusing a tab selects it; **Down** enters its list, **Up** from the top of the list goes back to
  the tabs. **Back** inside a list jumps to the tabs; **Back** on the tabs leaves the app.
- Entering the tab row always lands on the selected tab (otherwise Up from a list could land on the
  other tab and switch to it).
- On launch, focus is on the first channel. Coming back from the player restores the tab, the scroll
  position and focus on the item that was played (saved state kept via `rememberSaveableStateHolder`
  while the player is showing).

## Events tab (today's agenda)
- Sources: the agenda JSON APIs of **tvf90** and **streamtp**, each behind the `EventSchedule` interface
  (separate from `StreamSource`, since agendas and streams could come from different sites).
  `MergedSchedule` fetches both at once and shows them as one list sorted by start time. The sites
  usually list different events; when both list the same match it simply appears twice (one per site).
  If one site fails, its last loaded agenda stays; the other keeps updating. Details per site in
  `docs/adapters/`.
- Each event's signals are channel ids of the same site, played by that site's adapter.
  - tvf90 lists several mirrors per signal ("OP2", "OP3", "HD"); they share one stream id, so the app
    shows them as a single signal. Events have a country flag.
  - streamtp lists one entry per signal; the app groups entries with the same time, competition and title
    into one event. It gives no channel names or flags: signals are named after the stream id
    (`DISNEY 1`, `TUDN USA`, plus the language for fights streamed in several: `PARAMOUNT 2 · English`),
    and the flag slot stays empty.
- Both sites give wall-clock start times without zone (Lima for tvf90, Panama for streamtp; both UTC−5);
  the app converts them and shows every time in the TV's own time zone.
- The site gives start times only, so **Live** is a guess: from the start until 2h30 later
  (`MenuViewModel.LIVE_WINDOW`). Events are grouped into *Earlier today* (dimmed), *Live now* and *Coming
  up* (with a countdown during the last hour).
- Refreshed when the menu shows and every 60 s while it stays on screen (`MenuViewModel.keepFresh`, run
  by the UI under `repeatOnLifecycle(STARTED)`), never while playing. A failed refresh keeps the last
  agenda; the error + Retry state only shows if nothing ever loaded.
- D-pad: the first time the tab opens, the list starts at the first event that hasn't finished (earlier
  ones sit above). **OK** on an event with one signal plays it; with several it expands the signals below
  it (one expanded at a time) and focuses the first; **OK** on the event again or **Back** collapses it.
  Events without signals yet are shown (and focusable) with "No signal yet".
- Coming back from the player restores the expanded event and focus on the signal that was played.

## Error handling
- If the URL can't be obtained (site down, page changed), the screen shows an error message and a
  focused **Retry** button (one OK press on the remote). Back returns to the menu to pick something else.
- If playback fails mid-stream (typically an expired token), the app silently fetches a fresh URL once
  and keeps playing; only if that also fails does it show the error screen.
- Falling behind the live window just jumps back to live. Coming back to the app after leaving it
  resumes at the live edge.

## Architecture
- Single Gradle module `:app`, MVVM, Jetpack Compose for TV.
- `source/` — `StreamSource` and `EventSchedule` interfaces (the only things the UI knows about), plus
  `RoutingStreamSource` (plays each channel with its site's adapter), `MergedSchedule` (one agenda out of
  several) and `FallbackDns`. One adapter package per site (`source/tvf90`, `source/streamtp`, each with a
  `…Source` and a `…Schedule`). When a site changes, only its adapter breaks. Adapter details live in
  `docs/adapters/`.
- `ChannelLineup.kt` — the hardcoded list of channels (all tvf90 ids, in menu order).
- `menu/` — `MenuViewModel` (agenda state: loading / loaded / error, live-status classification,
  refresh loop, expanded event), `MenuScreen` (header + tabs) and one composable per tab (`ChannelsTab`,
  `EventsTab` + `EventRow`). Lists use foundation's `LazyColumn`: `TvLazyColumn` is deprecated and D-pad
  bring-into-view is built in.
- `player/` — `PlayerViewModel` (state: current channel or none, playback idle / loading / ready /
  error; `play` / `stop`), `PlayerScreen` (stateless UI), `VideoPlayer` (wraps ExoPlayer + Media3
  `PlayerView`).
- Networking: OkHttp; JSON with kotlinx-serialization; streamtp requests use DNS-over-HTTPS as a
  fallback (see Key decisions); event flags loaded with Coil. Times use
  `java.time` (core library desugaring, since minSdk 23 predates it). Tests: JVM unit tests with
  MockWebServer and saved copies of the site's page and agenda.

## Key decisions
- No hardcoded stream URL: it expires, so it's resolved at runtime on the device itself.
- Regex over the inline script instead of an HTML parser (the URL lives in a JS variable, not in the DOM).
- No dependency injection framework or multi-module setup yet — not justified at this size.
- ExoPlayer's built-in controller is disabled. It grabs D-pad keys for its own buttons, and pause/seek add
  little on live channels.
- No navigation library: two screens switched by `currentChannel` don't justify one.
- The lineup is a fixed list inside the app, not fetched from the site.
- streamtp's domain is blocked by the home ISP's DNS (it answers "no such domain"). Its requests first try
  the normal DNS and, only if that fails, ask Cloudflare over HTTPS (DoH), which the ISP can't tamper
  with. Limited to streamtp so the rest of the app behaves like any other app on the network. The video
  servers themselves aren't blocked, so the player uses the normal DNS.
- Every channel carries the site that plays it (`Channel.source`); `RoutingStreamSource` hands it to that
  site's adapter, so the player never needs to know which site a channel comes from.
