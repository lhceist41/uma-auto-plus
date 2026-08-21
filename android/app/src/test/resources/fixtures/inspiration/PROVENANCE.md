# Umamusume Details -> Inspiration tab fixtures

Four full-resolution (1080x1920, 8-bit RGBA) frames of the **Inspiration** panel of the read-only
`Umamusume Details` dialog, captured live on 2026-08-21 (MuMu, PL-R1c calibration). They carry no
account identifiers: only in-game factor names, star counts, and ancestor portraits.

The first two are the same A-rank `[Wild Frontier] Taiki Shuttle` Veteran.

| file | what it shows |
|---|---|
| `inspiration_top.png` | The panel scrolled to the top: the green `Sparks` section header, the Veteran's own five factor rows (stat **Power** 1*, aptitude **Mile** 2*, unique **Shooting for Victory!** 1*, then **Yasuda Kinen** 1*, **Mile Ch.** 1*, **Standard Distance** 1*, **Calm in a Crowd** 2*, **Playtime's Over!** 1*, **URA Finale** 1*), the `Legacy Origin` divider, and the first two rows of ancestor 0. |
| `inspiration_bottom.png` | The panel scrolled to the bottom: the tail of ancestor 0 (**Levelheaded** 1*, **Pace Chaser Savvy** 1*, **Ignited Spirit: Speed +** 2*) and the whole of ancestor 1 (stat **Speed** 2*, aptitude **Pace Chaser** 2*, unique **The Duty of Dignity Calls** 3*, then six more rows). |
| `inspiration_history_boundary.png` | An S-rank `[Red Strife] Gold Ship`, scrolled to the boundary between the factor list and what follows it: a green **Inspiration History** header, "This Umamusume has inspired the Umamusume of 142 trainers.", and then one dated row per borrow. That history is why the panel's scrollbar reports a content height of 10,018 px behind eighteen factor rows, and why the end of the FACTORS - empty space below the last card - is the signal the traversal stops on. |
| `inspiration_two_line_name.png` | An S+ `[Emperor's Path] Symboli Rudolf`, scrolled to the top. Its unique factor is **Behold Thine Emperor's Divine Might**, long enough to wrap onto a second line inside a card of the SAME height. That is the layout the name crop has to cover: a band sized for one line reads back only "Might", which is a wrong name rather than a missing one. |

The Taiki pair is deliberately kept as captured, mid-content gap and all: they were taken with a
single large manual swipe, and the scrollbar model proves three of ancestor 0's rows fall between
them. That makes them the exact regression fixture for gap detection, which is the one merge failure a
naive concatenation would hide. `VeteranInspirationProbesFixtureTest` pins the bar palette, the
two-column star geometry, the scrollbar content model, the band-walk reader, the cross-frame merge,
the end-of-factor-list signal, and that gap.

Decoded by `FixturePng` (android.jar has no imageio).
