# Veteran roster fixtures

Two crops of the read-only `Umamusume Details` dialog, used by `VeteranBadgeClassifierTest` to pin
the styled-field pixel classifiers (rank medal, stat-grade badges, aptitude-grade letters) against
real captured pixels rather than hand-typed strings.

| File | Trainee | Source frame | Notes |
|---|---|---|---|
| `veteran_taiki_details_top.png` | Taiki Shuttle [Wild Frontier] | `validation/parentlab-plr1-roster/02-details-skills-tab.png` | Rank A, rating 10192, stats A+/B/B/B/C |
| `veteran_copano_details_top.png` | Copano Rickey [Eightfold Fortune] | `validation/parentlab-plr1-roster/08-details-chevron-next-entry.png` | Rank A, rating 10381, Speed SS+ (1164), Stamina D (344) - the SS+/D anti-overfit case |

## Rank-tier fixtures (PL-R1b rank calibration, MuMu 2026-08-21)

The live roster carries four overall-rank tiers, not one: A and A+ (orange medal, hue ~18) and S and
S+ (gold medal, hue ~40). These six crops calibrate and validate `classifyRankMedal` across all four.
For each of the three tiers beyond A, `_a` is the frame its template was baked from and `_b` is a
different Veteran of the same tier, so a passing `_b` assertion proves the template generalizes
instead of echoing its own source (the same anti-overfit split as Taiki vs Copano for A).

| File | Trainee | Tier | Rating |
|---|---|---|---|
| `veteran_aplus_a_details_top.png` | Agnes Tachyon [tach-nology] | A+ | 14206 |
| `veteran_aplus_b_details_top.png` | El Condor Pasa [El Numero 1] | A+ | 13698 |
| `veteran_s_a_details_top.png` | Symboli Rudolf [Emperor's Path] | S | 15588 |
| `veteran_s_b_details_top.png` | Gold Ship [Red Strife] | S | 15499 |
| `veteran_splus_a_details_top.png` | Symboli Rudolf [Emperor's Path] | S+ | 17022 |
| `veteran_splus_b_details_top.png` | Mihono Bourbon [CODE: ICING] | S+ | 16885 |

## Rank residual medal crops (PL-R1b margin-aware acceptance, MuMu 2026-08-21)

Nine genuine overall-rank **A** medals from the live full scan `rs-1787321365843-07ccfed7` that the
absolute-floor-only rule failed closed on: their background corners dragged the whole-medal
correlation to 0.74-0.83, below the 0.85 strong floor, even though each still beat the A+ sibling by
>= 0.20. They calibrate and pin the margin fallback in `classifyRankMedalDetailed`. Each file is
exactly the `RANK_MEDAL_BOX` region (128x120 at absolute origin 350,168) as written by
`RosterEvidenceWriter`, so `VeteranBadgeClassifierTest` scores it through a sampler that maps the
absolute medal-box coordinates back onto the crop. The whole-medal NCC each reproduces offline is
noted; the A+ sibling score is in parentheses.

| File | Trainee | best A NCC (A+ sibling) |
|---|---|---|
| `veteran_rank_a_residual_208_medal.png` | Mejiro McQueen | 0.820 (0.583) |
| `veteran_rank_a_residual_219_medal.png` | Gold City | 0.741 (0.543) |
| `veteran_rank_a_residual_221_medal.png` | Sakura Bakushin O | 0.820 (0.583) |
| `veteran_rank_a_residual_222_medal.png` | Mejiro McQueen | 0.820 (0.583) |
| `veteran_rank_a_residual_231_medal.png` | Gold City | 0.741 (0.543) |
| `veteran_rank_a_residual_238_medal.png` | Rice Shower | 0.820 (0.583) |
| `veteran_rank_a_residual_244_medal.png` | Gold City | 0.741 (0.543) |
| `veteran_rank_a_residual_245_medal.png` | Matikanetannhauser | 0.830 (0.599) |
| `veteran_rank_a_residual_248_medal.png` | Gold City | 0.741 (0.543) |

Each fixture is the top rows of the original 1080x1920 MediaProjection capture, re-saved as 8-bit RGBA
non-interlaced PNG so `FixturePng` can decode it. The crop keeps absolute capture coordinates (origin
unchanged), so the classifier geometry constants apply directly. The Taiki/Copano pair keeps the top
820 rows (they also pin the stat-grade and aptitude classifiers); the six rank-tier crops keep the top
600 rows (through the stat row), which is all the rank medal and stat badges need. The Career Info
block is not included; those fields are parsed from OCR strings, not pixels. The full frames stay in
the gitignored `validation/` set.
