# Final Confirmation mode-tab fixture provenance

Top-strip crops of the career-launch **Final Confirmation** screen, both career modes, captured on
MuMu (1080x1920 portrait, native RGB) during the N0 evidence pass. The full-screen captures and the
capture trail live in the git-ignored `validation/n0-final-confirmation-mode/`; only these two minimal
crops are promoted here.

Each fixture is the top **1080x360** band of the screen: the green "Final Confirmation" header and the
two-tab pill strip. The crop stops well above the body, so it carries **no trainee, support-card, or
account content** -- only the two mode tabs. Coordinates are the real device coordinates (top-left
origin), so the same absolute sample points read correctly on a full screen and on these crops.

| Fixture | Shows |
|---|---|
| normal_career.png | Final Confirmation on the LEFT "Normal Career" tab: left pill green-selected, right "Independent Training" pill white-unselected |
| independent_training.png | Same screen on the RIGHT "Independent Training" tab: right pill green-selected, left "Normal Career" pill white-unselected |

## What these fixtures prove

The mode discriminator is a colour read, not a template: the selected tab pill is a solid green fill,
the unselected one near-white. Measured on both fixtures with an independent decoder, at the sample
points `FinalConfirmationTabGeometry` uses (left `(110,322)`, right `(980,322)`, 18px half-box,
`g-max(r,b) > 40`):

| Fixture | left-tab green fraction | right-tab green fraction | selected mean RGB | unselected mean RGB |
|---|---|---|---|---|
| normal_career.png | 1.000 | 0.000 | (137,210,8) | (246,245,249) |
| independent_training.png | 0.000 | 1.000 | (137,210,8) | (246,245,248) |

Complete separation (1.000 vs 0.000), so the `GREEN_FRACTION_MIN = 0.6` threshold sits mid-margin.
The fixture tests pin these fractions and the mode classification against the exact pixels; the
`FinalConfirmationProbes` decoder correctness is cross-pinned by `FixturePng` being an independent
implementation from the one that measured the table above.

## What they do not prove

They carry no evidence about any screen other than the Final Confirmation tab strip. The cross-screen
negative cases in the fixture test sample the existing `grandconcert` full-screen fixtures to show the
probe REFUSES screens that are not this two-tab control. On-device template matching for the
"Start Career!" / "Start!" buttons is not exercised here (JUnit has no OpenCV); the mode gate's safety
does not depend on it -- an unrecognized screen fails closed.
