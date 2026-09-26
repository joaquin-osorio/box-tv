# streamtp adapter (`com.boxtv.source.streamtp`)

Investigated 2026-09-26. Site: `https://streamtp-golden1.click/` (`StreamtpSource.SITE_URL`). The domain
number rotates (`streamtp-golden<n>`); when the site moves, updating `SITE_URL` is the fix.

## DNS block (read first)
The home ISP's DNS (router 192.168.1.1) answers **NXDOMAIN** for `streamtp-golden1.click`, while public
resolvers answer `93.123.109.235`. So every streamtp request goes through an OkHttp client whose `Dns` is
`dnsWithDohFallback()` (`source/FallbackDns.kt`): system DNS first, Cloudflare DNS-over-HTTPS
(`https://cloudflare-dns.com/dns-query`, bootstrapped by IP 1.1.1.1 / 1.0.0.1) only on
`UnknownHostException`. The client is built once in `MainActivity` (`streamtpClient`).
- The CDN hosts of the streams (`<n>.domhsd.com`) resolve normally, so ExoPlayer (`DefaultHttpDataSource`,
  system DNS) is not changed. If they get blocked too, the player needs an OkHttp data source with the same DNS.
- `DnsOverHttps` checks the public suffix list, which OkHttp 5 on Android loads from its assets via the
  androidx-startup `PlatformInitializer`. In JVM unit tests there is no Context, so a live DoH check there
  needs `resolvePrivateAddresses(true)` to skip that list.

## Stream resolution (`StreamtpSource`)
- `GET https://streamtp-golden1.click/global1.php?stream=<id>` — Clappr + hls.js (SwarmCloud P2P) page with
  ad scripts. No Referer or cookie needed (a `PHPSESSID` is set but not checked). It contains:
  ```js
  var playbackURL = "https:\/\/93.domhsd.com:443\/global\/<id>\/index.m3u8?token=<sig>-<xx>-<exp>-<start>&ip=<client ip>";
  ```
  - A JSON-style string: `\/` must be unescaped (the adapter replaces `\/` with `/`).
  - **The token is bound to the requester's public IP** (`ip=`). Fine for the app, which resolves and plays
    on the same device; don't resolve on one network and play on another.
  - `exp - start = 54000s` (15h). Still re-resolved on every play, like tvf90.
  - The URL 302-redirects to another `<n>.domhsd.com` host that serves a master playlist (720p variant,
    `tracks-v1a1/mono.m3u8`). Plays with no special headers; the app sends the browser User-Agent.
- Any `stream` value returns a `playbackURL`, even nonexistent ids. A bad id only fails at playback (ExoPlayer
  error → one silent re-resolve → error screen).

## Schedule API (`StreamtpSchedule`)
`https://streamtp-golden1.click/eventos.html` is a shell whose inline script fetches
`events.json?_=<timestamp>` (cache buster, not needed) every 60 s:
- `GET https://streamtp-golden1.click/events.json` — plain JSON array, no auth, nginx static file
  (`last-modified` moves every minute). One entry **per signal**:
  ```json
  [{"title":"Inglaterra vs España","category":"Liga de Naciones de la UEFA","time":"13:45",
    "url":"https://streamtp-golden1.click/global1.php?stream=espn","quality":"720p","status":"next"}]
  ```
  - An event = entries sharing `time` + `category` + `title`; the adapter groups them (58 entries → 42 events
    on 2026-09-26). Event id: `streamtp:<HH:mm>|<category>|<lowercased title>`, stable across refreshes as
    long as the site doesn't edit the entry.
  - Language variants are in the title: `"Raúl Rosas Jr. vs Raoni Barcelos | Español"` / `"| English"` /
    `"| Portugues"`. The part after ` | ` becomes the signal name suffix (`PARAMOUNT 1 · Español`).
    `category` can itself contain ` | ` (`"F1 | GP de Azerbaiyán"`); only the title is split.
  - `time` is `HH:mm` in **America/Panama** (UTC−5, no DST): the page renders its clock with that zone, and
    the F1 Baku race (11:00 UTC) is listed as `06:00`. No date: the list covers the site's current day, so
    the adapter uses today's date in Panama. Past-midnight events (none seen) would land on the wrong day.
  - Only the `stream` query parameter of `url` is used (host not checked, the domain rotates). Signal names
    are the stream id prettified (`disney1` → `DISNEY 1`, `tudn_usa` → `TUDN USA`): the site gives no names.
  - `status` (`next` / `live` / `finished`) and `quality` are ignored; the app computes Live/Finished itself.
  - No flags / images at all → `flagUrl = null`.
- Entries without a title, a parseable time or a `stream` id are dropped.

## Breakage checklist
- NXDOMAIN / connection errors on the device: check whether DoH is also blocked (swap the resolver URL),
  or whether the domain rotated (`SITE_URL`).
- Variable renamed or format changed: update `PLAYBACK_URL_REGEX`; refresh the fixture
  `app/src/test/resources/streamtp/player_page.html` (real page, host/token/IP replaced with fake values).
- Agenda moved or renamed: check the inline script of `eventos.html`. Fields changed: compare with the
  fixture `app/src/test/resources/streamtp/events.json` (trimmed real response; the `TBD` time entry and the
  entry whose URL has no `stream` are synthetic, to cover dropped entries).
