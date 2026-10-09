# Logged stat-gain matches provenance

`logged_gain_matches.csv` holds the training-screen gain reads logged by `constructIntegerFromMatches` (debug builds) in eight live sessions (six on the emulator, two on a phone), 2026-09-26 to 2026-10-08. Identical reads are merged.

| Column | Meaning |
|---|---|
| `source` | the session's device, and `single-row` (URA Finale) or `two-row` (the other scenarios) by the templates the read used |
| `count` | how many times this exact read was logged |
| `matches` | the template matches as `glyph@x:y`, centre coordinates in the row crop, sorted by x |
| `logged` | the string the reader built from them before this gate: every glyph joined left to right |

11,152 reads in total. 27 of them (all single-row) hold a "7" 15 to 21 px above the other glyphs: a diagonal edge of the trainee's outfit behind the gain, read into "77". In every other read the glyph centres lie within 7 px of each other. Numbers and glyph names only: no trainer name, ID or other identifying data.
