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

## Native 1080 Unity Cup bands

Five more bands come from one unattended Unity Cup career (King Halo) on 2026-11-15, saved with Debug Mode on as each
facility's analysis frame (`unitycup_train_NNNN_<FACILITY>.png`, 1080x1920 on MuMu, read at native size). They are cut
and channel-swapped like the bands above, with the "Skill Pts" anchor at (971, 134).

| Fixture | Source frame | Screen |
|---|---|---|
| native_speed_13_2.png | unitycup_train_0011_SPEED | Speed +13 and bonus-row +2; Power +6 |
| native_speed_15_2.png | unitycup_train_0016_SPEED | Speed +15 and bonus-row +2; Power +9 |
| native_guts_8.png | unitycup_train_0019_GUTS | Speed +3, Power +4, Guts +8 |
| native_guts_8_power_6.png | unitycup_train_0004_GUTS | Speed +5, Power +6, Guts +8 |
| native_stamina_13.png | unitycup_train_0037_STAMINA | Stamina +13, Guts +11 |

Every value was checked by eye on the frame. With exact gray equality the matcher read +13 and +15 as +1 and the side
gains +3 and +6 as a lone "+".

`native_boxes.csv` lists, for each gain row of the facility's stats, every box the matcher's peak search visits (masked
TM_CCORR_NORMED of 0.9 or more, in search order per template as `rank`): top-left position in band coordinates, the
equal-pixel share at exact equality (`share0`) and within 2 gray levels (`share2`), and the Pearson correlation, from an
OpenCV 4.13 replay of the matcher that reproduced all 227 matches the bot logged for the career's first 50 frames.

## Gold Speed columns

Three crops of the Speed column only (169x125, both gain rows) from unattended Unity Cup careers saved with Debug Mode
on, channel-swapped into native RGB. The read window starts at x 0; the gain row's box starts at y 60 and the bonus
row's at y 5. No trainer name, ID or other identifying data.

| Fixture | Source frame | Screen |
|---|---|---|
| gold_speed_chip_8_w1440.png | unitycup_train_0251_SPEED, 2026-11-16, a 1440x2560 capture the bot resized to 1080 wide | Speed gold +8 and bonus-chip +8 |
| native_gold_speed_25.png | unitycup_train_0206_SPEED, 2026-11-15, native 1080x1920 | Speed gold +25 and bonus-row +2; trainee skin after the 5 reaches the column edge |
| native_gold_speed_24.png | unitycup_train_0216_SPEED, 2026-11-15, native 1080x1920 | Speed gold +24, no bonus row; trainee skin after the 4 reaches the column edge |

Every value was checked by eye on the frame.
