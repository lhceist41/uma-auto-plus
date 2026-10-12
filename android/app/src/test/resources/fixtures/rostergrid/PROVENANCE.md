# Roster grid swipe fixture provenance

`swipe_probe_columns.png` holds five Trainee Select page swipes from MuMu (1080x1920, Filters OFF, sort Name Asc), captured with
`screencap` while the bot's own drag (x 540, y 1324 to 1134, 850 ms) paged the roster on 2026-10-01. No trainee was tapped and no
career was started.

Only what `TraineeGridScroll.measureDeltaPx` samples is kept: the pixels of the three probe columns (x 340, 540 and 739) for y 1000 to
1600, copied unchanged from the captures. The strip is 30 px wide: ten frames of three columns, in the order swipe 1 before, swipe 1
after (1.2 s later), swipe 2 before, and so on to swipe 5 after. The page in a swipe's after frame is the page in the next swipe's
before frame, which gives four static pairs.

Measured content movement: 292, 286, 288, 274 and 274 px. The fixed Filters / Name / Asc bar starts near y 1457 and does not move with
the list, so a sampling band that reaches it (the old bottom at y 1536) cannot match the moved content. If the probe columns or the
band change, regenerate the strip from the captures rather than editing it.

The frames show only game portraits and UI; no personally identifying information is present.

## Parked last row strips

Three column-0 strips (x 70 to 209, unchanged pixels, saved as RGBA) of Trainee Select with Filters OFF, cut from `screencap`
captures. They pin where the game parks the last roster row at the end of the list.

| Fixture | Source capture | Strip rows (screen y) | Shows |
|---|---|---|---|
| `tall_bottom_col0.png` | Samsung SM-S918B, 1080x2316, inset 94, 2026-10-11, Stars Desc, 37 trainees, list at its end | 1150 to 1959 | Clipped row, a full row, and the parked last row at y 1621 to 1823 |
| `tall_top_col0.png` | Same phone, 2026-10-06, list at its top | 1150 to 1959 | Rows at y 1241 to 1442 and 1476 to 1677, a third row reaching below the Filters bar |
| `mumu_bottom_col0.png` | MuMu, 1080x1920, 2026-10-01, Name Asc, 36 trainees, list at its end (the last frame of the swipe strip above) | 950 to 1559 | Rows at y 990 to 1192 and the parked last row at y 1225 to 1427 |

The Filters bar starts at y 1458 on 1080x1920 and 1854 on the phone: both it and the parked row move by the full extra height (the
bottom band). The strips show only portraits, stars and the grid background; no trainer name, ID or currency is in frame.
