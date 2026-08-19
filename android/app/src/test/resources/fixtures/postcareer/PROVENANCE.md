# Post-career reward-overlay fixture provenance

Full-resolution (1080x1920) capture of the benign reward overlay the game injects after a career
completes while a limited-time event is running, on the way back to Home. Ground truth for the
pixel probes in `utils/PostCareerScreenProbes.kt`.

| Fixture | Shows |
|---|---|
| event_points_rewards_summary.png | The event-period "REWARDS" points summary: big teal "REWARDS" title, the running event logo, two full-width lime section-header bars ("Event Points obtained" and "Progress"), the points tally, and the event-story progress row. A full-screen tap-to-advance overlay with no Next/OK/Close/Confirm button. |

## Source and the BGR -> RGB conversion

This frame is the one the navigator gave up on: after a COMPLETE career it failed to recognize
this screen, logged five straight UNKNOWNs, and stopped the queue. The only capture that exists is
the bot's own failure camera image (`nav_failure_unknown_state_20260819_045953.png`, pulled from
the device temp dir). The event ended and the career was recovered by hand before the same frame
could be re-captured with `adb screencap`, so this saveBitmap frame is the only real evidence.

The failure camera (`saveBitmap`) writes OpenCV **BGR** byte order, so its PNGs have red and blue
swapped versus the device colorspace (this is the documented reason the sparks provenance warns
against using raw failure-camera PNGs as color-probe fixtures). This fixture was corrected by
swapping the red and blue channels, which is the exact and only transform between the two orders,
so its per-pixel RGB now matches what `Bitmap.getPixel` returns at runtime. The correctness of the
correction is self-checking: the raw BGR frame fails the probe and only the swapped frame passes,
which is exactly the runtime consistency the fixture test guarantees. Nothing in the visible game
UI was altered.

No personally identifying information is present on this screen: no player name and no trainer
name, only event UI and game characters, so no cropping or redaction was needed.
