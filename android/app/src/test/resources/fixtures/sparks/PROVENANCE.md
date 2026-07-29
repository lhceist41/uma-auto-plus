# Spark selection fixture provenance

Full-resolution (1080x1920) captures of one complete Spark Reroll flow at a career finish, driven
by hand through the selection so that both pager pages exist. These are the only known captures of
the post-spend selection screens and are the ground truth for every pixel probe, geometry constant,
and OCR region in `utils/SparkScreenProbes.kt`.

| Fixture | Shows |
|---|---|
| sparks_screen.png | SPARKS screen, 10-row original set (9 full rows visible + row 10 clipped), Reroll Sparks + Confirm |
| confirm_reroll_dialog.png | "Confirm Reroll" spend dialog (30 TP, Cancel / Reroll Sparks), dimmed sparks screen behind |
| sparks_rerolled_result.png | "Sparks Rerolled" result, 7-row rerolled set, single Next |
| spark_selection_intro.png | "Spark Selection" intro dialog ("Select which Sparks to keep."), capture caught mid-animation (body text blurred; geometry unaffected) |
| pager_rerolled.png | Spark Selection pager page 1: heading "Rerolled Sparks", left+right chevrons, page dot 1 lit, wide Confirm |
| pager_original.png | Spark Selection pager page 2: heading "Original Sparks", page dot 2 lit, 8 of 10 rows visible |
| confirmation_original.png | "Confirmation" dialog: green title band, green "Original Sparks" set-name pill, all 10 rows, Cancel/Confirm |
| umamusume_details.png | Umamusume Details screen after the kept set was confirmed (post-selection boundary) |
| rating_record.png | "Rating Record Updated" screen (post-selection boundary) |

Archive source: a private local screenshot folder, not committed
(`MuMuSharedFolder/Screenshots`). Files are byte-identical copies; only the names changed.

## Second capture set: the ordinary keep confirmation (2026-07-19)

The nine captures above are all POST-SPEND, so they contain only the two side-named pill
variants. The ordinary keep confirmation that every no-reroll career ends on carries a plain
`Sparks` pill, and its absence from the set above cost a live career a safe stop.

| Fixture | Shows |
|---|---|
| keep_confirmation_plain.png | "Confirmation" dialog with a plain green `Sparks` pill, the complete 11-row kept set, "Keep this set of Sparks?" / "You won't be able to reroll Sparks later.", Cancel + Confirm |

Captured with `adb exec-out screencap -p` (true RGB) at 1080x1920, SHA-256
`3161e67f62517c5059697f8454d20f56f19e83a59ed12eeffa109acd26c846d4`.

Note for anyone adding fixtures later: the bot's own failure camera (`saveBitmap`, used for
`nav_failure_*.png`) writes OpenCV BGR byte order, so its PNGs have red and blue swapped and
are unusable as color-probe fixtures. Always use an `adb screencap` of the same screen.

The 11 rows in this capture, in order, matching the career log exactly: Guts 1* (stat),
Turf 2* (aptitude), Presents from X 1* (unique), then the whites Japanese Derby 1*,
Takarazuka Kinen 1*, Hakodate Racecourse 1*, Homestretch Haste 1*, Passing Pro 2*,
Tactical Tweak 1*, Ignited Spirit PWR 2*, Unity Cup 2*.

## Third capture: the star-undercount false blocks (2026-07-21 and 2026-07-25)

Two frames of the same failure, four days apart: the keep dialog's own star read came back
short of what the SPARKS screen had read seconds earlier, and the chooser refused to confirm
a set it could not verify, leaving a finished career blocked on the dialog. Both are
regression fixtures for the star-slot recalibration: the old star columns (855/901/947) sat
on the glyphs' last gold column, and these are two of the three frames the corrected centers
(845/891/936) were measured on.

| Fixture | Shows |
|---|---|
| keep_confirmation_guts2.png | The keep confirmation of a FAILED Grand Concert career (Taiki Shuttle, C rank): a 3-row set, Guts 2* (stat) / Pace Chaser 1* (aptitude) / Shooting for Victory! 1* (unique). Captured while the live bot sat blocked on it: the Sparks screen had read Guts as 2*, the keep dialog's own read returned 0*, and the chooser refused to confirm, leaving the career unfinalized. A 3-row set shrink-wraps the dialog body, the geometry the undercount favours. |
| keep_confirmation_medium3.png | "Confirmation" dialog, plain green `Sparks` pill, the 6-row kept set with Medium at three filled stars, Cancel + Confirm |

`keep_confirmation_guts2.png` is an `adb exec-out screencap` of the live emulator (native RGB,
1080x1920) taken 2026-07-25. SHA-256
`602e47b0b0af9b41a275c55f588f73f43da9b006c2ca63a4e08e3d1aac08692c`.

`keep_confirmation_medium3.png` is a byte-identical copy of the source emulator screenshot
(native RGB, 1080x1920), taken 2026-07-21 while the blocked dialog sat on screen.
SHA-256 `79b9f985cb7bb7e48d72e7f508dc27e1c969aa0836caa2446b1bb006ac655570`. Its 6 rows, in
order, matching the career log exactly: Speed 2* (stat), Medium 3*
(aptitude), Behold Thine Emperor's Divine Might 2* (unique), then the whites Arima Kinen 1*,
Ramp Up 1*, Unity Cup 1*.
