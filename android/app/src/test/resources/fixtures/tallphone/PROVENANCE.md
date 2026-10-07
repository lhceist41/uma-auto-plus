# Tall phone fixture provenance

Full-resolution (1080x2316) `adb exec-out screencap -p` captures from a Samsung SM-S918B on Android 16,
taken on 2026-10-07 while two Grand Concert careers and one spark reroll were played by hand. The window
cutout inset on this phone is 94 px, so the bands in `utils/ScreenBands.kt` put TOP at +94, DIALOG at
+198, MIDDLE at +245 and BOTTOM at +396 from the 1080x1920 coordinates. These frames pin the band of
every Grand Concert and career-end spark probe in `GrandConcertAndSparksOnTallPhoneTest`.

| Fixture | Shows |
|---|---|
| career_scheduled.png | Grand Concert career screen with the Lessons button lit and its pink Scheduled badge |
| technique_list.png | Lesson list with three technique cards, balances 50/30/26/26/10 |
| song_list.png | Lesson list with three song cards |
| learn_confirm_technique.png | Learn confirmation for an affordable technique (Cancel / Learn) |
| schedule_confirm_technique.png | Schedule confirmation with the red "Not enough performance points" band |
| concert_pending.png | 1st Concert pending screen: Hype banner, Goal ribbon, Lessons and Concert buttons |
| concert_confirm.png | "Ready to start the concert?" confirmation of the 1st Concert (Cancel / Start) |
| concert4_pending.png | 4th Concert pending screen (second career) |
| concert4_confirm.png | Start confirmation of the 4th Concert (second career) |
| concert_playback.png | Concert playback with the skip disc at the bottom right |
| concert_success_banner.png | Concert result SUCCESS banner with the green Next |
| bonuses_updated.png | "Bonuses Updated!" popup (Close / Confirm) over the career screen |
| active_bonuses_panel.png | "Active Concert Bonuses" panel with its white Close |
| career_complete.png | Complete Career screen: remaining performance points, Skills, Complete Career and Lessons |
| training_guts_selected.png | Training with Guts selected: Performance Points panel, a "+10" gain on the Vi row |
| sparks_screen.png | SPARKS screen, 3-row rolled set, Reroll Sparks + Confirm |
| keep_confirmation_plain.png | Keep confirmation with a plain `Sparks` pill over the dimmed SPARKS screen |
| sparks_rerolled_result.png | "Sparks Rerolled" result, 4-row rerolled set, single Next |
| spark_selection_intro.png | "Spark Selection" intro dialog ("Select which Sparks to keep.") |
| pager_original.png | Spark Selection pager, "Original Sparks" page, page dot 2 lit |
| pager_rerolled.png | Spark Selection pager, "Rerolled Sparks" page, page dot 1 lit |
| confirmation_rerolled.png | Keep confirmation with the `Rerolled Sparks` pill and the 4-row rerolled set |

## Source and colour

`adb screencap` writes the device colours as they are, so no red/blue swap was needed. The files are
8-bit RGBA (the shape `FixturePng` reads) at the full 1080x2316 size, so every probe reads its real
coordinates. To keep them small, everything farther than 48 px from a pixel the test reads (wrong-band
shifts included) is painted opaque black; the kept pixels are exactly the capture's. A new probe point
or OCR region outside the kept areas needs that area restored from the original capture first. The bot
was not running, so no overlay button appears.

No personally identifying information is present: the frames show game characters, stats, sparks
and game UI, and no trainer name or ID. The game hides the Android status bar.
