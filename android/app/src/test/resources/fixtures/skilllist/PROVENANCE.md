# Skill list confirmation fixture provenance

`skill_list_confirmation_top.png` is the top **1080x360** band of the skill list's Learn "Confirmation" dialog in
a Unity Cup career. It was taken with `adb exec-out screencap -p` from MuMu (1080x1920 portrait) on 2026-09-29. The
band holds the green "Confirmation" header and the first skill row. The small round button at the top centre is
UMA Auto+'s own floating overlay.

It is the negative for the Grand Concert lesson-dialog probe: the lesson Learn dialog shares this header, title and
Cancel/Learn buttons, but shows a technique or song pill where this dialog shows a grey skill row. Coordinates are
real device coordinates (top-left origin), so the probe's absolute sample points read the same here as on a full
screen.

`adb screencap` writes the device colours as they are, so no red/blue swap was needed. The band was cropped with
its pixels unchanged. It shows game UI and a skill name only, with no player, trainer or account content.

## Learn-list title glyph crops

The `circle_`, `double_` and `none_` files are title crops of single Learn-list rows: x 188-749, 85 px tall around the
title line, on 1080x1920 frames. That is the title box `SkillList.getSkillListEntryTitle` cuts, where the glyph sits.
They are ground truth for the `skill_title_double_circle`, `skill_title_circle` and `skill_title_x` templates, the 1/0.95
upscale and the 0.85 floor. The frames are MuMu screencaps from 2026-10-30, at the career-end Learn lists of two
Grand Concert careers on two emulator instances, in device colours, cropped with their pixels unchanged.

| Prefix | Row shows |
|---|---|
| `double_` | a skill with the ◎ glyph, two on Obtained (grey) rows |
| `circle_` | a skill with the ○ glyph, one on an Obtained row |
| `none_` | a skill with no glyph: plain, Obtained, and gold tiles (one Obtained) |

No × row was on screen in these frames. Each crop holds one skill title and its tile only: no trainer name, rank, ID or
friend code.
