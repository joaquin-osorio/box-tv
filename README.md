# Box TV

An Android TV app that plays web live streams as if they were native TV channels, navigable entirely
with a remote (D-pad).

> **Status: early MVP.** A fixed lineup of six live sports channels (ESPN, DSports, Fox Sports,
> TNT Sports, ESPN Premium, TyC Sports), switchable from a floating channel menu.

## Features

- Full-screen live playback with Media3 (ExoPlayer), starting immediately on launch with the first
  channel.
- Floating channel menu opened with the remote's **Left** button; it closes itself after 10 seconds of
  inactivity.
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
  - `adb` (part of Android SDK Platform-Tools) to install on a device.

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

### 2. Install on an Android TV device

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

- Launching the app starts playing the first channel (ESPN) right away.
- Press **Left** to open the channel list. Move with **Up/Down** and press **OK** to switch channel.
  **Back** or **Right** closes the list; it also closes on its own after 10 seconds without a button
  press.
- If the stream fails, press OK on **Retry**, or press **Left** to pick another channel.
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
- `app/src/main/java/com/boxtv/player/` — player ViewModel, screen, channel menu and ExoPlayer wrapper.
- `docs/` — technical notes (`docs/HUMAN.md` has a high-level overview of the app).

## Disclaimer

This is a personal project. It does not host any content; it only plays streams published by
third-party websites. Make sure you have the right to access any content you watch with it.
