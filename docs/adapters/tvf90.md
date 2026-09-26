# tvf90 adapter (`com.boxtv.source.tvf90.Tvf90Source`)

Investigated 2026-09-25. No JSON API for streams; resolution is a single HTML fetch + regex. The agenda does have
one (see "Schedule API" below).

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

## Schedule API (`com.boxtv.source.tvf90.Tvf90Schedule`)
Investigated 2026-09-25. `https://tvf90.com/agenda.html` is an empty shell; its `/js/index.js` loads the
agenda from `AGENDA_URL` in `/js/config.js`:
- `GET https://api.wqxag.com/diaries.json` — Strapi v4 response behind Bunny CDN (15 s cache), no auth.
  The app sends the browser User-Agent and `Referer: https://tvf90.com/` anyway. Images live under
  `IMG_URL = https://img.wqxag.com` (relative `url`s).
- Shape (unknown keys ignored):
  ```json
  {"data":[{"id":40025,"attributes":{
    "diary_description":" Liga MX: \nTijuana vs Atlas", "diary_hour":"22:00:00", "date_diary":"2026-09-25",
    "country":{"data":{"attributes":{"image":{"data":{"attributes":{"url":"/uploads/bandera_….png"}}}}}},
    "embeds":{"data":[{"attributes":{"embed_name":"TUDN USA | OP2",
                                     "embed_iframe":"/embed/eventos.html?r=<base64>"}}]}}}]}
  ```
  - `diary_description` is `"<competition>: <match>"` with stray spaces/newlines; some entries have no
    competition prefix (`"MLB – Red Sox vs. Cubs"`). The adapter splits on the first `:`.
  - `diary_hour` is **America/Lima** wall-clock time (the site converts it with Luxon). Entries only cover
    today; `date_diary` is the Lima date.
  - `embeds` may be empty (the site shows "Señal próximamente") → event with no channels.
- Embed chain: `r` is base64 of a tvf90 player page, e.g. `https://tvf90.com/1.php?stream=tudn`, sometimes with a
  trailing `\n` (`plus1.php?stream=even1\n`). `1.php`, `2.php`, `3.php`, `plus1.php` all iframe the same
  `5.php?stream=<id>` that `Tvf90Source` resolves; `hd.php` iframes `6.php?stream=<id>` (same `playbackURL`
  variable, another CDN). The app only keeps the `stream` id, so all mirrors of a signal ("| OP2", "| OP3",
  "| HD") collapse into one `Channel`, played via `5.php`. Every id seen on 2026-09-25 (espn3, tudn, winsports,
  winsports2, tntsportschile, foxsports2_usa, espn2mx, even1) resolved.
- Embeds whose decoded URL isn't on `tvf90.com` or has no `stream` id are ignored. Entries without a
  description or a parseable time are dropped.
- The site's JS polls every 30–45 s; the app refreshes on its own schedule (see the menu docs in `HUMAN.md`).

### Schedule breakage checklist
- `AGENDA_URL` moved: check `https://tvf90.com/js/config.js` (imported with a `?v=` cache buster from `index.js`).
- Fields renamed: compare with the fixture `app/src/test/resources/tvf90/diaries.json` (a trimmed real response;
  entry 40033's `country` was nulled to cover a missing flag).
- Embed format changed (no more `r`): update `Tvf90Schedule.toChannel`.
