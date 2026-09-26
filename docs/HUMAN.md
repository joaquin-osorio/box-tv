# Box TV — overview

## What it does
Android TV app that plays web live streams as if they were native TV channels. Current state (MVP):
opening the app goes straight into full-screen playback of the first channel of a fixed lineup of six
sports channels from tvf90 (`ChannelLineup`), autoplaying.

## How it works
1. `MainActivity` builds a `PlayerViewModel` with the tvf90 adapter and the channel lineup; it starts on the
   first channel.
2. The ViewModel asks the adapter to **resolve** the channel into a playable stream URL. This happens on
   every launch because the site hands out signed URLs that expire after ~5 hours.
3. The tvf90 adapter downloads the site's internal player page (pretending to be the site's own
   embedding page via the `Referer` header, which the site requires) and pulls the HLS (`.m3u8`) URL out
   of it. None of the site's JavaScript — ads, popups, geo checks — is ever executed.
4. The player screen plays that URL with Media3/ExoPlayer, full screen, starting immediately. There are
   no playback controls: channels behave like live TV.

## Channel menu
- **Left** on the remote opens a floating channel list over the video (which keeps playing), with focus
  on the current channel. **Up/Down + OK** switches channel (a new URL is resolved each time), and
  the menu closes. **Back** or **Right** closes it without switching.
- It closes on its own after **10 s without a key press**. Each key press inside the menu restarts
  the countdown. The timer lives in `PlayerViewModel` (`MENU_TIMEOUT`), not in the UI, so it's unit-tested
  with virtual time.
- Left also works from the loading and error screens, so a broken channel is never a dead end.
- Focus: while the menu is closed, the screen's root (or the Retry button, on error) holds focus and
  catches Left. While the menu is open, only the menu is focusable, so D-pad can't leak to the content
  underneath. When it closes, focus goes back to where it was.

## Error handling
- If the URL can't be obtained (site down, page changed), the screen shows an error message and a
  focused **Retry** button (one OK press on the remote).
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
- `player/` — `PlayerViewModel` (state: channel lineup, current channel, playback loading / ready /
  error, menu open + auto-close timer), `PlayerScreen` and `ChannelMenu` (stateless UI), `VideoPlayer`
  (wraps ExoPlayer + Media3 `PlayerView`).
- Networking: OkHttp. Tests: JVM unit tests with MockWebServer and a saved copy of the site's page.

## Key decisions
- No hardcoded stream URL: it expires, so it's resolved at runtime on the device itself.
- Regex over the inline script instead of an HTML parser (the URL lives in a JS variable, not in the DOM).
- No dependency injection framework or multi-module setup yet — not justified at this size.
- ExoPlayer's built-in controller is disabled. It grabbed every D-pad key and uses Left/Right for its own
  buttons, which clashes with "Left opens the menu". Pause/seek add little on live channels.
- The lineup is a fixed list inside the app, not fetched from the site. All channels currently come from
  tvf90; mixing sites will need each channel to know its source.
