# Legacy Select "Sparks" sub-view fixtures

Two full-resolution (1080x1920, 8-bit RGBA) frames of the reversible **Sparks** view opened from a
populated Legacy Select summary, captured live on 2026-08-20 (MuMu, PL-4a lineage collection). They
carry no account identifiers: only in-game factor names, star counts, and ancestor portraits.

| file | what it shows |
|---|---|
| `legacy_sparks_first.png` | The "1st Legacy" section: the Legacy 1 parent's block. Stat **Power** 1★, aptitude **Pace Chaser** 2★, unique **Dancing in the Leaves** 3★, then eight white race/skill rows (2,1,1,2,1,1,2,1). |
| `legacy_sparks_grandparents.png` | Scrolled into the grandparent blocks. Block 0 (A-rank grandparent): stat **Power** 3★, aptitude **Mile** 3★, unique **Resplendent Red Ace** 2★, six white rows (all 1★). Block 1 (SS-rank grandparent): stat **Stamina** 2★, aptitude **Mile** 2★ (clipped at the list bottom). |

`LegacySparkProbesFixtureTest` pins the band-walk reader, bar palette, star columns, and block
segmentation against these exact pixels. Decoded by `FixturePng` (android.jar has no imageio).
