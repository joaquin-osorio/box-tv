# tvf90 adapter (`com.boxtv.source.tvf90.Tvf90Source`)

Investigated 2026-09-25. No JSON API; resolution is a single HTML fetch + regex.

## Page chain
1. `https://tvf90.com/online.php?stream=<id>` — public page. Only contains an iframe
   `src="/5.php?stream=<id>"` plus ad/geo scripts (popunder overlay, `acscdn.com`). Not fetched by the app.
2. `https://tvf90.com/5.php?stream=<id>` — Clappr + hls.js (+ SwarmCloud P2P) player page. Contains:
   ```js
   var playbackURL = "https://4.ftlly.com:443/<id>/mono.m3u8?token=<sig>-<xx>-<exp>-<start>";
   ```
   - The string literal may sit on the next line (`playbackURL =\n    "..."`); the regex's `\s*` covers it.
   - The CDN host (`<n>.ftlly.com`) varies per request/channel.
   - **Requires `Referer: https://tvf90.com/online.php?stream=<id>`.** Without it the page is served
     without `playbackURL` (anti-hotlink). The page also has JS iframe/sandbox checks, irrelevant because
     we never execute its JS.
   - Token is freshly signed per request; `exp - start = 18000s` (5h). Never cache/hardcode the m3u8 URL.
     The player re-resolves on playback errors to recover from expiry.
3. The m3u8 (HLS v3, ~5.6s MPEG-TS segments, live sliding window) plays with no Referer/Origin. The app
   still sends a browser User-Agent.

## Known channel ids
All verified to return a `playbackURL` on 2026-09-25. The app's lineup is `com.boxtv.ChannelLineup`.
- `espn`, `dsports`, `foxsports`, `tntsports`, `espnpremium`, `tycsports`

## Breakage checklist
- Iframe path changed (`5.php` → something else): check `online.php` source.
- Variable renamed: update `PLAYBACK_URL_REGEX`, refresh fixture `app/src/test/resources/tvf90/player_page.html`.
- Fixture is a real page with the token replaced by a fake one.
