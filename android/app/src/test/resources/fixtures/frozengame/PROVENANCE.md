# Frozen-game fixture provenance

1080x1920 `adb exec-out screencap -p` captures from MuMu (Android 12, x86_64), 2026-10-09 and 2026-10-10.
Each fixture keeps **only the 576 pixels that `homeLumaGrid` samples** (32x18 points); every other pixel is opaque
black. The grid of a fixture equals the grid of its source frame, and no text, name, ID or balance can be read from
576 isolated pixels.

| Fixtures | Source | Shows |
|---|---|---|
| freeze_a_1..4 | game froze mid-career after a MuMu self-update | event dialogue frame, 4 captures over about 45 s; one manual tap between 2 and 3 |
| freeze_b_1..4 | the game froze on its Home lobby, idle | Home lobby, 4 captures over about 2 minutes |
| freeze_c_1..3 | the game froze on the Training screen in a Unity Cup career | Training screen, captures 2 minutes apart, the last after one manual Back tap |
| freeze_d_1..3 | the Unity engine froze during an app install | Home lobby, 3 captures over about 50 s |
| live_training_1..2 | a running career, same Training tab, within one second | idle animation |
| live_training_3..4 | a running career's Training analysis, about 1 s apart | a live screen |
| still_event_1..3 | a running career's training-event choice screen, 4 s | a quiet live screen: still from one capture to the next, 2 cells drift over the 4 s |

The bot's floating button sat at (626, 33), 62 px wide, in the freeze captures; only its sample point (658, 53)
changed between frozen frames.
