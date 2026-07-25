# Troubleshooting

Common issues and how to fix them. If none of this helps, open an issue (see [Reporting a bug](#reporting-a-bug)) and **attach a log file** — that's the single most useful thing for diagnosing a stop.

## The bot stopped or got stuck

Most stops come down to one of these three.

### 1. Unsupported screen resolution

Template matching is calibrated for **1080×1920 @ 240 DPI** or **1080×2340 @ 450 DPI** (Samsung). On anything else, detection misfires and the bot stalls. Set your emulator/device to one of those (see the resolution steps in the [README](README.md#to-set-the-phones-resolution-to-1080p-faster-and-more-accurate)), or use the `Basic Template Matching Test` under **Settings → Debug Tests** to find a working custom scale.

### 2. The emulator killed the Accessibility service (MuMu)

MuMu — and some other emulators — silently disable the Accessibility service mid-run to save resources. When that happens, the bot's taps and swipes stop landing even though the screen still updates, so it looks "frozen" on a perfectly normal screen, or an overnight queue quietly dies.

The bot can heal this on its own, but only if it's allowed to re-enable the service. Grant the permission **once**:

```
adb shell pm grant com.lhceist41.uma_auto_plus android.permission.WRITE_SECURE_SETTINGS
```

Run it from a PC with `adb`, or from the device itself using **aShell You + Shizuku** (the same tools used for the resolution steps in the README). Without this grant, the bot can't recover when the emulator kills the service, and unattended runs will stop the first time it happens.

> [!TIP]
> On MuMu, turning off background resource throttling and any "smart" power-saving makes the service death much rarer in the first place.

### 3. The trainee rotation can't find a trainee

If you use the run queue's trainee rotation and it stops with `Trainee '...' not found`, the bot scanned your roster and couldn't match the trainee you queued. Check that:

- You actually **own** that trainee — the rotation picks from your in-game roster, not from the preset list.
- The trainee's name in the rotation matches the in-game name.

## Grand Concert: the bot won't start the career from the lobby

For Grand Concert you start the career yourself in game, then press Start. The bot cannot page the
scenario carousel to Grand Concert yet, so a run that begins on the lobby stops with an unsupported
scenario message rather than picking the wrong scenario. Once the career is running, the whole thing
is automated: the lessons, all five concerts, and the career-end sequence, which spends leftover
lesson points, buys the career-end skills, handles the spark set, and walks the game back to the
home screen without you.

For the same reason the run queue, trainee rotation, and automatic TP restore do not apply while
Grand Concert is selected. Those settings are not rewritten, just not honored for this scenario, and
they come back when you switch to another one.

If the bot does stop mid-career on a lesson or concert screen, it says which screen it was looking at
and leaves the career untouched. Handle that screen in game, return to the career screen, and press
Start to resume the same career. Nothing is spent and no career is lost when that happens.

## Skills aren't bought, or the wrong event option is picked

Apply a **preset** for the character you're running: **Home → pick a scenario → pick the character**. The presets carry the skill-purchase plans and per-event choices. Without one, the bot falls back to generic scoring.

## Reporting a bug

A useful report includes a **log file**. The bot writes one per career to:

```
/storage/emulated/0/Android/data/com.lhceist41.uma_auto_plus/files/logs/
```

Pull the most recent file (named `<Trainee>_<timestamp>.txt`) and attach it to your issue. From a PC:

```
adb pull "/storage/emulated/0/Android/data/com.lhceist41.uma_auto_plus/files/logs/<filename>" .
```

Then open an issue with the log, your device/emulator and resolution, the scenario and preset you ran, and what you expected versus what happened.
