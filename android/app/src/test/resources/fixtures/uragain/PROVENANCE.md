# URA gain-band fixture provenance

Each PNG is one stat column's gain band from a URA Finale training screen on a 1080x2316 phone, cut exactly where
`determineStatGainFromTraining` cuts it: 150x82 px at (Skill Pts header centre + (-934 + 180 x column), -103). Native
RGB, no trainer name, ID or other identifying data. Values were checked by eye on the frames.

The two 2026-10-10 bands come from one analysis frame of an unattended URA Finale career (Stamina training selected);
the 2026-10-06 bands from screenshots of training screens in URA Finale careers.

| Fixture | Captured | Column | Screen shows |
|---|---|---|---|
| phone_ura_stamina_14.png | 2026-10-10 | Stamina | +14 (the bot read 4) |
| phone_ura_guts_7.png | 2026-10-10 | Guts | +7 |
| phone_ura_power_11.png | 2026-10-06 | Power | +11 |
| phone_ura_stamina_13.png | 2026-10-06 | Stamina | +13 |
| phone_ura_power_19.png | 2026-10-06 | Power | +19 |
| phone_ura_guts_10_plus_clipped.png | 2026-10-06 | Guts | +10, its "+" cut by the crop's left edge |
| phone_ura_power_18.png | 2026-10-06 | Power | +18 |
| phone_ura_speed_24.png | 2026-10-06 | Speed | +24 |
| phone_ura_wit_empty.png | 2026-10-06 | Wit | no gain (outfit art only) |
| phone_ura_stamina_empty.png | 2026-10-06 | Stamina | no gain (outfit art only) |

`candidates.csv` lists every box the template matcher judged in these bands (masked TM_CCORR_NORMED at or above
0.9), replayed with OpenCV 4.13 on the same gray the bot computes (COLOR_BGR2GRAY on Android's RGBA, premultiplied
templates): template, top-left x/y in the band, ccorr, the equal-pixel share over the whole template box and over its
opaque pixels on the clean band (`whole_ratio`, `opaque_ratio`) and in the matcher's own search copy, where earlier
rejected boxes of the same template are painted black (`searched_whole_ratio`, `searched_opaque_ratio`), and the
masked Pearson correlation. The replay reproduced the bot's logged read of the 2026-10-10 frame (Stamina 4, Guts 7).
JVM tests have no native template search, so the test judges these boxes rather than searching.

No emulator URA training frame with settled gain digits was available: the only one, from 2026-10-10, caught the
gains mid-animation.
