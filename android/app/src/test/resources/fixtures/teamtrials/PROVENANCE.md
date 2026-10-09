# Team Trials fixture provenance

Full-resolution (1080x2316) `adb exec-out screencap -p` captures from a Samsung SM-S918B on Android 16,
taken on 2026-10-08 while five Team Trials matches and the other dailies were played by hand. The window
cutout inset on this phone is 94 px, so `utils/ScreenBands.kt` puts TOP at +94, DIALOG at +198, MIDDLE at
+245 and BOTTOM at +396 from the 1080x1920 coordinates. These frames pin the probes in
`TeamTrialsProbesFixtureTest`. No MuMu capture of these screens exists yet.

| Fixture | Shows |
|---|---|
| select_opponent.png | Select Opponent: three opponent cards, RP 5/5 |
| select_opponent_race_again.png | Select Opponent reached through Race Again |
| team_trials_home_rp5.png | Team Trials home, RP 5/5 |
| team_trials_home_rp4.png | Team Trials home, RP 4/5 |
| team_trials_home_rp3.png | Team Trials home, RP 3/5 |
| team_trials_home_rp2.png | Team Trials home, RP 2/5 |
| team_trials_home_rp0.png | Team Trials home, RP 0/5 |
| home_rp0.png | Home, RP 0/5 |
| race_tab_rp5.png | Race tab, RP 5/5 |
| team_preview.png | Team preview with Next |
| items_selected.png | Items Selected dialog (Cancel / Race!) |
| standby.png | Standby lineup with "Quick Mode: ON" and See All Race Results |
| result_splash.png | WIN splash with TAP and the Skip button |
| race_finished.png | RACE FINISHED list with Next |
| winnings.png | WINNINGS chest with Next |

## Source and colour

`adb screencap` writes the device colours as they are, so no red/blue swap was needed. The files are
8-bit RGBA at the full 1080x2316 size. Only the areas the tests read are kept, each with 48 px around it:
the card scan column (x 8-112, y 299-2016), the RP pips (x 589-874, y 146-244) and the Quick Mode pill
(x 362-716, y 1934-2050). Everything else is opaque black.

The opponent name bars and the standby name bar are painted over, so no trainer name, ID or club name
is present.
