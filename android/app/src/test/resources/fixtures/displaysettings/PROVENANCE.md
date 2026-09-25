# Display Settings tab-strip fixture provenance

Top-strip crops of the Veteran roster **Display Settings** dialog, captured on MuMu (1080x1920 portrait,
native RGB) during the protection-probe evidence pass. The full-screen captures live in a local,
git-ignored validation directory outside the tracked tree; only these two minimal crops are promoted here.

Each fixture is the top **1080x240** band of the screen: the green "Display Settings" banner and the
Sort / Filter tab pill (y 176-232). The blurred roster header behind the dialog is the only other
content, so the crops carry **no Veteran, factor, or account content**. Coordinates are the real device
coordinates (top-left origin), so the same absolute sample points read correctly on a full screen and on
these crops.

| Fixture | Shows |
|---|---|
| sort_active.png | Dialog as opened: left "Sort" tab green-selected, right "Filter" tab white-unselected |
| filter_active.png | Same dialog after tapping Filter: right "Filter" tab green-selected, left "Sort" tab white-unselected |

## What these fixtures prove

The tab state is a colour read of the tab background, not of the label. Measured with an independent
decoder at the sample columns the recognizer uses on y=203 (Sort 100/180/400/480, Filter 610/690/910/990):

| Fixture | Sort samples green | Filter samples green | selected RGB | unselected RGB |
|---|---|---|---|---|
| sort_active.png | 4 of 4 | 0 of 4 | (136,209,8) | (245,244,247) |
| filter_active.png | 0 of 4 | 4 of 4 | (136,209,8) | (245,244,247) |

The tab tap targets are the label centres. On filter_active.png the Filter label centre (795,203) is the
white glyph (249,253,243), so a recognizer that samples it rejects a correctly selected Filter tab.

## What they do not prove

They carry no evidence about the dialog body, the checkboxes, or the bottom controls, which the tests
still supply synthetically. They do not show the tab mid-transition; a capture taken before the switch
completes is expected to fail closed.
