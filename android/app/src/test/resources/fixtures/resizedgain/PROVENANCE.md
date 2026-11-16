# Resized gain-band fixture provenance

Training gain bands as the bot sees them on a screen that is not 1080 wide: the capture is resized to 1080 wide before any
reading, so every pixel is resampled. Each PNG is made with OpenCV 4.13 from an existing fixture whose values were checked by
eye, using the bot's own resize for that screen width:

- `_w720`: the 1080 band shrunk to two thirds with INTER_AREA (a 720-wide screen), then enlarged back with INTER_LINEAR, as the
  bot enlarges a 720-wide capture.
- `_w1440`: the 1080 band enlarged to four thirds with INTER_LINEAR (a 1440-wide screen), then shrunk back with INTER_AREA, as
  the bot shrinks a 1440-wide capture.

| Files | Source | Cut |
|---|---|---|
| `phone_ura_*_w720.png`, `phone_ura_*_w1440.png` | the same-named `fixtures/uragain` band (single-row URA Finale, 150x82) | the band padded to 150x84 by repeating its last row, resized, cropped back to 150x82 |
| `<fixture>_col<N>_w720.png`, `<fixture>_col<N>_w1440.png` | the same-named `fixtures/statgain` band (two-row, 1080x140) | the band padded to 1080x141 by repeating its last row, resized, then stat column N's 150x110 window at x = 37 + 180 * N, y = 14 (row 2 above, row 1 below) |

Columns: 0 Speed, 2 Power, 4 Wit. The gains each band shows are those of its source fixture (see that folder's
`PROVENANCE.md`): Power +31 and +17 in `gold_speed_1281`, Power +19 and +10 in `gold_speed_1221`, Speed +2 and +1 in
`orange_speed_1169_guts`, Wit +10 in `wit_art_between_digits`.

The URA bands are resized as crops, so the outermost two pixels of each edge see repeated border pixels instead of the
neighbouring screen. A resampled 1080 frame is a stand-in: a real phone renders at its own size, and text can come out a
little smaller than the layout. No trainer name, ID or other identifying data.
