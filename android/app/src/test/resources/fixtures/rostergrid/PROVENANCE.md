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
