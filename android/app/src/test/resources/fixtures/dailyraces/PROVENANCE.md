# Daily Races fixture provenance

Full-resolution (1080x2316) `adb exec-out screencap -p` captures from a Samsung SM-S918B on Android 16,
taken on 2026-10-08 while one Daily Races multi-race (6 tickets, an event doubled them) was played by hand.
The window cutout inset on this phone is 94 px, so `utils/ScreenBands.kt` puts TOP at +94, DIALOG at +198,
MIDDLE at +245 and BOTTOM at +396 from the 1080x1920 coordinates. These frames pin the templates and places
in `DailyRaceProbesFixtureTest`. No MuMu capture of these screens exists yet.

| Fixture | Shows |
|---|---|
| race_tab.png | Race tab with the Daily Program tile |
| daily_program.png | Daily Program, Daily Races tile at 6/6 |
| daily_program_0.png | Daily Program, Daily Races tile at 0/6 "Done for today!" |
| daily_races_6.png | Daily Races race pick, counter 6/6 |
| daily_races_0.png | Daily Races race pick, counter 0/6 |
| difficulty.png | Moonlight Sho difficulty list, all four rows, counter 6/6 |
| race_details.png | Race Details with "Multi-Race: On" |
| runner_selection.png | Runner Selection with Confirm |
| multi_race_popup.png | Multi-Race popup, stepper 6/6, "Race! Consumes 6" |
| race_result.png | Race Results, race 1 with Complete |
| total_rewards.png | Race Results header over Total Rewards with Close |
| daily_sale.png | Daily Sale dialog (Cancel / Shop) |

## Source and colour

`adb screencap` writes the device colours as they are, so no red/blue swap was needed. The files are
8-bit RGBA at the full 1080x2316 size. Only the areas the tests read are kept, the same on every fixture:
the Daily Program label (x 132-502, y 1950-2086), the Daily Races tile label (x 152-482, y 1836-1962),
the dialog header (x 378-702, y 228-354), Complete (x 400-678, y 1906-2044), the Multi-Race pill
(x 380-800, y 1800-1936), the ticket counter (x 880-1070, y 90-180), the popup Race! (x 540-1020,
y 1370-1530) and a difficulty card column (x 990-1010, y 1000-1950). Everything else is opaque black.
No trainer name, ID or club name is in a kept area.

The five new templates (`button/daily_program_tile`, `button/daily_races`, `button/complete`,
`label/race_details_header`, `label/race_results_header`) are tight crops of the same captures.
