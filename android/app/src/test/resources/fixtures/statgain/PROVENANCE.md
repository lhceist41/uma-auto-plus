# Stat-gain row fixture provenance

Training-screen captures from three unattended Grand Concert careers (Hishi Amazon, Sweep Tosho,
Bamboo Memory) on 2026-09-30 and 2026-10-01, saved with Debug Mode on as each facility's analysis
frame (`gc_train_NNNN_<FACILITY>.png`, 1080x1920 on MuMu). Each fixture is the stat-gain band of
one frame, rows 1125-1264 at full width, channel-swapped from the bot's R/B order into native RGB.
In fixture coordinates the "Skill Pts" header anchor is at (971, 134).

Once a training would take a stat past 1200, the game draws that stat's gains in gold behind a
double chevron, on both the gain row and the music-note bonus row above it. The template matcher
read every such row as 0.

| Fixture | Source frame | Screen |
|---|---|---|
| gold_speed_1281.png | gc_train_0496_SPEED | Speed 1281: Speed gold +31 and +16; Power orange +31 and red +17 |
| gold_speed_1169.png | gc_train_0471_SPEED | Speed 1169: Speed gold +32 and +6 |
| gold_speed_1221.png | gc_train_0241_SPEED | Speed 1221: Speed gold +16 and +8; Power +19 and bonus-row +10 |
| gold_speed_1166.png | gc_train_0736_SPEED | Speed 1166: Speed gold +36 and +7 |
| gold_speed_1218.png | gc_train_0481_SPEED | Speed 1218: Speed gold +14 and +5; trained at 0% failure, and the next stat read showed Speed +19 |
| orange_speed_1169_guts.png | gc_train_0474_GUTS | Guts training at Speed 1169: the Speed side effect (+2, +1) stays below 1200 and stays orange |
| wit_art_between_digits.png | gc_train_0060_WIT | Wit +10 with warm background art inside the white outline between the digits |

Every value above was checked by eye on the frame. The reader's glyph templates were measured on
the same careers' 745 frames.

`logged_template_matches.txt` lists the template matcher's sorted per-row matches (template name and
centre x in the row crop) as logged by `constructIntegerFromMatches` on MuMu (single-row URA Finale and
two-row careers, 2026-09-26 to 2026-10-05) and on a 1080x2316 phone (Trackblazer, 2026-10-06): every
distinct list without two matches closer than 12 px, 329 lists covering 10,111 reads.
