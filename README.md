# Box TV

An Android TV app that plays web live streams as if they were native TV channels, navigable entirely
with a remote (D-pad).

> **Status: early MVP.** A home menu with a fixed lineup of six live sports channels (ESPN, DSports,
> Fox Sports, TNT Sports, ESPN Premium, TyC Sports) and today's sports agenda with links to the channels
> broadcasting each event.

## Features

- Home menu with tabs, built for the remote: pick a channel and it plays; **Back** returns to the menu
  right where you left it.
- **Events** tab: today's matches grouped into earlier / live now / coming up, auto-refreshed every
  minute. Pick an event to watch it; events broadcast on several channels let you choose one.
- Full-screen live playback with Media3 (ExoPlayer), starting as soon as a channel is picked.
- Stream URLs are resolved at runtime on the device (the sites hand out short-lived signed URLs), so
  nothing expires inside the APK.
- Clear error screen with a focused **Retry** button when a stream can't be obtained; automatic
  one-time recovery when a stream token expires mid-playback.
- Each streaming site lives behind a common `StreamSource` adapter, so a site changing its page only
  breaks its own adapter.

## Requirements

- An Android TV / Google TV device (or emulator) running **Android 6.0 (API 23)** or newer.
- To build from source:
  - **JDK 17** or newer.
  - **Android SDK** with platform **API 37** installed (Android Studio installs it for you).
  - Optionally `adb` (part of Android SDK Platform-Tools) to install over the network instead of with a
    USB drive.

The Gradle wrapper is included, so you don't need Gradle installed.

## Installation

### 1. Clone and build

```sh
git clone https://github.com/joaquin-osorio/box-tv.git
cd box-tv
./gradlew assembleDebug
```

If you're not using Android Studio, tell Gradle where the Android SDK is by creating a
`local.properties` file in the project root (it's git-ignored):

```properties
sdk.dir=/path/to/Android/sdk
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### 2. Install on an Android TV device (USB drive)

No developer mode or cables to the computer needed:

1. Copy `app/build/outputs/apk/debug/app-debug.apk` to a USB drive (FAT32 or exFAT).
2. On the TV, install a file manager from the Play Store if there isn't one (e.g. **File Commander**,
   **X-plore** or **FX File Explorer**).
3. Allow that file manager to install apps. Usually the TV asks the first time you open an APK and takes
   you to the right toggle; otherwise enable it under **Settings → Apps → Security & restrictions →
   Unknown sources** (the exact path varies by brand).
4. Plug the USB drive into the TV, open it in the file manager and select `app-debug.apk` → **Install**.
   To update, repeat with the new APK; the app's data is kept.

The same works with any other way of getting the APK onto the TV (e.g. **Send Files to TV** over Wi-Fi).

### Alternative: install over the network with adb

Handy for development, since it builds and installs in one step.

1. On the TV, enable developer mode: **Settings → System → About**, then press OK on **Android TV OS
   build** (or **Build**) seven times.
2. Enable **Settings → System → Developer options → USB debugging** (on some devices also
   **Network debugging** / **Wireless debugging**).
3. Find the TV's IP address under **Settings → Network & Internet**.
4. From your computer, on the same network:

   ```sh
   adb connect <TV_IP>:5555
   ```

   Accept the debugging prompt that appears on the TV. On Android 11+ devices using **Wireless
   debugging**, pair first with `adb pair <TV_IP>:<pairing_port>` and then connect to the port shown on
   that screen.
5. Build and install in one step:

   ```sh
   ./gradlew installDebug
   ```

   Or install a previously built APK with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

The app then shows up in the TV's launcher as **Box TV**.

### Installing on an emulator

In Android Studio, create a **TV** device in the Device Manager (e.g. "Television (1080p)"), start it,
and run `./gradlew installDebug` — or just press **Run** in Android Studio.

## Usage

- Launching the app opens the home menu on the **Channels** tab. Move with **Up/Down** and press **OK**
  to play a channel.
- Press **Up** from the top of a list (or **Back**) to reach the tabs; **Left/Right** switches tab and
  **Down** goes back into the list. **Back** on the tabs exits the app.
- On the **Events** tab, **OK** on an event plays it; if it has several signals, **OK** lists them below
  it (pick one with **Down** + **OK**), and **OK** again or **Back** hides them.
- While playing, **Back** returns to the menu.
- If the stream fails, press OK on **Retry**, or **Back** to pick something else.
- There are no playback controls (pause/seek): channels behave like live TV.

## Development

```sh
./gradlew ktlintCheck      # lint
./gradlew testDebugUnitTest  # JVM unit tests
```

Adapter tests run against saved fixture pages and MockWebServer, never against the real sites.

Project layout:

- `app/src/main/java/com/boxtv/source/` — `StreamSource` interface and one adapter per site.
- `app/src/main/java/com/boxtv/ChannelLineup.kt` — the channel list shown in the menu.
- `app/src/main/java/com/boxtv/menu/` — home menu screen and its tabs.
- `app/src/main/java/com/boxtv/player/` — player ViewModel, screen and ExoPlayer wrapper.
- `docs/` — technical notes (`docs/HUMAN.md` has a high-level overview of the app).

## Disclaimer

This is a personal project. It does not host any content; it only plays streams published by
third-party websites. Make sure you have the right to access any content you watch with it.
