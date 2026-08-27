# Windows host companion

The host companion is an optional input transport for UMA Auto+. Normal operation is Accessibility only, and the app stays fully usable that way. When it is paired and enabled, the companion adds a single bounded list swipe the automation can call on: the Debug Settings swipe diagnostics use it directly, and a normal launch may fall back to it for recovery only after an Accessibility scroll has already failed. It exposes two authenticated operations over an ADB reverse connection, health and that one bounded list swipe, and the Android side always decides whether the list actually moved. It cannot select a card, advance a launch, run a generic shell command, change package settings, or install software. With no companion paired, the automation stays Accessibility only.

## Requirements

- Windows 10 or later
- Android SDK Platform Tools with `adb.exe`
- Go 1.22 or later to build from source
- Android 7.0 (API 24) or later on the paired target
- One supported Android target running at 1080x1920 and 240 DPI, or 1080x2340 and 450 DPI

## Build

From a Windows command prompt:

```bat
cd tools\host-companion
go test ./...
go build -trimpath -buildvcs=false -ldflags="-s -w" -o build\uma-host-companion.exe .
```

The executable is written under `build\`, which is intentionally ignored by Git. Rebuilding from the same source with the same Go toolchain and target produces the distributable executable without modifying the Android release workflow.

## Pair one target

Connect the emulator or phone first, then run:

```bat
build\uma-host-companion.exe pair
```

The helper checks the usual Android SDK locations and `PATH` for `adb.exe`. Pass its exact path when it is elsewhere:

```bat
build\uma-host-companion.exe pair --adb "C:\Android\platform-tools\adb.exe"
```

If more than one ready target is listed by `adb devices -l`, pairing stops. Choose one explicitly using the serial shown by ADB:

```bat
build\uma-host-companion.exe pair --serial "127.0.0.1:16384"
```

Pairing validates the app package, Android version, display profile, and target binding. It stores the selected serial and a random secret in the current Windows profile, then prints a pairing code once. Treat that code as a password.

In Android Debug Settings:

1. Paste the code into **Host Input Pairing Code**.
2. Select **Accessibility + Host Companion** only when running a host swipe diagnostic.
3. Leave **Accessibility Only** selected for normal operation.

Pair again after changing the selected target or its display profile. Pairing never scans ports, starts or kills the ADB server, or picks the first target when more than one is ready.

## Run

Start the helper before the diagnostic:

```bat
build\uma-host-companion.exe run
```

It listens only on the Windows loopback interface, creates one exact `adb reverse` mapping for the paired target, authenticates the app, and accepts one app connection with one request in flight. A stored TCP target gets at most one reconnect attempt while offline. Each reconnect revalidates the target binding before the reverse mapping is restored.

The helper stops with Ctrl+C and removes only its own reverse mapping. It records protocol version, redacted target binding, request ID, scope, operation, timing, exit classification, and result. Pairing secrets, device serials, screenshots, screen text, names, and game resources are not logged.

## Safe diagnostics

The Android Debug Settings page provides separate Borrow list and Legacy Sparks list swipe diagnostics. Park the game on the named list before starting the bot. Each diagnostic:

- proves the intended screen from a fresh Android capture;
- checks authenticated companion health and foreground package state;
- sends exactly one bounded swipe request;
- takes a new Android capture after the request;
- reports `MOVED`, `NO_EFFECT`, or `UNCERTAIN`; and
- stops without selecting anything or continuing the launch.

An `EXECUTED` transport result means only that the fixed ADB command exited successfully. It is never treated as proof that the list moved. A host command timeout or a lost response after dispatch might have occurred reports `AMBIGUOUS`, closes the session, and does not repeat the swipe.
