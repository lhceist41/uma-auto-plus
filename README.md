# UMA Auto+

[![Latest release](https://img.shields.io/github/v/release/lhceist41/uma-auto-plus?label=latest%20release&color=blue)](https://github.com/lhceist41/uma-auto-plus/releases/latest)
[![Changelog](https://img.shields.io/badge/changelog-CHANGELOG.md-informational)](CHANGELOG.md)

A hands-off distribution of [steve1316/uma-android-automation](https://github.com/steve1316/uma-android-automation) for Umamusume: Pretty Derby. Pick a trainee, press Start, walk away: the bot plays whole careers, and can queue several in a row without you.

> [!IMPORTANT]
> **This is a personal fork.** All original credit goes to **steve1316** and the contributors of the upstream project. The core bot engine -- the screen reading, the training/racing/event logic, and the app itself -- is their work. This fork adds features and decision logic on top of it. If you are looking for the original project, please visit the [upstream repository](https://github.com/steve1316/uma-android-automation).

## What this fork adds

- **Unattended run queues.** Queue 2 to 20 careers. The bot walks back through the post-career menus, launches the next run itself, and stops with a clear message instead of looping when it meets a screen it cannot handle.
- **Trainee rotation.** Queue several trainees and the bot cycles through them, each run under her own preset, paging the Scenario Select carousel to that trainee's scenario between runs. Off by default.
- **Built-in presets.** 292 presets: 72 trainee cards, each with a build for all four career scenarios, plus four parent-farming variants. A searchable picker shows per-scenario fit advice, a Validated or Research badge, and starred favorites. Applying a preset sets its scenario with it.
- **Grand Concert.** The newest career scenario is automated end to end, including its lesson shop and all five concerts. It is still marked experimental in the scenario picker.
- **Overnight resilience.** The bot keeps the screen awake, restarts the game if it wedges, resumes an interrupted queue, gives up on a stuck run after a time limit you set, stops on an in-game date you schedule, rides out brief connection outages and the daily reset, and repairs its own Accessibility service when an emulator such as MuMu silently kills it mid-run.
- **Safer career launches.** Start waits until your selected preset is confirmed saved before it launches, so the trainee shown on Home is the trainee that runs. The bot also warns at career start when a trainee's aptitudes or race predictions make a run unlikely to finish, and asks for confirmation before starting a known scenario mismatch.
- **Decision-engine extensions**, built on steve1316's scoring: a knapsack skill-buying strategy that accounts for upgrade chains, a choice of when mid-career skill buying happens (including an opt-in adaptive mode), single-star race prediction reading, Unity Cup opponent selection from the prediction circles, per-scenario stat caps for the July 2026 rebalance, and Trackblazer-specific tuning.
- **Smart Borrow.** On queued launches the borrowed friend slot is filled from a curated list of strong cards instead of whatever sits in the top row, skipping picks the game would refuse.
- **Career results history.** Each finished career records how it ended, the settings it ran under, an estimated overall rank, and the sparks it produced, so preset tuning can be measured across many runs.
- **Quality of life.** A settings search across every page, named profiles for training settings, queue progress with a skip-run button, signed per-architecture release builds, and an in-app update checker.

For the version-by-version list, see the [CHANGELOG](CHANGELOG.md). For how any of it works internally, see [HOW_IT_WORKS.md](HOW_IT_WORKS.md).

### What is steve1316's

The core engine, without which none of the above exists, is steve1316's, built on his [`android-cv-automation-library`](https://github.com/steve1316/android-cv-automation-library):

- The `Game`/`Campaign` orchestration and the URA Finale, Unity Cup and Trackblazer scenario framework.
- The screen-recognition system and its full image library.
- The text reading (Google ML Kit and Tesseract) and the optional YOLOv8 stat-gain detector.
- The base training, racing and training-event scoring logic.
- The training-event recognizer that matches on-screen event names against the game database.
- The app itself, its architecture and the whole Settings UI.

Upstream improvements are merged in as they land. This fork extends that engine's decisions and wraps it in a hands-off distribution; it does not replace it.

| | |
|---|---|
| **Original repo** | [steve1316/uma-android-automation](https://github.com/steve1316/uma-android-automation) |
| **Original author** | [steve1316](https://github.com/steve1316) |
| **Discord** | https://discord.gg/5Yv4kqjAbm |
| **Fork base version** | v5.4.8 |

# Disclaimer

This project is purely for educational purposes to learn about Android automation and computer vision - basically a fun way to practice coding skills. Any usage is at your own risk. No one will be responsible for anything that happens to you or your own account except for yourself.

# Requirements

- An Android device or emulator running Android 7.0 or newer.
- A supported display configuration. Template matching is calibrated for **1080x1920 at 240 DPI**, or **1080x2340 at 450 DPI** for Samsung phones. On anything else the Home page warns you, detection misfires, and the bot stalls. If your phone cannot be set to one of those, try the `Basic Template Matching Test` under `Settings` > `Debug Tests` to find a working custom scale, or force the display down with the [resolution steps](#to-set-the-phones-resolution-to-1080p-faster-and-more-accurate) below.
- Tested emulators are Bluestacks 5 (Pie 64-bit, other versions should work) and MuMu, set up as follows:
    - Portrait mode forced on always.
    - 4 CPU cores, 4 GB memory, 1080 x 1920 (width x height), 240 DPI. The DPI matters.
    - Bluestacks only: update to the latest version to avoid Uma Musume crashing, and set the predefined profile under `Settings` > `Phone` to a modern high-end phone such as the Samsung Galaxy S22.

> [!IMPORTANT]
> The in-game graphics need to be set to `Standard` instead of `Basic`.

> [!WARNING]
> If you change the display resolution while the overlay button is still active, you will need to restart the app for the change to take effect.

# Instructions

1. Download the `.apk` for your device's CPU from the [latest release](https://github.com/lhceist41/uma-auto-plus/releases/latest) and install it. Each release ships three builds: `arm64-v8a` for most phones and ARM emulators, `armeabi-v7a` for older 32-bit devices, and `x86_64` for the Windows emulators that run an x86 image. If you are not sure, try `arm64-v8a` first; installing the wrong one fails harmlessly.
2. Open the app and set up a run: pick a trainee preset from the Home page (this also sets her scenario), or choose a scenario yourself and configure training, racing and skill settings by hand.
3. Review your loaded settings on the Home page.
4. Tap `Start`. The first time, you will be prompted to grant `Overlay` permission and enable the `Accessibility` service.

> [!NOTE]
> On newer Android versions, you are required to enable `Allow restricted settings` in the app's `App Info` settings.

> [!TIP]
> **Emulator users, especially MuMu: grant this permission once** so the bot can recover when the emulator silently kills the Accessibility service mid-run. Without it, unattended runs stop the first time that happens:
> ```
> adb shell pm grant com.lhceist41.uma_auto_plus android.permission.WRITE_SECURE_SETTINGS
> ```
> Run it from a PC with `adb`, or on-device with aShell You and Shizuku (the same tools as the resolution steps below).

5. Tapping `Start` then asks for screen-capture access (select `Entire screen` if prompted). A floating overlay button appears that you can drag around the screen.
6. Follow the guidance overlay while dragging, so the button ends up somewhere it will not cover important UI.

> [!CAUTION]
> Placing the overlay button over important UI elements will interfere with the bot's ability to read the screen.

7. Put the game where you want the bot to pick up:
    - **On the game's Home screen**, with the run queue enabled (the default), the bot launches the career itself: trainee, deck, borrowed card and Start Career.
    - **On the main training menu** of a career already in progress (where Rest, Train, Buy Skills and Races are visible), the bot plays from there and never touches a setup screen, exactly like the upstream project.

> <img width="270" height="585" alt="main screen" src="https://github.com/user-attachments/assets/05239856-878e-4e49-a325-db60013d7c75" />

8. Tap the overlay button to start automation.

> [!TIP]
> Use minimal or deactivated notifications so nothing covers the top of the screen while the bot reads it.

> [!TIP]
> Hitting stops, stuck screens, or "trainee not found"? See **[TROUBLESHOOTING.md](TROUBLESHOOTING.md)**.

# Presets

A preset is a complete configuration for one trainee in one scenario: stat priorities and targets, racing preferences, skill plans, training-event picks, and the tuning that scenario needs. Tap the trainee card on the Home page to open the picker, expand a trainee, and apply the scenario card you want.

Applying a preset replaces the settings it ships and leaves the rest of your configuration alone. [PRESETS_GUIDE.md](PRESETS_GUIDE.md) covers what a preset changes, how to read the picker's badges and advisories, and what to check when a run does not go the way you expected. Building or editing one is a separate job: see [docs/PRESET_AUTHORING.md](docs/PRESET_AUTHORING.md).

# What the bot changes on your account

- **Does it change my support deck?** It can, when it launches a career for you and **Auto-Fill Support Deck** is enabled (it is, by default). The bot presses the game's own Auto-Fill button, and the game rebuilds the deck with its own logic, which may **replace cards you placed yourself**, not just fill empty slots. On a queue this happens for every run. Turn Auto-Fill Support Deck off under Run Queue Settings to keep your hand-built deck exactly as you left it.
- **Can I set everything up myself?** Yes. Build your deck, pick your parents, start the career, and press Start once you are on the training screen. The bot plays from there and never sees a setup screen.
- **What about the borrowed friend card?** The game forces that one: the borrow resets every career and Start Career silently does nothing while the slot is empty, so an unattended launch has to pick something. Smart Borrow (on by default) scrolls the borrow list and takes the best card it reaches from a curated list, skipping any card that would duplicate a character already in your deck and any card of the trainee you are about to run, since the game refuses both. It falls back to your strong friend card or the first valid row, verifies the formation before pressing Start Career, and stops with a clear message when no valid borrow exists. Follow trainers with strong cards to give it good options, or turn Smart Borrow off for the plain top-row pick.
- **Is there a racing plan?** The same Race Planner the upstream project has, plus the smart-racing scheduler that fills fan gaps as the career runs. Most presets rely on that scheduler. Some ship a hand-built race schedule instead, for trainees whose viable race pool is too thin for dynamic scheduling: dirt specialists, near-empty Junior years, and hard fan checkpoints. The Legacy Farm variants race a G1-dense schedule on purpose.

# Optional features

These are off unless you turn them on:

- **Career-end spark reroll.** Spends 30 TP once to redraw a weak spark set, then reads both sets on the game's selection screen and keeps the better one. Anything it cannot verify stops safely for you to finish by hand. It spends TP, so supervise it the first few times.
- **TP restore between runs.** When the game asks to restore TP mid-queue, refill from Toughness, then Star Fruit, then a Carats refill, instead of ending the queue.
- **Event Boost.** Ticks "Event Boost (TP Usage x2)" before each career. Only worth it while a TP event is live.
- **Trainee rotation** in the run queue, described above.
- **Build-Aware Launch (advanced).** Verifies the live borrow list, deck and launch screen against what it intended to run before it spends TP, and refuses to start rather than guess. There is no fallback: if it cannot confirm the state, no career starts. It can pair with an optional [Windows companion](tools/host-companion/README.md) that adds one bounded list swipe as a last-resort recovery step and can never select a card or start a career.
- **Support-card dating schedule.** With a Group support card in the deck, takes recreation outings on the turns that advance the card's outing chain.
- **Remote Log Viewer** for watching progress from a browser on your PC, **screen recording** for capturing a problem, and the **YOLOv8 stat-gain detector** for reading training gains with an on-device vision model instead of template matching.

**Record Decision Data** is on by default: the bot keeps a small per-turn record of its decisions so a career can be reviewed later. It is stored on your device only, nothing is uploaded, and it is independent of Debug Mode. Turn it off to minimize storage use.

# Documentation

| Document | What it covers |
|---|---|
| [TROUBLESHOOTING.md](TROUBLESHOOTING.md) | Stops, stuck screens, unsupported resolutions, and how to report a bug |
| [PRESETS_GUIDE.md](PRESETS_GUIDE.md) | What a preset changes, how to choose one, why some look unusual |
| [HOW_IT_WORKS.md](HOW_IT_WORKS.md) | The decision engine, training scoring, racing, items, and per-scenario logic |
| [CHANGELOG.md](CHANGELOG.md) | Version-by-version changes |
| [CONTRIBUTING.md](CONTRIBUTING.md) | What belongs in the repository, and the publication rules |

# Device setup extras

## To set the phone's resolution to 1080p (faster and more accurate)

> [!NOTE]
> This only works when downscaling. If your device's official resolution is lower than 1080p it will most likely not work.

1. Install the [**aShell You**](https://github.com/DP-Hridayan/aShellYou) app. This allows you to run adb commands locally on your Android device, but requires [**Shizuku**](https://github.com/RikkaApps/Shizuku).
2. Install [**Shizuku**](https://github.com/RikkaApps/Shizuku), then start it by following [these instructions](https://shizuku.rikka.app/guide/setup/#start-via-wireless-debugging).
3. With **Shizuku** started, you can then use **aShell You** to send the following adb commands:
   - **Change resolution to 1080p:** `wm size 1080x1920 && wm density 240`
   - **Revert to original:** `wm size reset && wm density reset`

    You can also bookmark the commands for your own convenience.

Alternatively, do the same from a computer:

1. Install [**adb**](https://developer.android.com/tools/releases/platform-tools). Add the platform-tools folder to `PATH` via the `Environment Variable` setting under `View advanced system settings` so the terminal knows the `adb` command. You may need to restart your computer for the change to be picked up.
2. Open a new terminal (cmd, PowerShell, etc).
3. Plug in your Android device via USB. Executing `adb devices` will show your connected device when `Settings > Developer options > USB Debugging` is enabled. There may be a popup on the device asking you to allow the ADB connection. Wireless ADB is also available via `Settings > Developer options > Wireless debugging`.
4. Execute the following individually:
    - **Change resolution to 1080p:** `adb shell wm size 1080x1920` and `adb shell wm density 240`
    - **Revert changes:** `adb shell wm size reset` and `adb shell wm density reset`

> [!WARNING]
> If your home button disappears, reset the DPI back to default.

> [!TIP]
> Use 1.0 scaling and an 80% confidence threshold for best results in 1080p natively.

## To view logs in real time

The bot also writes a log file per career, which is the thing to attach to a bug report (see [TROUBLESHOOTING.md](TROUBLESHOOTING.md)). To watch live instead:

1. Install `Android Studio` and open any project so the `Logcat` console appears at the bottom.
2. Connect your device:
   - **USB:** enable `Developer Options` and `USB Debugging`, then connect by cable.
   - **Wireless:** enable `Wireless debugging` in Developer Options and pair with the code or QR.
   - **Emulators:** enable the emulator's ADB option (usually `127.0.0.1:5555`). If Android Studio still does not see it, run `adb connect 127.0.0.1:5555` from a terminal. `adb disconnect` first gives you a clean slate.
3. Select your device in Logcat's device dropdown.
4. Filter with `package:com.lhceist41.uma_auto_plus [UAA]`, or just `[UAA]`.

# For developers

This project is a React Native frontend configured via Expo over a Kotlin and OpenCV backend.

1. Clone or download the repository.
2. Make sure the YOLOv8 model file `best.onnx` is present in `android/app/src/main/assets/`. Without it the YOLO stat detection feature does not work; template matching still functions as a fallback.
3. Run `yarn install` from the root directory to install frontend dependencies.
4. Run `yarn start` (or `npx expo start`) for the Metro server.
5. Format and lint with `yarn format` (`yarn format:tsx` runs Prettier over `**/*.tsx` only, so plain `.ts` files are not covered; `yarn format:kt` runs Ktlint against [android/.editorconfig](./android/.editorconfig)).
6. `yarn android` compiles and installs on a connected device. `yarn build` produces a release APK.

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

# License

Licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).
