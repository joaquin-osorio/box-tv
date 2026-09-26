# Box TV — overview

## What it does
Android TV app that plays web live streams as if they were native TV channels. Current state (MVP):
opening the app shows a home menu listing a fixed lineup of six sports channels from tvf90
(`ChannelLineup`); picking one plays it full screen, and Back returns to the menu.

## How it works
1. `MainActivity` shows the home menu (`MenuScreen`) while nothing is playing, and the player once a
   channel is picked. "What is playing" lives in `PlayerViewModel` (`currentChannel`, null = menu), so
   that single value is the whole navigation.
2. Picking a channel asks the tvf90 adapter to **resolve** it into a playable stream URL. This happens on
   every play because the site hands out signed URLs that expire after ~5 hours.
3. The tvf90 adapter downloads the site's internal player page (pretending to be the site's own
   embedding page via the `Referer` header, which the site requires) and pulls the HLS (`.m3u8`) URL out
   of it. None of the site's JavaScript — ads, popups, geo checks — is ever executed.
4. The player screen plays that URL with Media3/ExoPlayer, full screen, starting immediately. There are
   no playback controls: channels behave like live TV. **Back** stops playback and returns to the menu.

## Home menu
- Header (app name, today's date, clock) over a row of tabs; currently one tab, **Channels**, with the
  lineup numbered like TV channels.
- D-pad: focusing a tab selects it; **Down** enters its list, **Up** from the top of the list goes back to
  the tabs. **Back** inside a list jumps to the tabs; **Back** on the tabs leaves the app.
- On launch, focus is on the first channel. Coming back from the player restores the tab, the scroll
  position and focus on the item that was played (saved state kept via `rememberSaveableStateHolder`
  while the player is showing).

## Error handling
- If the URL can't be obtained (site down, page changed), the screen shows an error message and a
  focused **Retry** button (one OK press on the remote). Back returns to the menu to pick something else.
- If playback fails mid-stream (typically an expired token), the app silently fetches a fresh URL once
  and keeps playing; only if that also fails does it show the error screen.
- Falling behind the live window just jumps back to live. Coming back to the app after leaving it
  resumes at the live edge.

## Architecture
- Single Gradle module `:app`, MVVM, Jetpack Compose for TV.
- `source/` — `StreamSource` interface (the only thing the UI knows about) + one adapter per site
  (`source/tvf90`). When a site changes, only its adapter breaks. Adapter details live in
  `docs/adapters/`.
- `ChannelLineup.kt` — the hardcoded list of channels (all tvf90 ids, in menu order).
- `menu/` — `MenuScreen` (header + tabs) and one composable per tab (`ChannelsTab`). Lists use
  foundation's `LazyColumn`: `TvLazyColumn` is deprecated and D-pad bring-into-view is built in.
- `player/` — `PlayerViewModel` (state: current channel or none, playback idle / loading / ready /
  error; `play` / `stop`), `PlayerScreen` (stateless UI), `VideoPlayer` (wraps ExoPlayer + Media3
  `PlayerView`).
- Networking: OkHttp. Tests: JVM unit tests with MockWebServer and a saved copy of the site's page.

## Key decisions
- No hardcoded stream URL: it expires, so it's resolved at runtime on the device itself.
- Regex over the inline script instead of an HTML parser (the URL lives in a JS variable, not in the DOM).
- No dependency injection framework or multi-module setup yet — not justified at this size.
- ExoPlayer's built-in controller is disabled. It grabs D-pad keys for its own buttons, and pause/seek add
  little on live channels.
- No navigation library: two screens switched by `currentChannel` don't justify one.
- The lineup is a fixed list inside the app, not fetched from the site. All channels currently come from
  tvf90; mixing sites will need each channel to know its source.
