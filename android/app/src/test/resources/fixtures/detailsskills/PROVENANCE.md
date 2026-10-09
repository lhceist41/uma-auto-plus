# Details Skills-tab glyph fixture provenance

Single skill cells of the Umamusume Details "Skills" tab, cropped to the reader's cell box (`SkillList.readDetailsSkillCell`: x 118-505 or 622-1010, 100 px tall, on 1080-wide frames). Ground truth for the tier glyph templates `details_skill_double_circle`, `details_skill_circle` and `details_skill_x` and their score floor.

| Prefix | Cell shows |
|---|---|
| `double_` | a skill with the ◎ glyph, two of them with the name wrapped to two lines, one from a 1080x2316 phone |
| `circle_` | a skill with the ○ glyph |
| `cross_` | a negative skill with the × glyph |
| `none_` | a skill with no glyph, one on a gold tile |

Sources: in-career Details reads from emulator careers (1080x1920, some from JPEG frame captures, so slightly compressed), the post-career Details screen on the emulator, and one phone capture. The three templates were cut from the post-career Details screen of other careers, so `cross_sapporo_racecourse` is the template's own source cell (no other × sample exists); every ◎ and ○ fixture is an independent sample.

Each crop holds one skill name and its tile only: no trainer name, rank, ID or friend code, so no redaction was needed. Colors are device RGB.
