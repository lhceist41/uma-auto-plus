# UMA Auto+

[![Latest release](https://img.shields.io/github/v/release/lhceist41/uma-auto-plus?label=latest%20release&color=blue)](https://github.com/lhceist41/uma-auto-plus/releases/latest)
[![Changelog](https://img.shields.io/badge/changelog-CHANGELOG.md-informational)](CHANGELOG.md)

> [!IMPORTANT]
> **This is a personal fork of [steve1316/uma-android-automation](https://github.com/steve1316/uma-android-automation).**
> All original credit goes to **steve1316** and the contributors of the upstream project. The core bot engine -- the screen reading, the training/racing/event logic, and the app itself -- is entirely their work.
>
> I did not create the original bot. The core engine is steve1316's; this fork adds features and decision-logic on top of it (see the honest breakdown below). If you're looking for the original project, please visit the upstream repository linked above.

---

## What this fork adds

steve1316 built the bot engine. This fork is a substantial body of work on top of it -- roughly 200 commits past the v5.4.8 fork point -- turning that engine into a hands-off, meta-aligned distribution. Here is an honest split of what I added versus what is steve1316's.

### What I added

**Hands-off, multi-run grinding**

- **Multi-run queue** -- queue 2-20 consecutive careers so the bot grinds unattended, with configurable run count, inter-run delay, and stop-on-error.
- **Trainee rotation** -- queue several different trainees and the bot cycles through them, each run under her own preset, switching automatically between careers (off by default).
- **Between-run navigation** -- the bot walks back through the post-career menus and launches the next run by itself, and stops with a clear message instead of looping when it hits a screen it can't handle.
- **Overnight resilience** -- the bot keeps the screen awake, restarts itself if it freezes, resumes the queue after a crash, gives up on a stuck run after a time limit you set, restores TP from whatever is available (Toughness, then Star Fruit, then a Carats refill), and repairs its own Accessibility service when an emulator like MuMu silently kills it mid-run. Each of these came from a real overnight queue that died early.
- **Career-end spark reroll** -- opt-in, off by default: spends 30 TP once to redraw a career's sparks when the odds are good and no target 3-star spark is already present, keeping the better of the two. Supervise the first use.

**Pick-and-go presets**

- **87 hand-tuned character presets** -- 29 trainees across 3 scenarios (URA Finale / Unity Cup / Trackblazer), each pre-filling stat priorities, race plan, skill plan, event picks, and training thresholds. Pick a scenario, pick a character, hit Start -- no manual tuning required.
- **Scenario advisories** -- the Home page shows a green "good pick" or yellow "mismatch" banner (with the reason) for the selected trainee and scenario.

**Decision-engine extensions** (built on top of steve1316's scoring)

- **Optimize Knapsack skill buying** -- works out the best combination of skills your end-of-career points can buy, including how upgrade chains exclude each other, instead of just grabbing the best-looking skill first.
- **Deck validation at career start** -- warns immediately if the trainee's aptitudes are too low or her race predictions won't be visible to the bot, instead of letting a doomed run waste half an hour.
- **Single-star prediction detection** -- the bot can see single-star races on the race list (the original only sees double-star ones), and forces a fan-earning race when a Junior fan goal is about to fail.
- **July 2026 rebalance adaptation** -- per-scenario stat caps (URA 1400 / Unity Cup 1300 with Wit 1800 / Trackblazer 1200 with Stamina 1900 and Wit 1500) and a scenario-aware stat-reading ceiling, so a legitimate high stat past the old flat 1200 limit is no longer discarded as a misread.
- **Race and training refinements** -- mandatory races retry toward 1st place when a free retry is available, and training anticipates rainbows from bars close to maximum friendship.
- **Trackblazer tuning** -- Akikawa-bonding training priority (a proxy for MotY points), climax energy-item reservation, an Alarm Clock retry policy, and an opt-in irregular-training gate. Trackblazer is the fork's most-tuned scenario.
- **Mood-floor guard** -- an optional stricter mood floor for trainees with single-option mood-trap events.
- **Results history** -- every career saves a record of how it ended (completed / force-ended / cut short) together with the settings it ran under, and a bundled tool turns a batch of them into a per-trainee results table, so preset tuning can be measured across many runs instead of judged one at a time. Extra diagnostics for bug reports sit behind the Debug Mode setting.

**New modes (experimental)** -- Daily Races and Team Trials task modes -- fully implemented and selectable, but not yet verified end-to-end on a live run (upstream is career-only).

**UI and quality of life** -- a settings search across every page, a named-profile manager, queue progress with a skip-run button, and an icon overhaul that fixed a first-launch crash.

**Distribution** -- rebranded to "UMA Auto+" with a distinct dark-navy "U+" icon (white "U", orange "+") and its own `applicationId` so it installs alongside the original; a GitHub Actions pipeline that builds and publishes signed APKs on every release tag; and an in-app update checker.

### What's steve1316's

The core engine -- the part that makes any of this possible -- is steve1316's, built on his [`android-cv-automation-library`](https://github.com/steve1316/android-cv-automation-library):

- The `Game`/`Campaign` orchestration and the URA Finale / Unity Cup / Trackblazer scenario framework.
- The screen-recognition system and its full image library.
- The text reading (Google ML Kit + Tesseract) and the optional YOLOv8 stat-gain detector.
- The base training, racing, and training-event scoring logic.
- The training-event recognizer that matches on-screen event names against the game database.
- The app itself -- its architecture and the whole Settings UI.

I also continuously merge steve1316's ongoing improvements (energy-item conservation, per-distance race strategies, megaphone stat thresholds, game-data updates, and more) -- those are his work that I cherry-pick in.

My work extends that engine's *decisions* and wraps it in a hands-off distribution; it does not replace it. For the full version-by-version list of changes, see the [CHANGELOG](CHANGELOG.md).

---

## Upstream project

| | |
|---|---|
| **Original repo** | [steve1316/uma-android-automation](https://github.com/steve1316/uma-android-automation) |
| **Original author** | [steve1316](https://github.com/steve1316) |
| **Discord** | https://discord.gg/5Yv4kqjAbm |
| **Fork base version** | v5.4.8 |

> [!TIP]
> For a detailed explanation of how the bot works -- including the decision engine, training scoring, racing system, item management, and scenario-specific logic -- see [HOW_IT_WORKS.md](HOW_IT_WORKS.md). That document is from the original project.

---

# Disclaimer

This project is purely for educational purposes to learn about Android automation and computer vision - basically a fun way to practice coding skills. Any usage is at your own risk. No one will be responsible for anything that happens to you or your own account except for yourself.

# Requirements

- Android Device or Emulator (Nougat 7.0+)
    - Hard requirement for Android phones is 1080p and 240 DPI or 450 DPI (for Samsung phones). If your device do not meet these, you can try the `Basic Template Matching Test` in the Settings under the `Debug Tests` section to determine the best scale to use in the `Custom Scale for Template Matching` setting. If not, then you can also try the [To set the phone's resolution to 1080p (faster and more accurate)](#to-set-the-phones-resolution-to-1080p-faster-and-more-accurate) section to forcibly set the display resolution and DPI of your Android phone. Note that may come with the side-effect of your device UI being scrunched in or zoomed out.
    - Tested emulators are Bluestacks 5 (Pie 64-bit, but other versions should work) and MuMu. The following setup is required:
        - Portrait Mode needs to be forced on always.
        - Bluestacks itself needs to be updated to the latest version to avoid Uma Musume crashing.
        - In the Bluestacks Settings > Phone, the predefined profile needs to be set to a modern high-end phone like the Samsung Galaxy S22.
        - Setup for both BlueStacks and MuMu:
            - 4 CPU Cores
            - 4GB Memory
            - 1080 x 1920 (width x height)
            - 240 DPI (This is important)

> [!WARNING]
> If you change the display resolution while the overlay button is still active, you will need to restart the app for the change to take effect.

> [!IMPORTANT]
> The in-game graphics need to be set to `Standard` instead of `Basic`.

# Features

- [x] Completes a full run from start/midway to its conclusion.
- [x] Supports multiple scenarios including **URA Finale**, **Unity Cup**, **Trackblazer**, and those in the future to come.
- [x] Recognizes the game's screens and buttons in real time.
- [x] Reads training stat gains with an on-device vision model for improved accuracy.
- [x] Reads on-screen text and matches it against the game's event database.
- [x] Modern user interface built using React Native, Typescript and Expo for full configurability.
- [x] Remote Log Viewer to watch the bot's progress live from a browser on your PC.
- [x] Screen recording for debugging to easily capture and review issues.
- [x] Import/export settings as JSON, alongside customizable skill point buying plans and training configurations.
- [x] Smart racing plan that dynamically schedules extra races based on current stats and fan requirements.
- [x] Training Event customization per event for fine-grained control over choices.
- [x] Load and manage profiles for the Training Settings to easily swap between different builds.
- [x] A multitude of settings to configure including setting preferred stat targets per distance.

# Instructions

1. Download the latest `.apk` file from the `Releases` section on the right of this page and install it on your Android device.
2. Open the application. Upon launching, navigate through the user-friendly frontend to select your desired scenario (URA Finale, Unity Cup, etc.) and configure your training priorities, races, and other settings.
3. You can review your loaded settings and configurations directly on the Home page.
4. Tap the `Start` button. If this is the first time, you will be prompted to grant `Overlay` permissions and enable the `Accessibility` service.

> [!NOTE]
> On newer Android versions, you're required to enable `Allow restricted settings` in the app's `App Info` settings.

> [!TIP]
> **Emulator users (especially MuMu): grant this permission once** so the bot can recover when the emulator silently kills the Accessibility service mid-run. Without it, unattended runs stop the first time that happens:
> ```
> adb shell pm grant com.lhceist41.uma_auto_plus android.permission.WRITE_SECURE_SETTINGS
> ```
> Run it from a PC with `adb`, or on-device with aShell You + Shizuku (the same tools as the resolution steps below).

5. Once enabled, tapping `Start` will ask for screen-capture access (select `Entire screen` if prompted). A floating overlay button will appear that you can drag around the screen.
6. Follow the guidance overlay when you drag the overlay button for the places on the screen to safely leave the button at to avoid covering important UI elements.

> [!CAUTION]
> Placing the overlay button over important UI elements will interfere with the bot's ability to read the screen.

7. Navigate to the main training menu in Uma Musume (where Rest, Train, Buy Skills, Races, etc. are visible).

> <img width="270" height="585" alt="main screen" src="https://github.com/user-attachments/assets/05239856-878e-4e49-a325-db60013d7c75" />

8. Tap the overlay button to start automation.

> [!TIP]
> Use minimal or deactivated notifications so nothing covers the top of the screen while the bot reads it.

> [!TIP]
> Hitting stops, stuck screens, or "trainee not found"? See **[TROUBLESHOOTING.md](TROUBLESHOOTING.md)**.

## To view Logs in Real-time

1. Install `Android Studio` and create any new project or open an existing one in order for the `Logcat` console to appear at the bottom.
2. Connect your Android device to your computer:
   - **USB Connection:** Enable `Developer Options` and `USB Debugging` on your device, then connect via USB cable.
   - **Wireless Connection:** In Developer Options, enable `Wireless debugging` and pair your device using the pairing code or QR code.
   - **Bluestacks or other emulators:** In the emulator settings, there is usually an option to allow ADB wireless connection on `127.0.0.1:5555`. Enabling that option should be enough, but if Android Studio still does not see it, you can open up a terminal like `cmd` and type `adb connect 127.0.0.1:5555` and it should say `connected to 127.0.0.1:5555`.

> [!TIP]
> You may need to type `adb disconnect` to disconnect all ADB connections beforehand for a fresh slate.

3. In Android Studio's Logcat console at the bottom of the window, select your connected device from the device dropdown menu.
4. Filter the logs by typing `package:com.lhceist41.uma_auto_plus [UAA]` or just `[UAA]` in the search box to see only the logs from this app.
5. Run the app - you'll now see all of its logs appear in real-time as it runs.

## To set the phone's resolution to 1080p (faster and more accurate)

> [!NOTE]
> This only works when downscaling. If your device's official resolution is lower than 1080p it will most likely not work.

1. Install the [**aShell You**](https://github.com/DP-Hridayan/aShellYou) app. This allows you to run adb commands locally on your Android device, but requires [**Shizuku**](https://github.com/RikkaApps/Shizuku).
2. Install [**Shizuku**](https://github.com/RikkaApps/Shizuku), then start it by following [these instructions](https://shizuku.rikka.app/guide/setup/#start-via-wireless-debugging).
3. With **Shizuku** started, you can then use **aShell You** to send the following adb commands:
   - **Change resolution to 1080p:** `wm size 1080x1920 && wm density 240`
   - **Revert to original:** `wm size reset && wm density reset`

    You can also bookmark the commands for your own convenience.

Alternatively, you can do the same on a computer if you cannot get the above to work out.
1. Install [**adb**](https://developer.android.com/tools/releases/platform-tools). You will also to add the file path to the folder to `PATH` via the `Environment Variable` setting under `View advanced system settings` so that the terminal will know what the `adb` command should do. You may need to restart your computer to have your terminal pick up the changes.
2. Open up a new terminal anywhere (cmd, Powershell, etc).
3. Plug in your Android device via USB. If all goes well, then executing `adb devices` will show your connected device when `Settings > Developer options > USB Debugging` is enabled. There may be a popup on your Android device beforehand asking you to give permission to connect to ADB. Wirelessly connecting to ADB is also available via the Android `Settings > Developer options > Wireless debugging`
4. Execute the following commands individually to forcibly set your display resolution to 1080p and DPI to 240:
    - **Change resolution to 1080p:** `adb shell wm size 1080x1920` and `adb shell wm density 240`
    - **Revert changes:** `adb shell wm size reset` and `adb shell wm density reset`

> [!WARNING]
> If your home button disappears, reset the DPI back to default.

> [!TIP]
> Use 1.0 scaling and an 80% confidence threshold for best results in 1080p natively.

# For Developers

This project is separated into a React Native frontend configured via Expo and an extensive Kotlin/OpenCV backend.

1. Download and extract the repository.
2. Download OpenCV for Android (v4.12.0) from `https://opencv.org/releases/`. Create `/android/opencv` and copy the extracted `/OpenCV-android-sdk/sdk/` contents into it.
3. The project uses a YOLOv8 model for stat gain detection. Ensure the `best.onnx` model file is present in the `android/app/src/main/assets/` directory.

> [!IMPORTANT]
> Without the ONNX model file, the YOLO stat detection feature will not work. Template matching will still function as a fallback.

4. The project utilizes Expo. Run `yarn install` from the root directory to install frontend dependencies.
5. The dev environment is ready. Run `yarn start` or `npx expo start` to run the Metro HTTP server.
6. To ensure code consistency, developers should format and lint the codebase using the following commands:
    - `yarn format`: Formats both TypeScript/TSX files (via **Prettier**) and Kotlin files (via **Ktlint**).
    - `yarn format:tsx`: Formats only TypeScript and TSX files using **Prettier**.
    - `yarn format:kt`: Formats only Kotlin files using **Ktlint** (following settings in [android/.editorconfig](./android/.editorconfig)).
7. To test Android builds, execute `yarn android` to compile and install the application directly on your device. Use `yarn build` for release APK generation.

> [!NOTE]
> Do not run the React Native shell app directly from Android Studio. Always rely on the Expo Metro bundler for correct bridging.

# Technologies Used

1. [eng.traineddata from tessdata](https://github.com/tesseract-ocr/tessdata)
2. [MediaProjection - Used to obtain full screenshots](https://developer.android.com/reference/android/media/projection/MediaProjection)
3. [AccessibilityService - Used to dispatch gestures like tapping and scrolling](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
4. [OpenCV Android - Used to template match](https://opencv.org/releases/)
5. [Tesseract4Android - For performing OCR on the screen](https://github.com/adaptech-cz/Tesseract4Android)
6. [string-similarity - For comparing string similarities during text detection](https://github.com/rrice/java-string-similarity)
7. [React Native - Used as the frontend](https://reactnative.dev/)
8. [Expo - Modern modular frontend](https://expo.dev/)
9. [SQLite - Local database via expo-sqlite](https://docs.expo.dev/versions/latest/sdk/sqlite/)
10. [Ktor - For the Remote Log Viewer](https://ktor.io/)
11. [YOLOv8 - Object detection](https://github.com/ultralytics/ultralytics)
12. [ONNX Runtime - Lightweight engine for executing the YOLOv8 model](https://onnxruntime.ai/)
