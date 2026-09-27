# Training selection fixture provenance

Full-resolution (1080x1920) captures that pin the pixel probe in `utils/TrainingSelectionProbe.kt`.
The Grand Concert training fixtures in `grandconcert/` (`training_*.png`) are further positives, and
every other fixture PNG is a negative.

| Fixture | Shows |
|---|---|
| training_selection.png | The Training selection screen of a career in progress: the "Training" header, the five training buttons with Power raised, Back, the Skip pill, Quick and Log. The bot had been stopped here while it analysed training. |
| training_selection_other_training.png | The same screen on another turn, with Guts raised. |
| career_menu_after_back.png | The career's training menu (Rest, Training, Skills) reached by pressing the game's Back once on the Training selection screen, same turn. |
| training_result_cutscene.png | A training result (the "Training" header, the stat and friendship gains, the Skip pill) with no Back button. |
| race_list.png | The Race List: a Back button under a different header. |

## Source and colour

- The first three are `adb screencap` captures from MuMu on 2026-09-27; the title screen showed game
  version 1.35.1 that morning. The
  failure they document: a Start on this screen had the bot take its Skip pill for the launch Quick
  Mode prompt; one Back from a person then returned the career to its training menu.
- `training_result_cutscene.png` is an `adb screencap` capture from MuMu on 2026-09-05; the game
  version was not recorded.
- `race_list.png` is the upstream project's sample image `src/data/imageDetectionSample3.png`, stored
  as RGB there and converted to RGBA here with its colours unchanged.

`adb screencap` writes the device colours as they are, so no red/blue swap was needed. Each file was
re-encoded losslessly; the decoded pixels match the source. Nothing in the game UI was altered.

No personally identifying information is present: the screens show game characters, stats and game
UI, and no player or trainer name.
