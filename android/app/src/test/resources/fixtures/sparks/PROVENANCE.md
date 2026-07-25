# Spark selection fixture provenance

Full-resolution (1080x1920) live captures of one complete Spark Reroll flow, taken on the
maintainer's emulator during the 2026-07-08 01:54-01:55 career finish (trainee "[Jokester
(star) Vibes] Tosen Jordan", hand-driven through the selection so both pager pages exist).
These are the only known captures of the post-spend selection screens and are the ground
truth for every pixel probe, geometry constant, and OCR region in
`utils/SparkScreenProbes.kt`.

| Fixture | Original archive file | Shows |
|---|---|---|
| sparks_screen.png | MuMu-20260708-015433-216.png | SPARKS screen, 10-row original set (9 full rows visible + row 10 clipped), Reroll Sparks + Confirm |
| confirm_reroll_dialog.png | MuMu-20260708-015436-668.png | "Confirm Reroll" spend dialog (30 TP, Cancel / Reroll Sparks), dimmed sparks screen behind |
| sparks_rerolled_result.png | MuMu-20260708-015444-252.png | "Sparks Rerolled" result, 7-row rerolled set, single Next |
| spark_selection_intro.png | MuMu-20260708-015448-432.png | "Spark Selection" intro dialog ("Select which Sparks to keep."), capture caught mid-animation (body text blurred; geometry unaffected) |
| pager_rerolled.png | MuMu-20260708-015454-433.png | Spark Selection pager page 1: heading "Rerolled Sparks", left+right chevrons, page dot 1 lit, wide Confirm |
| pager_original.png | MuMu-20260708-015500-352.png | Spark Selection pager page 2: heading "Original Sparks", page dot 2 lit, 8 of 10 rows visible |
| confirmation_original.png | MuMu-20260708-015531-768.png | "Confirmation" dialog: green title band, green "Original Sparks" set-name pill, all 10 rows, Cancel/Confirm |
| umamusume_details.png | MuMu-20260708-015537-839.png | Umamusume Details screen after the kept set was confirmed (post-selection boundary) |
| rating_record.png | MuMu-20260708-015544-462.png | "Rating Record Updated" screen (post-selection boundary) |

Archive source directory: the maintainer's MuMu screenshot folder
(`MuMuSharedFolder/Screenshots`). Files are byte-identical copies; only the names changed.

## Second capture set: the ordinary keep confirmation (2026-07-19)

The nine captures above are all POST-SPEND, so they contain only the two side-named pill
variants. The ordinary keep confirmation that every no-reroll career ends on carries a plain
`Sparks` pill, and its absence from the set above cost a live career a safe stop.

| Fixture | Source | Shows |
|---|---|---|
| keep_confirmation_plain.png | supervised validation capture, 2026-07-19 18:48 (Biwa Hayahide, Unity Cup) | "Confirmation" dialog with a plain green `Sparks` pill, the complete 11-row kept set, "Keep this set of Sparks?" / "You won't be able to reroll Sparks later.", Cancel + Confirm |

Captured with `adb exec-out screencap -p` (true RGB) at 1080x1920, SHA-256
`3161e67f62517c5059697f8454d20f56f19e83a59ed12eeffa109acd26c846d4`.

Note for anyone adding fixtures later: the bot's own failure camera (`saveBitmap`, used for
`nav_failure_*.png`) writes OpenCV BGR byte order, so its PNGs have red and blue swapped and
are unusable as color-probe fixtures. Always use an `adb screencap` of the same screen.

The 11 rows in this capture, in order, matching the career log exactly: Guts 1* (stat),
Turf 2* (aptitude), Presents from X 1* (unique), then the whites Japanese Derby 1*,
Takarazuka Kinen 1*, Hakodate Racecourse 1*, Homestretch Haste 1*, Passing Pro 2*,
Tactical Tweak 1*, Ignited Spirit PWR 2*, Unity Cup 2*.

## Third capture: the 2026-07-21 star-undercount false block

The scanner read this exact dialog's Medium row as 2-star (the SPARKS screen had correctly
read 3-star seconds earlier) and hard-blocked a finished no-spend career. The frame is the
regression fixture for the star-slot recalibration: the old star columns (855/901/947) sat on
the glyphs' last gold column, and this capture is one of the three frames the corrected
centers (845/891/936) were measured on.

| Fixture | Source | Shows |
|---|---|---|
| keep_confirmation_guts2.png | adb screencap 2026-07-25 01:52 | The keep confirmation of a FAILED Grand Concert career (Taiki Shuttle, C rank): a 3-row set, Guts 2* (stat) / Pace Chaser 1* (aptitude) / Shooting for Victory! 1* (unique). Captured while the live bot sat blocked on it: the Sparks screen had read Guts as 2*, the keep dialog's own read returned 0*, and the chooser refused to confirm, leaving the career unfinalized. A 3-row set shrink-wraps the dialog body, the geometry the undercount favours. |
| keep_confirmation_medium3.png | MuMu-20260721-211251-976.png | "Confirmation" dialog, plain green `Sparks` pill, the 6-row kept set with Medium at three filled stars, Cancel + Confirm |

Byte-identical copy of the maintainer's MuMu screenshot (native RGB, 1080x1920), taken
2026-07-21 21:12 local while the blocked dialog sat on screen. SHA-256
`79b9f985cb7bb7e48d72e7f508dc27e1c969aa0836caa2446b1bb006ac655570`.

The 6 rows, in order, matching the career log exactly: Speed 2* (stat), Medium 3*
(aptitude), Behold Thine Emperor's Divine Might 2* (unique), then the whites Arima Kinen 1*,
Ramp Up 1*, Unity Cup 1*.
