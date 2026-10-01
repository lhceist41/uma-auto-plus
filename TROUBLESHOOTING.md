# Troubleshooting

Common issues and how to fix them. If none of this helps, ask in `#help` on the [UMA Auto+ Discord](https://discord.gg/JNhCDs4MSF), or open an issue (see [Reporting a bug](#reporting-a-bug)) and **attach a log file**: that's the single most useful thing for diagnosing a stop.

## The bot stopped or got stuck

Most stops come down to one of these three.

### 1. Unsupported screen resolution

Template matching is calibrated for **1080×1920 @ 240 DPI** or **1080×2340 @ 450 DPI** (Samsung). On anything else, detection misfires and the bot stalls. Set your emulator/device to one of those (see the resolution steps in the [README](README.md#to-set-the-phones-resolution-to-1080p-faster-and-more-accurate)), or use the `Basic Template Matching Test` under **Settings → Go to Debug Settings → Debug Tests** to find a working custom scale.

### 2. The emulator killed the Accessibility service (MuMu)

MuMu — and some other emulators — silently disable the Accessibility service mid-run to save resources. When that happens, the bot's taps and swipes stop landing even though the screen still updates, so it looks "frozen" on a perfectly normal screen, or an overnight queue quietly dies.

The bot can heal this on its own, but only if it's allowed to re-enable the service. Grant the permission **once**:

```
adb shell pm grant com.lhceist41.uma_auto_plus android.permission.WRITE_SECURE_SETTINGS
```

Run it from a PC with `adb`, or from the device itself using **aShell You + Shizuku** (the same tools used for the resolution steps in the README). On MuMu, if `adb` is not recognized, use MuMu's own copy in front of the command: `"C:\Program Files\Netease\MuMuPlayerGlobal-12.0\shell\adb.exe" -s 127.0.0.1:16384 shell pm grant com.lhceist41.uma_auto_plus android.permission.WRITE_SECURE_SETTINGS` (the folder name can differ by MuMu version; the port is in MuMu's settings). Without this grant, the bot can't recover when the emulator kills the service, and unattended runs will stop the first time it happens. Home shows whether the permission is granted.

MuMu can also leave the service switched on while the bot's taps silently stop landing. No fix from inside the app is known for that: when restarting the service changes nothing, the bot stops the queue and says its taps stopped having any effect. Restart MuMu (or the device), then press Start again.

> [!TIP]
> On MuMu, turning off background resource throttling and any "smart" power-saving makes the service death much rarer in the first place.

### 3. The trainee rotation can't find a trainee

If you use the run queue's trainee rotation and it stops with `Trainee '...' not found`, the bot scanned your roster and couldn't match the trainee you queued. Check that:

- You actually **own** that trainee — the rotation picks from your in-game roster, not from the preset list.
- The trainee's name in the rotation matches the in-game name.

### 4. "Start again from UMA Auto+ with an explicit launch choice"

Tapping the floating overlay button by itself cannot start a new automation session. If you see this message, or the app otherwise refuses to resume after only tapping the overlay, open UMA Auto+ and press `Start` there first; the overlay button then finishes setting up automation on the training screen as usual.

### 5. "Not started, and nothing was spent"

UMA Auto+ can refuse a Start rather than risk a career on the wrong settings or a settings file it cannot trust. Either way, nothing is spent and no career is touched.

- **The settings did not reach the bot.** If what the bot is about to use does not match what UMA Auto+ just checked when you pressed Start, it refuses instead of running on the mismatch. This refusal shows no dialog: the notification reads "Not started" and gives the reason, and the app's message log has the same line in red. Force stop UMA Auto+ (Android Settings, Apps, UMA Auto+, Force stop), reopen it, turn its accessibility service back on if it was switched off, then press Start again.
- **The settings file could not be verified.** If UMA Auto+ cannot safely use its saved settings file, it refuses the same way. If your device's storage is full, free some space first, then force stop and reopen UMA Auto+ as above. Reopening checks the file again and restores it from the last good backup if it is damaged; a damaged file is renamed and kept next to the current one (as `settings.db.corrupt-<time>`) rather than deleted, though reaching it needs a rooted device or a debug build, so it is not something most players can pull themselves. If this message keeps coming back, please report it as a bug with your usual log file attached (see [Reporting a bug](#reporting-a-bug)).

### 6. "The game ended its session" or "reopening the game did not bring it back"

When the game sits idle for a long time, for example overnight, it ends its session and shows a **Session Error** whose only button is Title Screen. Between runs, the bot taps Title Screen itself only when no career is in progress: you pressed Start with the game on its home screen and the queue was not resuming a career, or the previous career finished and the game got back to its home screen. It then taps through the title screen, closes the notices the game shows after logging in, and launches the career. It does this once per launch, and a second Session Error stops the queue. With a career in progress the queue stops with "the game ended its session and needs to go back to its title screen": tap Title Screen in the game, wait for its home screen, then press Start in UMA Auto+.

In the same no-career situation, if the bot cannot recognise the game's screen, it brings the game back to the front once (starting it again if it had closed) and starts the launch over. If the game still shows nothing the bot knows, the queue stops with "the game showed a screen the bot could not recognise, and reopening the game did not bring it back": open the game, check what it shows, then press Start in UMA Auto+.

The title screen is recognised only at 1080×1920. Neither recovery has been seen working on a device yet.

### 7. The game asked to download additional data

After a game patch the game can ask to download additional data. The bot taps **OK** (never Cancel) and waits, tapping nothing, for up to about 10 minutes for the download to finish, between runs and during a career. This has not been seen on a real patch yet. If the game instead forces a **Data Update** in the middle of a career (its only button is **Title Screen**), the bot stops safely without tapping anything. Tap **Title Screen**, let the game reload and any download finish, then press Start again. If the queue stops asking you to answer the game's data prompt, do the same: tap **OK** or **Title Screen**, let any download finish and press Start again. The stopped queue is not continued: Start begins a new queue from run 1 and first picks up the career in progress. If your rotation mixes scenarios, select that career's trainee and scenario before you press Start.

### 8. The queue paused after a career

If you used **Stop after this career** (on Home or on the dashboard), the bot finished that career and paused the queue on purpose: Home says "Queue paused after run 2/4" and the notification says you paused it. Press **Start** within 24 hours, with Run Queue on and the same number of runs, to continue with the next run. An in-app update is refused while a paused queue is saved; resume or discard it on Home first.

### 9. The bot misses a button near the game's left edge

Android's floating accessibility button (the "U+" bubble) can sit over the game's left edge. Drag it elsewhere, or switch the accessibility shortcut to a gesture.

## Grand Concert

The bot pages the Scenario Select carousel to Grand Concert like any other scenario, so it works with
the run queue, trainee rotation, and automatic TP restore the same as URA Finale, Unity Cup, or
Trackblazer. Once the career is running, the scenario's own systems are automated on top of the shared
career loop: the lessons, all five concerts, and the career-end sequence, which spends leftover lesson
points, buys the career-end skills, handles the spark set, and walks the game back to the home screen
without you.

If the Lesson shop shows something it doesn't recognize mid-visit, it backs out to the career screen
without spending and tries again on a later turn; your career and queue are unaffected.

If a concert screen still isn't recognized after a few retries, the bot stops the run with the career
untouched, and the rest of the run queue stops with it, because the game holds only one career at a
time. It names the concert flow and how many attempts it made, not the exact screen. Handle that screen in the game and return to the career
screen, then press Start in UMA Auto+ and tap the overlay button to finish the same career. Nothing is
spent when that happens.

## Skills aren't bought, or the wrong event option is picked

Apply a **preset** for the trainee you're running: on **Home**, tap **Select Trainee Preset...**, tap the trainee (and outfit), then tap **Apply** on the scenario you're running; applying a preset also sets the scenario. The presets carry the skill-purchase plans and per-event choices. Without one, the bot falls back to generic scoring.

## The Racing Plan is missing information

Older saved defaults can lack information the bot needs, causing it to ignore the whole plan. Newly generated defaults include that information, but saved and imported plans are not automatically repaired. The default still contains all races and can have scheduling conflicts.

Before replacing an affected plan, use **Settings > Settings Management > Export Settings** and save a copy through the share dialog. Keep a record of the races and dates you want; importing the old export later will also restore its incomplete plan.

On **Racing Plan**, use **Clear** to remove the entire current selection, then pick the required races and dates again. Search, minimum fans, terrain, grade and distance filters limit the visible list; OP and Pre-OP races are excluded. If a wanted race is unavailable, keep your saved copy rather than substitute a different race. **Add All** replaces the entire plan with the currently filtered list, including new priorities; it does not append to your selection or restore the previous schedule. Review the resulting selections and plan check before use. Readable data can still have same-turn conflicts or consecutive-race warnings.

## Updating from inside the app

The **Update** button in the **Update Available** dialog downloads the new release for your device and hands it to Android's installer. Android then shows its own confirmation: tap **Install**. UMA Auto+ closes while it updates; open it again afterwards.

The first time, before anything is downloaded, the dialog asks you to allow UMA Auto+ to install apps: tap **Open settings**, turn on **Allow from this source** on Android's **Install unknown apps** screen, go back, and tap **Continue** in the dialog. If you denied it, open **Android Settings**, then **Apps**, then **UMA Auto+**, then **Install unknown apps** (on some devices it is under **Special app access**), turn it on, then tap **Update** again. You can also use **Open release page** to download the file in your browser instead.

The update is refused while the bot is running, while Start is armed (the overlay button is showing), or while an interrupted queue is saved. Press Stop first, or resume or discard the saved queue on Home.

## Watch your queue from your PC

The dashboard is part of the Remote Log Viewer, which is off by default. It only shows what the bot is doing, and it can be opened only on the device or from a computer connected to it over ADB. The steps are in [README.md](README.md#watch-your-queue-from-your-pc): turn on **Enable Remote Log Viewer** under **Settings**, **Debug Settings**, press **Start**, connect ADB (on MuMu, `adb connect 127.0.0.1:<the ADB port from MuMu's settings>`; on a phone, USB or Wireless debugging), run `adb forward tcp:9000 tcp:9000` with your Server Port on both sides, open `http://localhost:9000`, and enter the access code shown on the Debug Settings page. On Windows with MuMu, double-clicking [`tools/open-dashboard.cmd`](tools/open-dashboard.cmd) does the connect, forward and browser steps for you. If `adb` is not recognized, MuMu's own copy is at `C:\Program Files\Netease\MuMuPlayerGlobal-12.0\shell\adb.exe` (the folder name can differ by MuMu version); put that full path in quotes in front of the commands.

- **The page does not open.** The viewer runs only after Start is pressed and stops when you press Stop. Run `adb forward` again after MuMu or the phone restarts, and use the same port on both sides. On MuMu, double-clicking `tools/open-dashboard.cmd` again repeats both steps.
- **The code is refused.** The code changes each time you press Start; enter the one shown now on the Debug Settings page.

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
