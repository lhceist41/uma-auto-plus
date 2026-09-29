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
