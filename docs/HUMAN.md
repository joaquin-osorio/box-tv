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
4. The player screen plays that URL with Media3/ExoPlayer, full screen, starting immediately. Pressing
   any remote button shows the playback controls.

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
- `player/` — `PlayerViewModel` (state: loading / ready / error), `PlayerScreen` (stateless UI),
  `VideoPlayer` (wraps ExoPlayer + Media3 `PlayerView`).
- Networking: OkHttp. Tests: JVM unit tests with MockWebServer and a saved copy of the site's page.

## Key decisions
- No hardcoded stream URL: it expires, so it's resolved at runtime on the device itself.
- Regex over the inline script instead of an HTML parser (the URL lives in a JS variable, not in the DOM).
- No dependency injection framework or multi-module setup yet — not justified at this size.
