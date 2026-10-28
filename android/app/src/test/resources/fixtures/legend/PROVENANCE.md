# Legend Race fixture provenance

All fixtures are full 1080x1920 `screencap -p` captures of the MuMu emulator (8-bit RGBA, non-interlaced, device colours
as written, so no red/blue swap) from a Global account: the grid and the race list while the bot ran the Daily Legend Race
step, the Runner Selection during a Legend Race played by hand. They pin the screen read in `DailyLegendRaceScreenFixtureTest`.

| Fixture | Taken | Shows |
|---|---|---|
| legend_grid.png | 2026-10-25 | The Daily Legend Races grid, scrolled one page, "to Star Unlock" strips on most cards, ticket counter 1/1 |
| race_list_one_row.png | 2026-10-25 | El Condor Pasa's race list with its single row (Japan Cup, Hard), under the same "Daily Legend Races" header |
| runner_selection.png | 2026-10-28 | The Legend Race's Runner Selection for Sakura Bakushin O (Hard): its header over the race line, the trainee's aptitudes and the roster |

The frames are kept whole so the test matches each label in the band the bot searches it in: the "Daily Legend Races"
header in the top quarter, Main Rewards in the middle half, the Runner Selection header text at the top and across the
race list's top half. No trainer name, ID or club name is on any screen.
