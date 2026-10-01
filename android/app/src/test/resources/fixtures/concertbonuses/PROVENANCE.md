# Concert bonus popup fixture provenance

Live 1080x1920 frames from the Grand Concert career whose run stalled on 2026-09-30 (22:33), kept as the
ground truth for the "Bonuses Updated!" and "Active Concert Bonuses" popups.

| Fixture | Shows |
|---|---|
| event_choices_live.png | A trainee event with its three choices at 22:33:26, with the persistent Skip pill below it. A real event, not a dialog. |
| bonuses_updated_live.png | "Bonuses Updated! / Concert bonuses updated!" (Close and Confirm) over the career screen at 22:33:36, whose Skip pill the bot read as an event cutscene. |
| active_bonuses_live.png | The "Active Concert Bonuses" panel (Close only) that the blind tap reached, which no dialog matched. |

The first two are stills cut from a screen recording and are straight RGB. The third is the bot's own
failure capture, which stores red and blue swapped; its channels were swapped back so it is straight RGB like
the rest. No personally identifying information is present: the frames show game UI and a trainee portrait.

## Ordinary dialogs with the same layout

The "Bonuses Updated!" popup's green title band and green right-hand button are the layout of the game's ordinary centred dialogs, so
the pixel probe alone cannot tell them apart. These straight RGB 1080x1920 `screencap` frames from earlier runs pin that:

| Fixture | Shows |
|---|---|
| warning_live.png | The consecutive-race Warning (Cancel and OK) over the career screen. |
| auto_select_live.png | The Auto-Select confirmation (Cancel and OK) over the deck screen. |
| restore_tp_live.png | The Confirm dialog offering to restore TP (No and Restore). |
| bonuses_updated_second_live.png | A second "Bonuses Updated!" popup, over a different career screen and background. |

## Privacy edit

`auto_select_live.png` and `restore_tp_live.png` showed the player's Team Rank badge and account currency counts in the top bar. In both
frames the region x 0-1079, y 0-519 was replaced with flat gray before they were stored; nothing in that region is sampled by any test (the
title read samples y 525 and below). The other five frames hold no trainer name, rank, ID or friend code: only the career HUD (turn count,
goal text, energy bar), a trainee portrait, game dialogs, and the Android notification shade in `warning_live.png` ("Run 1 of 2"), which
carries no account detail. Each stored frame was opened and checked after the edit.
