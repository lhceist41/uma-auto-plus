# Title-screen fixture provenance

Full-resolution (1080x1920) `adb screencap` captures from MuMu, game version 1.35.1. Ground truth for
the pixel probe in `utils/TitleScreenProbe.kt`.

| Fixture | Shows |
|---|---|
| title_screen.png | The title screen waiting for a tap: "TAP TO START", the CRIWARE badge (bottom left) and the round menu button (bottom right). |
| title_screen_other_background.png | The same title screen a few seconds later, over a different frame of its animated background. |
| title_logging_in.png | The title screen right after the tap, logging in: "Entering the starting gate... 100.0%" in place of "TAP TO START". Both corner controls stay, so the probe matches it too. |
| notices_over_home.png | The Notices dialog the game shows over Home after logging in following the daily reset. |
| session_error.png | The "Session Error / Returning to Title screen due to inactivity." dialog with its one Title Screen button, over a dimmed Scenario Select. |
| now_loading.png | The "Now Loading..." screen between the title and Home. |

The first two and the last three come from a morning Start on 2026-09-27 after the game had idled
overnight: the bot met the Session Error, and the frames were taken while the player followed it to
Home by hand. `title_logging_in.png` comes from a cold start of the game on 2026-09-26.

## Source and colour

`adb screencap` writes the device colours as they are, so these PNGs need no red/blue swap (unlike
the bot's own failure-camera frames; see `postcareer/PROVENANCE.md`). Each file was re-encoded
losslessly for size; the decoded pixels are identical to the capture. Nothing in the game UI was
altered.

The bot's floating overlay button ("U+", left edge, about x 0-70, y 425-490) appears in every frame.
The probe reads only the bottom corners, away from it.

No personally identifying information is present: the title screen hides the trainer ID ("Tap here
to display"), and the dialogs show only game notices and UI.
