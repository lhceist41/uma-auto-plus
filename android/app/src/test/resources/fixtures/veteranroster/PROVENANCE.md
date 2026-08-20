# Veteran roster fixtures

Two crops of the read-only `Umamusume Details` dialog, used by `VeteranBadgeClassifierTest` to pin
the styled-field pixel classifiers (rank medal, stat-grade badges, aptitude-grade letters) against
real captured pixels rather than hand-typed strings.

| File | Trainee | Source frame | Notes |
|---|---|---|---|
| `veteran_taiki_details_top.png` | Taiki Shuttle [Wild Frontier] | `validation/parentlab-plr1-roster/02-details-skills-tab.png` | Rank A, rating 10192, stats A+/B/B/B/C |
| `veteran_copano_details_top.png` | Copano Rickey [Eightfold Fortune] | `validation/parentlab-plr1-roster/08-details-chevron-next-entry.png` | Rank A, rating 10381, Speed SS+ (1164), Stamina D (344) - the SS+/D anti-overfit case |

Each is the top 820 rows of the original 1080x1920 MediaProjection capture (MuMu, PL-R1 investigation
2026-08-20), re-saved as 8-bit RGBA non-interlaced PNG so `FixturePng` can decode it. The crop keeps
absolute capture coordinates (origin unchanged), so the classifier geometry constants apply directly.
The Career Info block (below row 820) is not included; those fields are parsed from OCR strings, not
pixels. The full frames stay in the gitignored `validation/` set.
