# UMA Auto+ — Character Preset Reference

Generated from `src/data/characterPresets.ts` on 2026-04-16.

Covers 51 preset entries: 17 characters × 3 scenarios (Trackblazer / Unity Cup / URA Finale).

Skill IDs are resolved from `src/data/skills.json`.

---

## Quick build summary

| Character | Distance | Style | Surface |
|-----------|----------|-------|---------|
| Agnes Tachyon | Medium | Pace Chaser | Turf |
| Air Groove | Medium | Late Surger | Turf |
| Daiwa Scarlet | Mile | Front Runner | Turf |
| El Condor Pasa | Mile | Pace Chaser | Turf |
| Gold City (Autumn Cosmos) | Mile | Late Surger | Turf |
| Gold Ship | Long | End Closer | Turf |
| Grass Wonder | Long | Late Surger | Turf |
| Haru Urara | Sprint | Late Surger | Dirt |
| Hishi Amazon | Medium | End Closer | Turf |
| King Halo | Sprint | Late Surger | Turf |
| Maruzensky (Formula R) | Mile | Front Runner | Turf |
| Matikanefukukitaru | Long | Late Surger | Turf |
| Mayano Top Gun | Long | Pace Chaser | Turf |
| Nice Nature | Medium | Late Surger | Turf |
| Sakura Bakushin O | Sprint | Front Runner | Turf |
| Taiki Shuttle | Mile | Pace Chaser | Turf |
| Vodka | Medium | Late Surger | Turf |

---

## Agnes Tachyon

### Trackblazer

**Build identity**

- Distance: `Medium`
- Style: `Pace Chaser`
- Surface: `Turf`
- Distance override (training): `Medium`

**Stat targets — Medium (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1100 | 800 | 800 | 350 | 600 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: false
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Concentration (id 200431)
2. U=ma2 (id 100321)
3. Speed Star (id 200581)
4. Pace Chaser Corners ◎ (id 201321)
5. Pace Chaser Straightaways ◎ (id 201311)
6. Gourmand (id 201351)
7. Medium Corners ◎ (id 201111)
8. Medium Straightaways ◎ (id 201101)
9. Straightaway Acceleration (id 200372)
10. Professor of Curvature (id 200331)
11. Swinging Maestro (id 200351)
12. Left-Handed ◎ (id 200021)
13. Corner Recovery ○ (id 200352)
14. Straightaway Recovery (id 200382)
15. Mystifying Murmur (id 201161)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Medium, Short, Mile, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Trackblazer scenario overrides**

- Energy threshold: 40
- Consecutive race limit: 3
- Min stat gain for Charm: 30
- Max retries per race: 1
- Whistle forces training: true
- Shop check grades: G1, G2
- Shop check frequency: every 1 race(s)
- Retry races before final at grades: G1
- Irregular training: enabled (min stat gain 20)
- Excluded shop items: Guts Scroll, Guts Manual

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 6 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (6)</summary>

| Event | Picked option |
|-------|---------------|
| Expression of Conviction | Option 1 |
| Tachyon the Spoiled Child | Option 2 |
| At Tachyon's Pace | Option 2 |
| The Strongest Collaborator?! | Option 1 |
| Hamburger Helper! | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 3 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### Unity Cup

**Build identity**

- Distance: `Medium`
- Style: `Pace Chaser`
- Surface: `Turf`
- Distance override (training): `Medium`

**Stat targets — Medium (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1100 | 800 | 800 | 350 | 600 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Concentration (id 200431)
2. U=ma2 (id 100321)
3. Speed Star (id 200581)
4. Pace Chaser Corners ◎ (id 201321)
5. Pace Chaser Straightaways ◎ (id 201311)
6. Gourmand (id 201351)
7. Medium Corners ◎ (id 201111)
8. Medium Straightaways ◎ (id 201101)
9. Straightaway Acceleration (id 200372)
10. Professor of Curvature (id 200331)
11. Swinging Maestro (id 200351)
12. Left-Handed ◎ (id 200021)
13. Corner Recovery ○ (id 200352)
14. Straightaway Recovery (id 200382)
15. Mystifying Murmur (id 201161)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Medium, Short, Mile, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 6 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (6)</summary>

| Event | Picked option |
|-------|---------------|
| Expression of Conviction | Option 1 |
| Tachyon the Spoiled Child | Option 2 |
| At Tachyon's Pace | Option 2 |
| The Strongest Collaborator?! | Option 1 |
| Hamburger Helper! | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 3 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### URA Finale

**Build identity**

- Distance: `Medium`
- Style: `Pace Chaser`
- Surface: `Turf`
- Distance override (training): `Medium`

**Stat targets — Medium (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1100 | 800 | 800 | 350 | 600 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Concentration (id 200431)
2. U=ma2 (id 100321)
3. Speed Star (id 200581)
4. Pace Chaser Corners ◎ (id 201321)
5. Pace Chaser Straightaways ◎ (id 201311)
6. Gourmand (id 201351)
7. Medium Corners ◎ (id 201111)
8. Medium Straightaways ◎ (id 201101)
9. Straightaway Acceleration (id 200372)
10. Professor of Curvature (id 200331)
11. Swinging Maestro (id 200351)
12. Left-Handed ◎ (id 200021)
13. Corner Recovery ○ (id 200352)
14. Straightaway Recovery (id 200382)
15. Mystifying Murmur (id 201161)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Medium, Short, Mile, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 6 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (6)</summary>

| Event | Picked option |
|-------|---------------|
| Expression of Conviction | Option 1 |
| Tachyon the Spoiled Child | Option 2 |
| At Tachyon's Pace | Option 2 |
| The Strongest Collaborator?! | Option 1 |
| Hamburger Helper! | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 3 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

## Air Groove

### Trackblazer

**Build identity**

- Distance: `Medium`
- Style: `Late Surger`
- Surface: `Turf`
- Distance override (training): `Medium`

**Stat targets — Medium (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 800 | 800 | 400 | 700 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: false
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Concentration (id 200431)
2. A Lifelong Dream, A Moment's Flight (id 100711)
3. Speed Star (id 200581)
4. Late Surger Corners ◎ (id 201391)
5. Late Surger Straightaways ◎ (id 201381)
6. Gourmand (id 201351)
7. Medium Corners ◎ (id 201111)
8. Medium Straightaways ◎ (id 201101)
9. Mystifying Murmur (id 201161)
10. Professor of Curvature (id 200331)
11. Swinging Maestro (id 200351)
12. Left-Handed ◎ (id 200021)
13. Corner Recovery ○ (id 200352)
14. Straightaway Recovery (id 200382)
15. Race Planner (id 200571)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Medium, Short, Mile, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Trackblazer scenario overrides**

- Energy threshold: 40
- Consecutive race limit: 3
- Min stat gain for Charm: 30
- Max retries per race: 1
- Whistle forces training: true
- Shop check grades: G1, G2
- Shop check frequency: every 1 race(s)
- Retry races before final at grades: G1
- Irregular training: enabled (min stat gain 20)
- Excluded shop items: Guts Scroll, Guts Manual

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 7 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (7)</summary>

| Event | Picked option |
|-------|---------------|
| The Empress and Mom | Option 1 |
| Seize Her! | Option 2 |
| Take Good Care of Your Tail | Option 2 |
| A Taste of Effort | Option 1 |
| Imprinted Memories | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 3 |
| Flowers for You | Option 2 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### Unity Cup

**Build identity**

- Distance: `Medium`
- Style: `Late Surger`
- Surface: `Turf`
- Distance override (training): `Medium`

**Stat targets — Medium (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 800 | 800 | 400 | 700 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Concentration (id 200431)
2. A Lifelong Dream, A Moment's Flight (id 100711)
3. Speed Star (id 200581)
4. Late Surger Corners ◎ (id 201391)
5. Late Surger Straightaways ◎ (id 201381)
6. Gourmand (id 201351)
7. Medium Corners ◎ (id 201111)
8. Medium Straightaways ◎ (id 201101)
9. Mystifying Murmur (id 201161)
10. Professor of Curvature (id 200331)
11. Swinging Maestro (id 200351)
12. Left-Handed ◎ (id 200021)
13. Corner Recovery ○ (id 200352)
14. Straightaway Recovery (id 200382)
15. Race Planner (id 200571)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Medium, Short, Mile, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 7 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (7)</summary>

| Event | Picked option |
|-------|---------------|
| The Empress and Mom | Option 1 |
| Seize Her! | Option 2 |
| Take Good Care of Your Tail | Option 2 |
| A Taste of Effort | Option 1 |
| Imprinted Memories | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 3 |
| Flowers for You | Option 2 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### URA Finale

**Build identity**

- Distance: `Medium`
- Style: `Late Surger`
- Surface: `Turf`
- Distance override (training): `Medium`

**Stat targets — Medium (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 800 | 800 | 400 | 700 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Concentration (id 200431)
2. A Lifelong Dream, A Moment's Flight (id 100711)
3. Speed Star (id 200581)
4. Late Surger Corners ◎ (id 201391)
5. Late Surger Straightaways ◎ (id 201381)
6. Gourmand (id 201351)
7. Medium Corners ◎ (id 201111)
8. Medium Straightaways ◎ (id 201101)
9. Mystifying Murmur (id 201161)
10. Professor of Curvature (id 200331)
11. Swinging Maestro (id 200351)
12. Left-Handed ◎ (id 200021)
13. Corner Recovery ○ (id 200352)
14. Straightaway Recovery (id 200382)
15. Race Planner (id 200571)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Medium, Short, Mile, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 7 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (7)</summary>

| Event | Picked option |
|-------|---------------|
| The Empress and Mom | Option 1 |
| Seize Her! | Option 2 |
| Take Good Care of Your Tail | Option 2 |
| A Taste of Effort | Option 1 |
| Imprinted Memories | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 3 |
| Flowers for You | Option 2 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

## Daiwa Scarlet

### Trackblazer

**Build identity**

- Distance: `Mile`
- Style: `Front Runner`
- Surface: `Turf`
- Distance override (training): `Mile`

**Stat targets — Mile (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 650 | 1000 | 400 | 800 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: false
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Concentration (id 200431)
2. Escape Artist (id 200541)
3. Unrestrained (id 200551)
4. Taking the Lead (id 200531)
5. Front Runner Corners ◎ (id 201251)
6. Front Runner Straightaways ◎ (id 201241)
7. Mile Corners ◎ (id 201041)
8. Mile Straightaways ◎ (id 201031)
9. Mile Maven (id 200681)
10. Professor of Curvature (id 200331)
11. Left-Handed ◎ (id 200021)
12. Restless (id 201281)
13. Corner Recovery ○ (id 200352)
14. Straightaway Recovery (id 200382)
15. Final Push (id 200552)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Mile, Short, Medium, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Trackblazer scenario overrides**

- Energy threshold: 40
- Consecutive race limit: 3
- Min stat gain for Charm: 30
- Max retries per race: 1
- Whistle forces training: true
- Shop check grades: G1, G2
- Shop check frequency: every 1 race(s)
- Retry races before final at grades: G1
- Irregular training: enabled (min stat gain 20)
- Excluded shop items: Guts Scroll, Guts Manual

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 6 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (6)</summary>

| Event | Picked option |
|-------|---------------|
| The Weight of Racewear | Option 1 |
| Recommended Restaurant | Option 1 |
| Advice from an Older Student | Option 1 |
| Enjoying Number One | Option 1 |
| Can't Lose Sight of Number One! | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 3 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### Unity Cup

**Build identity**

- Distance: `Mile`
- Style: `Front Runner`
- Surface: `Turf`
- Distance override (training): `Mile`

**Stat targets — Mile (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 650 | 1000 | 400 | 800 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Concentration (id 200431)
2. Escape Artist (id 200541)
3. Unrestrained (id 200551)
4. Taking the Lead (id 200531)
5. Front Runner Corners ◎ (id 201251)
6. Front Runner Straightaways ◎ (id 201241)
7. Mile Corners ◎ (id 201041)
8. Mile Straightaways ◎ (id 201031)
9. Mile Maven (id 200681)
10. Professor of Curvature (id 200331)
11. Left-Handed ◎ (id 200021)
12. Restless (id 201281)
13. Corner Recovery ○ (id 200352)
14. Straightaway Recovery (id 200382)
15. Final Push (id 200552)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Mile, Short, Medium, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 6 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (6)</summary>

| Event | Picked option |
|-------|---------------|
| The Weight of Racewear | Option 1 |
| Recommended Restaurant | Option 1 |
| Advice from an Older Student | Option 1 |
| Enjoying Number One | Option 1 |
| Can't Lose Sight of Number One! | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 3 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### URA Finale

**Build identity**

- Distance: `Mile`
- Style: `Front Runner`
- Surface: `Turf`
- Distance override (training): `Mile`

**Stat targets — Mile (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 650 | 1000 | 400 | 800 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Concentration (id 200431)
2. Escape Artist (id 200541)
3. Unrestrained (id 200551)
4. Taking the Lead (id 200531)
5. Front Runner Corners ◎ (id 201251)
6. Front Runner Straightaways ◎ (id 201241)
7. Mile Corners ◎ (id 201041)
8. Mile Straightaways ◎ (id 201031)
9. Mile Maven (id 200681)
10. Professor of Curvature (id 200331)
11. Left-Handed ◎ (id 200021)
12. Restless (id 201281)
13. Corner Recovery ○ (id 200352)
14. Straightaway Recovery (id 200382)
15. Final Push (id 200552)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Mile, Short, Medium, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 6 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (6)</summary>

| Event | Picked option |
|-------|---------------|
| The Weight of Racewear | Option 1 |
| Recommended Restaurant | Option 1 |
| Advice from an Older Student | Option 1 |
| Enjoying Number One | Option 1 |
| Can't Lose Sight of Number One! | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 3 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

## El Condor Pasa

### Trackblazer

**Build identity**

- Distance: `Mile`
- Style: `Pace Chaser`
- Surface: `Turf`
- Distance override (training): `Mile`

**Stat targets — Mile (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 650 | 900 | 400 | 800 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: false
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Concentration (id 200431)
2. A Lifelong Dream, A Moment's Flight (id 100711)
3. Speed Star (id 200581)
4. Pace Chaser Corners ◎ (id 201321)
5. Pace Chaser Straightaways ◎ (id 201311)
6. Mile Corners ◎ (id 201041)
7. Mile Straightaways ◎ (id 201031)
8. Gourmand (id 201351)
9. Mile Maven (id 200681)
10. Professor of Curvature (id 200331)
11. Swinging Maestro (id 200351)
12. Left-Handed ◎ (id 200021)
13. Corner Recovery ○ (id 200352)
14. Straightaway Recovery (id 200382)
15. Changing Gears (id 201051)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Mile, Short, Medium, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Trackblazer scenario overrides**

- Energy threshold: 40
- Consecutive race limit: 3
- Min stat gain for Charm: 30
- Max retries per race: 1
- Whistle forces training: true
- Shop check grades: G1, G2
- Shop check frequency: every 1 race(s)
- Retry races before final at grades: G1
- Irregular training: enabled (min stat gain 20)
- Excluded shop items: Guts Scroll, Guts Manual

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 6 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (6)</summary>

| Event | Picked option |
|-------|---------------|
| Passion-filled Outfit | Option 2 |
| A Personalized Mask | Option 1 |
| Go for the Extra-Large Pizza! | Option 1 |
| Hot and Spicy! | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 3 |
| Determination of the World's Strongest | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### Unity Cup

**Build identity**

- Distance: `Mile`
- Style: `Pace Chaser`
- Surface: `Turf`
- Distance override (training): `Mile`

**Stat targets — Mile (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 650 | 900 | 400 | 800 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Concentration (id 200431)
2. A Lifelong Dream, A Moment's Flight (id 100711)
3. Speed Star (id 200581)
4. Pace Chaser Corners ◎ (id 201321)
5. Pace Chaser Straightaways ◎ (id 201311)
6. Mile Corners ◎ (id 201041)
7. Mile Straightaways ◎ (id 201031)
8. Gourmand (id 201351)
9. Mile Maven (id 200681)
10. Professor of Curvature (id 200331)
11. Swinging Maestro (id 200351)
12. Left-Handed ◎ (id 200021)
13. Corner Recovery ○ (id 200352)
14. Straightaway Recovery (id 200382)
15. Changing Gears (id 201051)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Mile, Short, Medium, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 6 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (6)</summary>

| Event | Picked option |
|-------|---------------|
| Passion-filled Outfit | Option 2 |
| A Personalized Mask | Option 1 |
| Go for the Extra-Large Pizza! | Option 1 |
| Hot and Spicy! | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 3 |
| Determination of the World's Strongest | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### URA Finale

**Build identity**

- Distance: `Mile`
- Style: `Pace Chaser`
- Surface: `Turf`
- Distance override (training): `Mile`

**Stat targets — Mile (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 650 | 900 | 400 | 800 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Concentration (id 200431)
2. A Lifelong Dream, A Moment's Flight (id 100711)
3. Speed Star (id 200581)
4. Pace Chaser Corners ◎ (id 201321)
5. Pace Chaser Straightaways ◎ (id 201311)
6. Mile Corners ◎ (id 201041)
7. Mile Straightaways ◎ (id 201031)
8. Gourmand (id 201351)
9. Mile Maven (id 200681)
10. Professor of Curvature (id 200331)
11. Swinging Maestro (id 200351)
12. Left-Handed ◎ (id 200021)
13. Corner Recovery ○ (id 200352)
14. Straightaway Recovery (id 200382)
15. Changing Gears (id 201051)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Mile, Short, Medium, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 6 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (6)</summary>

| Event | Picked option |
|-------|---------------|
| Passion-filled Outfit | Option 2 |
| A Personalized Mask | Option 1 |
| Go for the Extra-Large Pizza! | Option 1 |
| Hot and Spicy! | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 3 |
| Determination of the World's Strongest | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

## Gold City (Autumn Cosmos)

### Trackblazer

**Build identity**

- Distance: `Mile`
- Style: `Late Surger`
- Surface: `Turf`
- Distance override (training): `Mile`

**Stat targets — Mile (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 600 | 800 | 400 | 550 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: false
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Concentration (id 200431)
2. A Lifelong Dream, A Moment's Flight (id 100711)
3. Speed Star (id 200581)
4. Late Surger Corners ◎ (id 201391)
5. Late Surger Straightaways ◎ (id 201381)
6. Mile Corners ◎ (id 201041)
7. Mile Straightaways ◎ (id 201031)
8. Mile Maven (id 200681)
9. Gourmand (id 201351)
10. Professor of Curvature (id 200331)
11. Swinging Maestro (id 200351)
12. Left-Handed ◎ (id 200021)
13. Corner Recovery ○ (id 200352)
14. Straightaway Recovery (id 200382)
15. Changing Gears (id 201051)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Mile, Short, Medium, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Trackblazer scenario overrides**

- Energy threshold: 40
- Consecutive race limit: 3
- Min stat gain for Charm: 30
- Max retries per race: 1
- Whistle forces training: true
- Shop check grades: G1, G2
- Shop check frequency: every 1 race(s)
- Retry races before final at grades: G1
- Irregular training: enabled (min stat gain 20)
- Excluded shop items: Guts Scroll, Guts Manual

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 5 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (5)</summary>

| Event | Picked option |
|-------|---------------|
| A City Girl's Mood ♪ | Option 1 |
| A Quiet Talk Before the Show | Option 1 |
| A Delicious Trap? | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 3 |
| Client's Orders | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### Unity Cup

**Build identity**

- Distance: `Mile`
- Style: `Late Surger`
- Surface: `Turf`
- Distance override (training): `Mile`

**Stat targets — Mile (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 600 | 800 | 400 | 550 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Concentration (id 200431)
2. A Lifelong Dream, A Moment's Flight (id 100711)
3. Speed Star (id 200581)
4. Late Surger Corners ◎ (id 201391)
5. Late Surger Straightaways ◎ (id 201381)
6. Mile Corners ◎ (id 201041)
7. Mile Straightaways ◎ (id 201031)
8. Mile Maven (id 200681)
9. Gourmand (id 201351)
10. Professor of Curvature (id 200331)
11. Swinging Maestro (id 200351)
12. Left-Handed ◎ (id 200021)
13. Corner Recovery ○ (id 200352)
14. Straightaway Recovery (id 200382)
15. Changing Gears (id 201051)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Mile, Short, Medium, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 5 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (5)</summary>

| Event | Picked option |
|-------|---------------|
| A City Girl's Mood ♪ | Option 1 |
| A Quiet Talk Before the Show | Option 1 |
| A Delicious Trap? | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 3 |
| Client's Orders | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### URA Finale

**Build identity**

- Distance: `Mile`
- Style: `Late Surger`
- Surface: `Turf`
- Distance override (training): `Mile`

**Stat targets — Mile (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1100 | 600 | 800 | 350 | 550 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Concentration (id 200431)
2. A Lifelong Dream, A Moment's Flight (id 100711)
3. Speed Star (id 200581)
4. Late Surger Corners ◎ (id 201391)
5. Late Surger Straightaways ◎ (id 201381)
6. Mile Corners ◎ (id 201041)
7. Mile Straightaways ◎ (id 201031)
8. Mile Maven (id 200681)
9. Gourmand (id 201351)
10. Professor of Curvature (id 200331)
11. Swinging Maestro (id 200351)
12. Left-Handed ◎ (id 200021)
13. Corner Recovery ○ (id 200352)
14. Straightaway Recovery (id 200382)
15. Changing Gears (id 201051)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Mile, Short, Medium, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 5 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (5)</summary>

| Event | Picked option |
|-------|---------------|
| A City Girl's Mood ♪ | Option 1 |
| A Quiet Talk Before the Show | Option 1 |
| A Delicious Trap? | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 3 |
| Client's Orders | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

## Gold Ship

### Trackblazer

**Build identity**

- Distance: `Long`
- Style: `End Closer`
- Surface: `Turf`
- Distance override (training): `Long`

**Stat targets — Long (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 1000 | 1000 | 500 | 600 |

**Training**

- Stat priority: Speed > Stamina > Power > Wit > Guts
- Spark stat targets (3★ focus): Speed, Stamina, Guts
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: false
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Anchors Aweigh! (id 100071)
2. Lie in Wait (id 201691)
3. Blast Forward (id 201173)
4. Long Straightaways ◎ (id 201171)
5. Long Corners ◎ (id 201181)
6. Swinging Maestro (id 200351)
7. Professor of Curvature (id 200331)
8. In Body and Mind (id 200511)
9. Be Still (id 201692)
10. Overwhelming Pressure (id 201211)
11. Stamina Siphon (id 201221)
12. Passing Pro (id 201202)
13. VIP Pass (id 201201)
14. Homestretch Haste (id 200512)
15. Corner Recovery ○ (id 200352)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Long, Short, Mile, Medium
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Trackblazer scenario overrides**

- Energy threshold: 40
- Consecutive race limit: 3
- Min stat gain for Charm: 30
- Max retries per race: 1
- Whistle forces training: true
- Shop check grades: G1, G2
- Shop check frequency: every 1 race(s)
- Retry races before final at grades: G1
- Irregular training: enabled (min stat gain 20)
- Excluded shop items: Wit Scroll, Wit Manual

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 17 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (17)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| The Red of the Protagonist! | Option 1 |
| A Date, Golshi Style | Option 1 |
| A Sudden Episode from Golshi's Past! | Option 2 |
| Pair Discount Repeat Offender | Option 2 |
| Which Did You Lose? | Option 2 |
| My Part-Time Job Is... Crazy? | Option 1 |
| The Day After, Voices Hoarse | Option 1 |
| This One's For Keeps! | Option 1 |
| Killer Appetite! | Option 1 |
| Legend of the Left Pinky | Option 1 |
| Hello From About 1.5 Billion Years Ago | Option 2 |
| A Lovely Place | Option 1 |
| Nighttime Park Visit | Option 2 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 2 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### Unity Cup

**Build identity**

- Distance: `Long`
- Style: `End Closer`
- Surface: `Turf`
- Distance override (training): `Long`

**Stat targets — Long (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 1000 | 1000 | 500 | 600 |

**Training**

- Stat priority: Speed > Stamina > Power > Wit > Guts
- Spark stat targets (3★ focus): Speed, Stamina, Guts
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Anchors Aweigh! (id 100071)
2. Lie in Wait (id 201691)
3. Blast Forward (id 201173)
4. Long Straightaways ◎ (id 201171)
5. Long Corners ◎ (id 201181)
6. Swinging Maestro (id 200351)
7. Professor of Curvature (id 200331)
8. In Body and Mind (id 200511)
9. Be Still (id 201692)
10. Overwhelming Pressure (id 201211)
11. Stamina Siphon (id 201221)
12. Passing Pro (id 201202)
13. VIP Pass (id 201201)
14. Homestretch Haste (id 200512)
15. Corner Recovery ○ (id 200352)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Long, Short, Mile, Medium
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 17 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (17)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| The Red of the Protagonist! | Option 1 |
| A Date, Golshi Style | Option 1 |
| A Sudden Episode from Golshi's Past! | Option 2 |
| Pair Discount Repeat Offender | Option 2 |
| Which Did You Lose? | Option 2 |
| My Part-Time Job Is... Crazy? | Option 1 |
| The Day After, Voices Hoarse | Option 1 |
| This One's For Keeps! | Option 1 |
| Killer Appetite! | Option 1 |
| Legend of the Left Pinky | Option 1 |
| Hello From About 1.5 Billion Years Ago | Option 2 |
| A Lovely Place | Option 1 |
| Nighttime Park Visit | Option 2 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 2 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### URA Finale

**Build identity**

- Distance: `Long`
- Style: `End Closer`
- Surface: `Turf`
- Distance override (training): `Long`

**Stat targets — Long (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 1000 | 1000 | 500 | 600 |

**Training**

- Stat priority: Speed > Stamina > Power > Wit > Guts
- Spark stat targets (3★ focus): Speed, Stamina, Guts
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Anchors Aweigh! (id 100071)
2. Lie in Wait (id 201691)
3. Blast Forward (id 201173)
4. Long Straightaways ◎ (id 201171)
5. Long Corners ◎ (id 201181)
6. Swinging Maestro (id 200351)
7. Professor of Curvature (id 200331)
8. In Body and Mind (id 200511)
9. Be Still (id 201692)
10. Overwhelming Pressure (id 201211)
11. Stamina Siphon (id 201221)
12. Passing Pro (id 201202)
13. VIP Pass (id 201201)
14. Homestretch Haste (id 200512)
15. Corner Recovery ○ (id 200352)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Long, Short, Mile, Medium
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 17 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (17)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| The Red of the Protagonist! | Option 1 |
| A Date, Golshi Style | Option 1 |
| A Sudden Episode from Golshi's Past! | Option 2 |
| Pair Discount Repeat Offender | Option 2 |
| Which Did You Lose? | Option 2 |
| My Part-Time Job Is... Crazy? | Option 1 |
| The Day After, Voices Hoarse | Option 1 |
| This One's For Keeps! | Option 1 |
| Killer Appetite! | Option 1 |
| Legend of the Left Pinky | Option 1 |
| Hello From About 1.5 Billion Years Ago | Option 2 |
| A Lovely Place | Option 1 |
| Nighttime Park Visit | Option 2 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 2 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

## Grass Wonder

### Trackblazer

**Build identity**

- Distance: `Long`
- Style: `Late Surger`
- Surface: `Turf`
- Distance override (training): `Long`

**Stat targets — Long (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 900 | 800 | 400 | 600 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: false
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (14 skills, in priority order)**

1. Fast & Furious (id 200591)
2. On Your Left! (id 200601)
3. Rising Dragon (id 200611)
4. Lie in Wait (id 201691)
5. All-Seeing Eyes (id 201441)
6. Long Straightaways ◎ (id 201171)
7. Long Corners ◎ (id 201181)
8. Late Surger Straightaways ◎ (id 201381)
9. Late Surger Corners ◎ (id 201391)
10. Mystifying Murmur (id 201161)
11. Professor of Curvature (id 200331)
12. Homestretch Haste (id 200512)
13. Be Still (id 201692)
14. Sharp Gaze (id 201442)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Long, Medium, Short, Mile
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Trackblazer scenario overrides**

- Energy threshold: 40
- Consecutive race limit: 3
- Min stat gain for Charm: 30
- Max retries per race: 1
- Whistle forces training: true
- Shop check grades: G1, G2
- Shop check frequency: every 1 race(s)
- Retry races before final at grades: G1
- Irregular training: enabled (min stat gain 20)
- Excluded shop items: _(none)_

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 18 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (18)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| A Warrior's Spirit | Option 1 |
| Whimsical Encounter | Option 2 |
| Everlasting Game | Option 1 |
| Errands Have Perks | Option 1 |
| Beauteaful | Option 1 |
| Tracen Karuta Queen | Option 2 |
| Together for Tea | Option 1 |
| Yamato Nadeshiko | Option 2 |
| Childhoods Apart | Option 2 |
| Childhood Dream | Option 2 |
| Flower Vase | Option 2 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 2 |
| Hidden Meaning | Option 2 |
| Principles | Option 1 |
| Hate to Lose | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### Unity Cup

**Build identity**

- Distance: `Long`
- Style: `Late Surger`
- Surface: `Turf`
- Distance override (training): `Long`

**Stat targets — Long (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 900 | 800 | 400 | 600 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (14 skills, in priority order)**

1. Fast & Furious (id 200591)
2. On Your Left! (id 200601)
3. Rising Dragon (id 200611)
4. Lie in Wait (id 201691)
5. All-Seeing Eyes (id 201441)
6. Long Straightaways ◎ (id 201171)
7. Long Corners ◎ (id 201181)
8. Late Surger Straightaways ◎ (id 201381)
9. Late Surger Corners ◎ (id 201391)
10. Mystifying Murmur (id 201161)
11. Professor of Curvature (id 200331)
12. Homestretch Haste (id 200512)
13. Be Still (id 201692)
14. Sharp Gaze (id 201442)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Long, Medium, Short, Mile
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 18 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (18)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| A Warrior's Spirit | Option 1 |
| Whimsical Encounter | Option 2 |
| Everlasting Game | Option 1 |
| Errands Have Perks | Option 1 |
| Beauteaful | Option 1 |
| Tracen Karuta Queen | Option 2 |
| Together for Tea | Option 1 |
| Yamato Nadeshiko | Option 2 |
| Childhoods Apart | Option 2 |
| Childhood Dream | Option 2 |
| Flower Vase | Option 2 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 2 |
| Hidden Meaning | Option 2 |
| Principles | Option 1 |
| Hate to Lose | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### URA Finale

**Build identity**

- Distance: `Long`
- Style: `Late Surger`
- Surface: `Turf`
- Distance override (training): `Long`

**Stat targets — Long (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 900 | 800 | 400 | 600 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (14 skills, in priority order)**

1. Fast & Furious (id 200591)
2. On Your Left! (id 200601)
3. Rising Dragon (id 200611)
4. Lie in Wait (id 201691)
5. All-Seeing Eyes (id 201441)
6. Long Straightaways ◎ (id 201171)
7. Long Corners ◎ (id 201181)
8. Late Surger Straightaways ◎ (id 201381)
9. Late Surger Corners ◎ (id 201391)
10. Mystifying Murmur (id 201161)
11. Professor of Curvature (id 200331)
12. Homestretch Haste (id 200512)
13. Be Still (id 201692)
14. Sharp Gaze (id 201442)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Long, Medium, Short, Mile
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 18 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (18)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| A Warrior's Spirit | Option 1 |
| Whimsical Encounter | Option 2 |
| Everlasting Game | Option 1 |
| Errands Have Perks | Option 1 |
| Beauteaful | Option 1 |
| Tracen Karuta Queen | Option 2 |
| Together for Tea | Option 1 |
| Yamato Nadeshiko | Option 2 |
| Childhoods Apart | Option 2 |
| Childhood Dream | Option 2 |
| Flower Vase | Option 2 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 2 |
| Hidden Meaning | Option 2 |
| Principles | Option 1 |
| Hate to Lose | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

## Haru Urara

### Trackblazer

**Build identity**

- Distance: `Sprint`
- Style: `Late Surger`
- Surface: `Dirt`
- Distance override (training): `Sprint`

**Stat targets — Sprint (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1100 | 400 | 800 | 500 | 500 |

**Training**

- Stat priority: Speed > Power > Stamina > Guts > Wit
- Spark stat targets (3★ focus): Speed, Power
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: false
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Lead the Charge! (id 201681)
2. Trending in the Charts! (id 201671)
3. Adored by All (id 201011)
4. In High Spirits (id 202041)
5. Turbo Sprint (id 200651)
6. Sprint Straightaways ◎ (id 200961)
7. Sprint Corners ◎ (id 200971)
8. Late Surger Straightaways ◎ (id 201381)
9. Late Surger Corners ◎ (id 201391)
10. Plan X (id 200991)
11. Blinding Flash (id 200671)
12. Perfect Prep! (id 201001)
13. Familiar Ground (id 202002)
14. Forward, March! (id 201682)
15. Countermeasure (id 200992)

**Racing**

- Preferred terrain: Dirt
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Short, Mile, Medium, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Trackblazer scenario overrides**

- Energy threshold: 40
- Consecutive race limit: 3
- Min stat gain for Charm: 30
- Max retries per race: 1
- Whistle forces training: true
- Shop check grades: G1, G2
- Shop check frequency: every 1 race(s)
- Retry races before final at grades: G1
- Irregular training: enabled (min stat gain 20)
- Excluded shop items: Stamina Scroll, Stamina Manual, Wit Scroll, Wit Manual

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 14 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (14)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| Arm-Wrestling Contest | Option 2 |
| Looking for Something Important | Option 2 |
| Sand Training! | Option 1 |
| The Final Boss... Spe! | Option 1 |
| A Little Detour! | Option 1 |
| Parks Are Fun! | Option 1 |
| So Cool! | Option 1 |
| Forgot to Eat! | Option 2 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 2 |
| The Racewear I Love! | Option 1 |
| Pair Interview! | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### Unity Cup

**Build identity**

- Distance: `Sprint`
- Style: `Late Surger`
- Surface: `Dirt`
- Distance override (training): `Sprint`

**Stat targets — Sprint (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1100 | 400 | 800 | 500 | 500 |

**Training**

- Stat priority: Speed > Power > Stamina > Guts > Wit
- Spark stat targets (3★ focus): Speed, Power
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Lead the Charge! (id 201681)
2. Trending in the Charts! (id 201671)
3. Adored by All (id 201011)
4. In High Spirits (id 202041)
5. Turbo Sprint (id 200651)
6. Sprint Straightaways ◎ (id 200961)
7. Sprint Corners ◎ (id 200971)
8. Late Surger Straightaways ◎ (id 201381)
9. Late Surger Corners ◎ (id 201391)
10. Plan X (id 200991)
11. Blinding Flash (id 200671)
12. Perfect Prep! (id 201001)
13. Familiar Ground (id 202002)
14. Forward, March! (id 201682)
15. Countermeasure (id 200992)

**Racing**

- Preferred terrain: Dirt
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Short, Mile, Medium, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 14 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (14)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| Arm-Wrestling Contest | Option 2 |
| Looking for Something Important | Option 2 |
| Sand Training! | Option 1 |
| The Final Boss... Spe! | Option 1 |
| A Little Detour! | Option 1 |
| Parks Are Fun! | Option 1 |
| So Cool! | Option 1 |
| Forgot to Eat! | Option 2 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 2 |
| The Racewear I Love! | Option 1 |
| Pair Interview! | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### URA Finale

**Build identity**

- Distance: `Sprint`
- Style: `Late Surger`
- Surface: `Dirt`
- Distance override (training): `Sprint`

**Stat targets — Sprint (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1100 | 400 | 800 | 500 | 500 |

**Training**

- Stat priority: Speed > Power > Stamina > Guts > Wit
- Spark stat targets (3★ focus): Speed, Power
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Lead the Charge! (id 201681)
2. Trending in the Charts! (id 201671)
3. Adored by All (id 201011)
4. In High Spirits (id 202041)
5. Turbo Sprint (id 200651)
6. Sprint Straightaways ◎ (id 200961)
7. Sprint Corners ◎ (id 200971)
8. Late Surger Straightaways ◎ (id 201381)
9. Late Surger Corners ◎ (id 201391)
10. Plan X (id 200991)
11. Blinding Flash (id 200671)
12. Perfect Prep! (id 201001)
13. Familiar Ground (id 202002)
14. Forward, March! (id 201682)
15. Countermeasure (id 200992)

**Racing**

- Preferred terrain: Dirt
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Short, Mile, Medium, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 14 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (14)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| Arm-Wrestling Contest | Option 2 |
| Looking for Something Important | Option 2 |
| Sand Training! | Option 1 |
| The Final Boss... Spe! | Option 1 |
| A Little Detour! | Option 1 |
| Parks Are Fun! | Option 1 |
| So Cool! | Option 1 |
| Forgot to Eat! | Option 2 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 2 |
| The Racewear I Love! | Option 1 |
| Pair Interview! | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

## Hishi Amazon

### Trackblazer

**Build identity**

- Distance: `Medium`
- Style: `End Closer`
- Surface: `Turf`
- Distance override (training): `Medium`

**Stat targets — Medium (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 700 | 900 | 400 | 600 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: false
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Encroaching Shadow (id 200641)
2. Sturm und Drang (id 200631)
3. Daring Strike (id 202021)
4. Sleeping Lion (id 200621)
5. Go-Home Specialist (id 201481)
6. End Closer Straightaways ◎ (id 201451)
7. End Closer Corners ◎ (id 201461)
8. Medium Straightaways ◎ (id 201101)
9. Medium Corners ◎ (id 201111)
10. Professor of Curvature (id 200331)
11. Mystifying Murmur (id 201161)
12. Homestretch Haste (id 200512)
13. Straightaway Spurt (id 200642)
14. Slipstream (id 201651)
15. Corner Recovery ○ (id 200352)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Medium, Short, Mile, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Trackblazer scenario overrides**

- Energy threshold: 40
- Consecutive race limit: 3
- Min stat gain for Charm: 30
- Max retries per race: 1
- Whistle forces training: true
- Shop check grades: G1, G2
- Shop check frequency: every 1 race(s)
- Retry races before final at grades: G1
- Irregular training: enabled (min stat gain 20)
- Excluded shop items: Wit Scroll, Wit Manual

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 15 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (15)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| Another Level | Option 1 |
| One-on-One! Gangster! Racewear! | Option 1 |
| Friend or Rival? | Option 1 |
| Hishiama's Dorm-Leader Breakfast | Option 1 |
| Hishiama's Needlework | Option 1 |
| Hishiama's Foraging | Option 1 |
| The Magic of Sweets? | Option 1 |
| Blazing Memories | Option 1 |
| Cool and Fiery Sisters | Option 1 |
| Hishiama's Special View | Option 2 |
| Hishiama and the Arts | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 2 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### Unity Cup

**Build identity**

- Distance: `Medium`
- Style: `End Closer`
- Surface: `Turf`
- Distance override (training): `Medium`

**Stat targets — Medium (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 700 | 900 | 400 | 600 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Encroaching Shadow (id 200641)
2. Sturm und Drang (id 200631)
3. Daring Strike (id 202021)
4. Sleeping Lion (id 200621)
5. Go-Home Specialist (id 201481)
6. End Closer Straightaways ◎ (id 201451)
7. End Closer Corners ◎ (id 201461)
8. Medium Straightaways ◎ (id 201101)
9. Medium Corners ◎ (id 201111)
10. Professor of Curvature (id 200331)
11. Mystifying Murmur (id 201161)
12. Homestretch Haste (id 200512)
13. Straightaway Spurt (id 200642)
14. Slipstream (id 201651)
15. Corner Recovery ○ (id 200352)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Medium, Short, Mile, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 15 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (15)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| Another Level | Option 1 |
| One-on-One! Gangster! Racewear! | Option 1 |
| Friend or Rival? | Option 1 |
| Hishiama's Dorm-Leader Breakfast | Option 1 |
| Hishiama's Needlework | Option 1 |
| Hishiama's Foraging | Option 1 |
| The Magic of Sweets? | Option 1 |
| Blazing Memories | Option 1 |
| Cool and Fiery Sisters | Option 1 |
| Hishiama's Special View | Option 2 |
| Hishiama and the Arts | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 2 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### URA Finale

**Build identity**

- Distance: `Medium`
- Style: `End Closer`
- Surface: `Turf`
- Distance override (training): `Medium`

**Stat targets — Medium (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 700 | 900 | 400 | 600 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Encroaching Shadow (id 200641)
2. Sturm und Drang (id 200631)
3. Daring Strike (id 202021)
4. Sleeping Lion (id 200621)
5. Go-Home Specialist (id 201481)
6. End Closer Straightaways ◎ (id 201451)
7. End Closer Corners ◎ (id 201461)
8. Medium Straightaways ◎ (id 201101)
9. Medium Corners ◎ (id 201111)
10. Professor of Curvature (id 200331)
11. Mystifying Murmur (id 201161)
12. Homestretch Haste (id 200512)
13. Straightaway Spurt (id 200642)
14. Slipstream (id 201651)
15. Corner Recovery ○ (id 200352)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Medium, Short, Mile, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 15 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (15)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| Another Level | Option 1 |
| One-on-One! Gangster! Racewear! | Option 1 |
| Friend or Rival? | Option 1 |
| Hishiama's Dorm-Leader Breakfast | Option 1 |
| Hishiama's Needlework | Option 1 |
| Hishiama's Foraging | Option 1 |
| The Magic of Sweets? | Option 1 |
| Blazing Memories | Option 1 |
| Cool and Fiery Sisters | Option 1 |
| Hishiama's Special View | Option 2 |
| Hishiama and the Arts | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 2 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

## King Halo

### Trackblazer

**Build identity**

- Distance: `Sprint`
- Style: `Late Surger`
- Surface: `Turf`
- Distance override (training): `Sprint`

**Stat targets — Sprint (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 400 | 900 | 400 | 600 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: false
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (14 skills, in priority order)**

1. Fast & Furious (id 200591)
2. On Your Left! (id 200601)
3. Rising Dragon (id 200611)
4. Lie in Wait (id 201691)
5. All-Seeing Eyes (id 201441)
6. Sprint Straightaways ◎ (id 200961)
7. Sprint Corners ◎ (id 200971)
8. Late Surger Straightaways ◎ (id 201381)
9. Late Surger Corners ◎ (id 201391)
10. Changing Gears (id 201051)
11. Furious Feat (id 200701)
12. Professor of Curvature (id 200331)
13. Homestretch Haste (id 200512)
14. Be Still (id 201692)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Short, Mile, Medium, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Trackblazer scenario overrides**

- Energy threshold: 40
- Consecutive race limit: 3
- Min stat gain for Charm: 30
- Max retries per race: 1
- Whistle forces training: true
- Shop check grades: G1, G2
- Shop check frequency: every 1 race(s)
- Retry races before final at grades: G1
- Irregular training: enabled (min stat gain 20)
- Excluded shop items: _(none)_

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 17 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (17)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| The Outfit That Suits Me Most | Option 2 |
| Running Isn't Everything | Option 2 |
| Manners Are Common Sense | Option 1 |
| Movies Are Full of Learning Opportunities | Option 1 |
| The King Knows No Exhaustion | Option 1 |
| First-Rate in Studies Too | Option 1 |
| After-School Soda | Option 2 |
| Three Heads Are Better than One | Option 1 |
| Sweet Tooth Temptation | Option 1 |
| First-Rate Spot | Option 2 |
| First-Rate Harvest | Option 2 |
| Crowds Are No Problem | Option 2 |
| Breaking Curfew is Second-Rate | Option 2 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 2 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### Unity Cup

**Build identity**

- Distance: `Sprint`
- Style: `Late Surger`
- Surface: `Turf`
- Distance override (training): `Sprint`

**Stat targets — Sprint (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 400 | 900 | 400 | 600 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (14 skills, in priority order)**

1. Fast & Furious (id 200591)
2. On Your Left! (id 200601)
3. Rising Dragon (id 200611)
4. Lie in Wait (id 201691)
5. All-Seeing Eyes (id 201441)
6. Sprint Straightaways ◎ (id 200961)
7. Sprint Corners ◎ (id 200971)
8. Late Surger Straightaways ◎ (id 201381)
9. Late Surger Corners ◎ (id 201391)
10. Changing Gears (id 201051)
11. Furious Feat (id 200701)
12. Professor of Curvature (id 200331)
13. Homestretch Haste (id 200512)
14. Be Still (id 201692)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Short, Mile, Medium, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 17 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (17)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| The Outfit That Suits Me Most | Option 2 |
| Running Isn't Everything | Option 2 |
| Manners Are Common Sense | Option 1 |
| Movies Are Full of Learning Opportunities | Option 1 |
| The King Knows No Exhaustion | Option 1 |
| First-Rate in Studies Too | Option 1 |
| After-School Soda | Option 2 |
| Three Heads Are Better than One | Option 1 |
| Sweet Tooth Temptation | Option 1 |
| First-Rate Spot | Option 2 |
| First-Rate Harvest | Option 2 |
| Crowds Are No Problem | Option 2 |
| Breaking Curfew is Second-Rate | Option 2 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 2 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### URA Finale

**Build identity**

- Distance: `Sprint`
- Style: `Late Surger`
- Surface: `Turf`
- Distance override (training): `Sprint`

**Stat targets — Sprint (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 400 | 900 | 400 | 600 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (14 skills, in priority order)**

1. Fast & Furious (id 200591)
2. On Your Left! (id 200601)
3. Rising Dragon (id 200611)
4. Lie in Wait (id 201691)
5. All-Seeing Eyes (id 201441)
6. Sprint Straightaways ◎ (id 200961)
7. Sprint Corners ◎ (id 200971)
8. Late Surger Straightaways ◎ (id 201381)
9. Late Surger Corners ◎ (id 201391)
10. Changing Gears (id 201051)
11. Furious Feat (id 200701)
12. Professor of Curvature (id 200331)
13. Homestretch Haste (id 200512)
14. Be Still (id 201692)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Short, Mile, Medium, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 17 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (17)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| The Outfit That Suits Me Most | Option 2 |
| Running Isn't Everything | Option 2 |
| Manners Are Common Sense | Option 1 |
| Movies Are Full of Learning Opportunities | Option 1 |
| The King Knows No Exhaustion | Option 1 |
| First-Rate in Studies Too | Option 1 |
| After-School Soda | Option 2 |
| Three Heads Are Better than One | Option 1 |
| Sweet Tooth Temptation | Option 1 |
| First-Rate Spot | Option 2 |
| First-Rate Harvest | Option 2 |
| Crowds Are No Problem | Option 2 |
| Breaking Curfew is Second-Rate | Option 2 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 2 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

## Maruzensky (Formula R)

### Trackblazer

**Build identity**

- Distance: `Mile`
- Style: `Front Runner`
- Surface: `Turf`
- Distance override (training): `Mile`

**Stat targets — Mile (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 600 | 800 | 500 | 1000 |

**Training**

- Stat priority: Speed > Power > Wit > Stamina > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: false
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (14 skills, in priority order)**

1. Escape Artist (id 200541)
2. Professor of Curvature (id 200331)
3. Rushing Gale! (id 200371)
4. Unrestrained (id 200551)
5. Mile Maven (id 200681)
6. Mile Corners ◎ (id 201041)
7. Mile Straightaways ◎ (id 201031)
8. Front Runner Corners ◎ (id 201251)
9. Front Runner Straightaways ◎ (id 201241)
10. Changing Gears (id 201051)
11. Restless (id 201281)
12. Front Runner Savvy ◎ (id 201521)
13. Concentration (id 200431)
14. Taking the Lead (id 200531)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Mile, Short, Medium, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Trackblazer scenario overrides**

- Energy threshold: 40
- Consecutive race limit: 3
- Min stat gain for Charm: 30
- Max retries per race: 1
- Whistle forces training: true
- Shop check grades: G1, G2
- Shop check frequency: every 1 race(s)
- Retry races before final at grades: G1
- Irregular training: enabled (min stat gain 20)
- Excluded shop items: Guts Scroll, Guts Manual

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 21 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (21)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| The Maruzen I Admire | Option 1 |
| Welcome to Bubblyland | Option 2 |
| Maruzensky's Treasure | Option 1 |
| Hot Rod | Option 1 |
| Let's Play ♪ | Option 1 |
| A Lady's Style ☆ | Option 1 |
| Let's Cook! | Option 1 |
| The Road to a Rad Victory! | Option 1 |
| Down to Dance! | Option 1 |
| Nostalgia Fever ☆ | Option 1 |
| The Secret to Supporting Each Other | Option 1 |
| Even Role Models Get Lonely | Option 2 |
| Meeting New People Is Trendy ☆ | Option 2 |
| The Fun Never Stops ♪ | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 1 |
| Trendsetter | Option 1 |
| Sewing Star | Option 2 |
| My Favorite Things | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### Unity Cup

**Build identity**

- Distance: `Mile`
- Style: `Front Runner`
- Surface: `Turf`
- Distance override (training): `Mile`

**Stat targets — Mile (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 600 | 800 | 400 | 550 |

**Training**

- Stat priority: Speed > Power > Wit > Stamina > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (14 skills, in priority order)**

1. Escape Artist (id 200541)
2. Professor of Curvature (id 200331)
3. Rushing Gale! (id 200371)
4. Unrestrained (id 200551)
5. Mile Maven (id 200681)
6. Mile Corners ◎ (id 201041)
7. Mile Straightaways ◎ (id 201031)
8. Front Runner Corners ◎ (id 201251)
9. Front Runner Straightaways ◎ (id 201241)
10. Changing Gears (id 201051)
11. Restless (id 201281)
12. Front Runner Savvy ◎ (id 201521)
13. Concentration (id 200431)
14. Taking the Lead (id 200531)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Mile, Short, Medium, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 21 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (21)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| The Maruzen I Admire | Option 1 |
| Welcome to Bubblyland | Option 2 |
| Maruzensky's Treasure | Option 1 |
| Hot Rod | Option 1 |
| Let's Play ♪ | Option 1 |
| A Lady's Style ☆ | Option 1 |
| Let's Cook! | Option 1 |
| The Road to a Rad Victory! | Option 1 |
| Down to Dance! | Option 1 |
| Nostalgia Fever ☆ | Option 1 |
| The Secret to Supporting Each Other | Option 1 |
| Even Role Models Get Lonely | Option 2 |
| Meeting New People Is Trendy ☆ | Option 2 |
| The Fun Never Stops ♪ | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 1 |
| Trendsetter | Option 1 |
| Sewing Star | Option 2 |
| My Favorite Things | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### URA Finale

**Build identity**

- Distance: `Mile`
- Style: `Front Runner`
- Surface: `Turf`
- Distance override (training): `Mile`

**Stat targets — Mile (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 600 | 800 | 350 | 550 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (14 skills, in priority order)**

1. Escape Artist (id 200541)
2. Professor of Curvature (id 200331)
3. Rushing Gale! (id 200371)
4. Unrestrained (id 200551)
5. Mile Maven (id 200681)
6. Mile Corners ◎ (id 201041)
7. Mile Straightaways ◎ (id 201031)
8. Front Runner Corners ◎ (id 201251)
9. Front Runner Straightaways ◎ (id 201241)
10. Changing Gears (id 201051)
11. Restless (id 201281)
12. Front Runner Savvy ◎ (id 201521)
13. Concentration (id 200431)
14. Taking the Lead (id 200531)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Mile, Short, Medium, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 21 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (21)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| The Maruzen I Admire | Option 1 |
| Welcome to Bubblyland | Option 2 |
| Maruzensky's Treasure | Option 1 |
| Hot Rod | Option 1 |
| Let's Play ♪ | Option 1 |
| A Lady's Style ☆ | Option 1 |
| Let's Cook! | Option 1 |
| The Road to a Rad Victory! | Option 1 |
| Down to Dance! | Option 1 |
| Nostalgia Fever ☆ | Option 1 |
| The Secret to Supporting Each Other | Option 1 |
| Even Role Models Get Lonely | Option 2 |
| Meeting New People Is Trendy ☆ | Option 2 |
| The Fun Never Stops ♪ | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 1 |
| Trendsetter | Option 1 |
| Sewing Star | Option 2 |
| My Favorite Things | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

## Matikanefukukitaru

### Trackblazer

**Build identity**

- Distance: `Long`
- Style: `Late Surger`
- Surface: `Turf`
- Distance override (training): `Long`

**Stat targets — Long (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 900 | 800 | 400 | 600 |

**Training**

- Stat priority: Speed > Stamina > Power > Wit > Guts
- Spark stat targets (3★ focus): Speed, Stamina, Power
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: false
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Fast & Furious (id 200591)
2. Rising Dragon (id 200611)
3. Professor of Curvature (id 200331)
4. Swinging Maestro (id 200351)
5. Lie in Wait (id 201691)
6. Keen Eye (id 200691)
7. On Your Left! (id 200601)
8. Late Surger Corners ◎ (id 201391)
9. Late Surger Straightaways ◎ (id 201381)
10. Long Corners ◎ (id 201181)
11. Long Straightaways ◎ (id 201171)
12. VIP Pass (id 201201)
13. Late Surger Savvy ◎ (id 201541)
14. Cooldown (id 200741)
15. Concentration (id 200431)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Long, Short, Mile, Medium
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Trackblazer scenario overrides**

- Energy threshold: 40
- Consecutive race limit: 3
- Min stat gain for Charm: 30
- Max retries per race: 1
- Whistle forces training: true
- Shop check grades: G1, G2
- Shop check frequency: every 1 race(s)
- Retry races before final at grades: G1
- Irregular training: enabled (min stat gain 20)
- Excluded shop items: Guts Scroll, Guts Manual, Wit Scroll, Wit Manual

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 17 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (17)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| I'll Protect You! | Option 1 |
| Now or Never! Sacred Sites | Option 1 |
| When Fukukitaru Comes, Fortune Follows | Option 1 |
| Cursed Camera | Option 2 |
| Manhattan's Dream | Option 2 |
| Pretty Gunslingers | Option 1 |
| Seven Gods of Fortune Fine Food Tour | Option 1 |
| Punch in a Pinch | Option 1 |
| Taking the Plunge | Option 1 |
| Shrine Visit | Option 2 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 1 |
| Room of the Chosen Ones | Option 2 |
| Better Fortune! Lucky Telephone | Option 1 |
| Under the Meteor Shower | Option 2 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### Unity Cup

**Build identity**

- Distance: `Long`
- Style: `Late Surger`
- Surface: `Turf`
- Distance override (training): `Long`

**Stat targets — Long (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 900 | 800 | 400 | 600 |

**Training**

- Stat priority: Speed > Stamina > Power > Wit > Guts
- Spark stat targets (3★ focus): Speed, Stamina, Power
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Fast & Furious (id 200591)
2. Rising Dragon (id 200611)
3. Professor of Curvature (id 200331)
4. Swinging Maestro (id 200351)
5. Lie in Wait (id 201691)
6. Keen Eye (id 200691)
7. On Your Left! (id 200601)
8. Late Surger Corners ◎ (id 201391)
9. Late Surger Straightaways ◎ (id 201381)
10. Long Corners ◎ (id 201181)
11. Long Straightaways ◎ (id 201171)
12. VIP Pass (id 201201)
13. Late Surger Savvy ◎ (id 201541)
14. Cooldown (id 200741)
15. Concentration (id 200431)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Long, Short, Mile, Medium
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 17 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (17)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| I'll Protect You! | Option 1 |
| Now or Never! Sacred Sites | Option 1 |
| When Fukukitaru Comes, Fortune Follows | Option 1 |
| Cursed Camera | Option 2 |
| Manhattan's Dream | Option 2 |
| Pretty Gunslingers | Option 1 |
| Seven Gods of Fortune Fine Food Tour | Option 1 |
| Punch in a Pinch | Option 1 |
| Taking the Plunge | Option 1 |
| Shrine Visit | Option 2 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 1 |
| Room of the Chosen Ones | Option 2 |
| Better Fortune! Lucky Telephone | Option 1 |
| Under the Meteor Shower | Option 2 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### URA Finale

**Build identity**

- Distance: `Long`
- Style: `Late Surger`
- Surface: `Turf`
- Distance override (training): `Long`

**Stat targets — Long (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 900 | 800 | 400 | 600 |

**Training**

- Stat priority: Speed > Stamina > Power > Wit > Guts
- Spark stat targets (3★ focus): Speed, Stamina, Power
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (15 skills, in priority order)**

1. Fast & Furious (id 200591)
2. Rising Dragon (id 200611)
3. Professor of Curvature (id 200331)
4. Swinging Maestro (id 200351)
5. Lie in Wait (id 201691)
6. Keen Eye (id 200691)
7. On Your Left! (id 200601)
8. Late Surger Corners ◎ (id 201391)
9. Late Surger Straightaways ◎ (id 201381)
10. Long Corners ◎ (id 201181)
11. Long Straightaways ◎ (id 201171)
12. VIP Pass (id 201201)
13. Late Surger Savvy ◎ (id 201541)
14. Cooldown (id 200741)
15. Concentration (id 200431)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Long, Short, Mile, Medium
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 17 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (17)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| I'll Protect You! | Option 1 |
| Now or Never! Sacred Sites | Option 1 |
| When Fukukitaru Comes, Fortune Follows | Option 1 |
| Cursed Camera | Option 2 |
| Manhattan's Dream | Option 2 |
| Pretty Gunslingers | Option 1 |
| Seven Gods of Fortune Fine Food Tour | Option 1 |
| Punch in a Pinch | Option 1 |
| Taking the Plunge | Option 1 |
| Shrine Visit | Option 2 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 1 |
| Room of the Chosen Ones | Option 2 |
| Better Fortune! Lucky Telephone | Option 1 |
| Under the Meteor Shower | Option 2 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

## Mayano Top Gun

### Trackblazer

**Build identity**

- Distance: `Long`
- Style: `Pace Chaser`
- Surface: `Turf`
- Distance override (training): `Long`

**Stat targets — Long (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 1000 | 900 | 500 | 600 |

**Training**

- Stat priority: Speed > Stamina > Power > Wit > Guts
- Spark stat targets (3★ focus): Speed, Stamina, Power
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: false
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (14 skills, in priority order)**

1. Professor of Curvature (id 200331)
2. Swinging Maestro (id 200351)
3. Race Planner (id 200571)
4. Gourmand (id 201351)
5. Speed Star (id 200581)
6. Pace Chaser Corners ◎ (id 201321)
7. Pace Chaser Straightaways ◎ (id 201311)
8. Long Corners ◎ (id 201181)
9. Long Straightaways ◎ (id 201171)
10. Pace Chaser Savvy ◎ (id 201531)
11. VIP Pass (id 201201)
12. Rushing Gale! (id 200371)
13. Concentration (id 200431)
14. Calm and Collected (id 200561)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Long, Short, Mile, Medium
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Trackblazer scenario overrides**

- Energy threshold: 40
- Consecutive race limit: 3
- Min stat gain for Charm: 30
- Max retries per race: 1
- Whistle forces training: true
- Shop check grades: G1, G2
- Shop check frequency: every 1 race(s)
- Retry races before final at grades: G1
- Irregular training: enabled (min stat gain 20)
- Excluded shop items: Guts Scroll, Guts Manual

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 18 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (18)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| You're My Sunshine ☆ | Option 1 |
| Meant to Be ♪ | Option 1 |
| With My Whole Heart! | Option 1 |
| Maya Will Teach You ☆ | Option 1 |
| Tips from a Top Model! | Option 1 |
| Maya's Race Class ☆ | Option 1 |
| Hearty Chanko! ☆ | Option 1 |
| Maya's Exciting ☆ Livestream! | Option 1 |
| Maya's Euphoric ☆ Livestream! | Option 2 |
| Maya's Special Someone! | Option 1 |
| Wish on a Star | Option 2 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 1 |
| Maya's Thrilling ☆ Test of Courage | Option 1 |
| Sweet Feelings for You ♪ | Option 1 |
| Mayano Takes Off ☆ | Option 2 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### Unity Cup

**Build identity**

- Distance: `Long`
- Style: `Pace Chaser`
- Surface: `Turf`
- Distance override (training): `Long`

**Stat targets — Long (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 1000 | 900 | 500 | 600 |

**Training**

- Stat priority: Speed > Stamina > Power > Wit > Guts
- Spark stat targets (3★ focus): Speed, Stamina, Power
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (14 skills, in priority order)**

1. Professor of Curvature (id 200331)
2. Swinging Maestro (id 200351)
3. Race Planner (id 200571)
4. Gourmand (id 201351)
5. Speed Star (id 200581)
6. Pace Chaser Corners ◎ (id 201321)
7. Pace Chaser Straightaways ◎ (id 201311)
8. Long Corners ◎ (id 201181)
9. Long Straightaways ◎ (id 201171)
10. Pace Chaser Savvy ◎ (id 201531)
11. VIP Pass (id 201201)
12. Rushing Gale! (id 200371)
13. Concentration (id 200431)
14. Calm and Collected (id 200561)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Long, Short, Mile, Medium
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 18 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (18)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| You're My Sunshine ☆ | Option 1 |
| Meant to Be ♪ | Option 1 |
| With My Whole Heart! | Option 1 |
| Maya Will Teach You ☆ | Option 1 |
| Tips from a Top Model! | Option 1 |
| Maya's Race Class ☆ | Option 1 |
| Hearty Chanko! ☆ | Option 1 |
| Maya's Exciting ☆ Livestream! | Option 1 |
| Maya's Euphoric ☆ Livestream! | Option 2 |
| Maya's Special Someone! | Option 1 |
| Wish on a Star | Option 2 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 1 |
| Maya's Thrilling ☆ Test of Courage | Option 1 |
| Sweet Feelings for You ♪ | Option 1 |
| Mayano Takes Off ☆ | Option 2 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### URA Finale

**Build identity**

- Distance: `Long`
- Style: `Pace Chaser`
- Surface: `Turf`
- Distance override (training): `Long`

**Stat targets — Long (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 1000 | 900 | 500 | 600 |

**Training**

- Stat priority: Speed > Stamina > Power > Wit > Guts
- Spark stat targets (3★ focus): Speed, Stamina, Power
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (14 skills, in priority order)**

1. Professor of Curvature (id 200331)
2. Swinging Maestro (id 200351)
3. Race Planner (id 200571)
4. Gourmand (id 201351)
5. Speed Star (id 200581)
6. Pace Chaser Corners ◎ (id 201321)
7. Pace Chaser Straightaways ◎ (id 201311)
8. Long Corners ◎ (id 201181)
9. Long Straightaways ◎ (id 201171)
10. Pace Chaser Savvy ◎ (id 201531)
11. VIP Pass (id 201201)
12. Rushing Gale! (id 200371)
13. Concentration (id 200431)
14. Calm and Collected (id 200561)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Long, Short, Mile, Medium
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 18 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (18)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| You're My Sunshine ☆ | Option 1 |
| Meant to Be ♪ | Option 1 |
| With My Whole Heart! | Option 1 |
| Maya Will Teach You ☆ | Option 1 |
| Tips from a Top Model! | Option 1 |
| Maya's Race Class ☆ | Option 1 |
| Hearty Chanko! ☆ | Option 1 |
| Maya's Exciting ☆ Livestream! | Option 1 |
| Maya's Euphoric ☆ Livestream! | Option 2 |
| Maya's Special Someone! | Option 1 |
| Wish on a Star | Option 2 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 1 |
| Maya's Thrilling ☆ Test of Courage | Option 1 |
| Sweet Feelings for You ♪ | Option 1 |
| Mayano Takes Off ☆ | Option 2 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

## Nice Nature

### Trackblazer

**Build identity**

- Distance: `Medium`
- Style: `Late Surger`
- Surface: `Turf`
- Distance override (training): `Medium`

**Stat targets — Medium (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 800 | 800 | 400 | 600 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: false
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (13 skills, in priority order)**

1. Fast & Furious (id 200591)
2. Rising Dragon (id 200611)
3. Professor of Curvature (id 200331)
4. On Your Left! (id 200601)
5. Lie in Wait (id 201691)
6. Late Surger Corners ◎ (id 201391)
7. Late Surger Straightaways ◎ (id 201381)
8. Medium Corners ◎ (id 201111)
9. Medium Straightaways ◎ (id 201101)
10. Late Surger Savvy ◎ (id 201541)
11. Be Still (id 201692)
12. Swinging Maestro (id 200351)
13. Concentration (id 200431)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Medium, Short, Mile, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Trackblazer scenario overrides**

- Energy threshold: 40
- Consecutive race limit: 3
- Min stat gain for Charm: 30
- Max retries per race: 1
- Whistle forces training: true
- Shop check grades: G1, G2
- Shop check frequency: every 1 race(s)
- Retry races before final at grades: G1
- Irregular training: enabled (min stat gain 20)
- Excluded shop items: Guts Scroll, Guts Manual, Wit Scroll, Wit Manual

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 15 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (15)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| Nature and Her Tired Trainer | Option 1 |
| Bittersweet Sparkle | Option 1 |
| Festive Colors | Option 2 |
| Rainy-Day Fun | Option 1 |
| Not My Style | Option 2 |
| Whirlwind Advice | Option 2 |
| A Little Can't Hurt | Option 1 |
| A Phone Call from Mom | Option 1 |
| Once in a While | Option 2 |
| Snapshot of Emotions | Option 2 |
| Let's Watch the Fish | Option 2 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### Unity Cup

**Build identity**

- Distance: `Medium`
- Style: `Late Surger`
- Surface: `Turf`
- Distance override (training): `Medium`

**Stat targets — Medium (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 800 | 800 | 400 | 600 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (13 skills, in priority order)**

1. Fast & Furious (id 200591)
2. Rising Dragon (id 200611)
3. Professor of Curvature (id 200331)
4. On Your Left! (id 200601)
5. Lie in Wait (id 201691)
6. Late Surger Corners ◎ (id 201391)
7. Late Surger Straightaways ◎ (id 201381)
8. Medium Corners ◎ (id 201111)
9. Medium Straightaways ◎ (id 201101)
10. Late Surger Savvy ◎ (id 201541)
11. Be Still (id 201692)
12. Swinging Maestro (id 200351)
13. Concentration (id 200431)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Medium, Short, Mile, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 15 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (15)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| Nature and Her Tired Trainer | Option 1 |
| Bittersweet Sparkle | Option 1 |
| Festive Colors | Option 2 |
| Rainy-Day Fun | Option 1 |
| Not My Style | Option 2 |
| Whirlwind Advice | Option 2 |
| A Little Can't Hurt | Option 1 |
| A Phone Call from Mom | Option 1 |
| Once in a While | Option 2 |
| Snapshot of Emotions | Option 2 |
| Let's Watch the Fish | Option 2 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### URA Finale

**Build identity**

- Distance: `Medium`
- Style: `Late Surger`
- Surface: `Turf`
- Distance override (training): `Medium`

**Stat targets — Medium (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 800 | 800 | 400 | 600 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (13 skills, in priority order)**

1. Fast & Furious (id 200591)
2. Rising Dragon (id 200611)
3. Professor of Curvature (id 200331)
4. On Your Left! (id 200601)
5. Lie in Wait (id 201691)
6. Late Surger Corners ◎ (id 201391)
7. Late Surger Straightaways ◎ (id 201381)
8. Medium Corners ◎ (id 201111)
9. Medium Straightaways ◎ (id 201101)
10. Late Surger Savvy ◎ (id 201541)
11. Be Still (id 201692)
12. Swinging Maestro (id 200351)
13. Concentration (id 200431)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Medium, Short, Mile, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 15 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (15)</summary>

| Event | Picked option |
|-------|---------------|
| Etsuko's Exhaustive Coverage (G1) | Option 2 |
| Etsuko's Exhaustive Coverage (G2/G3) | Option 2 |
| Etsuko's Exhaustive Coverage (Pre/OP) | Option 2 |
| Nature and Her Tired Trainer | Option 1 |
| Bittersweet Sparkle | Option 1 |
| Festive Colors | Option 2 |
| Rainy-Day Fun | Option 1 |
| Not My Style | Option 2 |
| Whirlwind Advice | Option 2 |
| A Little Can't Hurt | Option 1 |
| A Phone Call from Mom | Option 1 |
| Once in a While | Option 2 |
| Snapshot of Emotions | Option 2 |
| Let's Watch the Fish | Option 2 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

## Sakura Bakushin O

### Trackblazer

**Build identity**

- Distance: `Sprint`
- Style: `Front Runner`
- Surface: `Turf`
- Distance override (training): `Sprint`

**Stat targets — Sprint (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 400 | 900 | 400 | 600 |

**Training**

- Stat priority: Speed > Power > Wit > Guts > Stamina
- Spark stat targets (3★ focus): Speed, Power
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: false
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (14 skills, in priority order)**

1. Adored by All (id 201011)
2. Turbo Sprint (id 200651)
3. Escape Artist (id 200541)
4. Professor of Curvature (id 200331)
5. Swinging Maestro (id 200351)
6. Final Push (id 200552)
7. Sprint Straightaways ◎ (id 200961)
8. Sprint Corners ◎ (id 200971)
9. Front Runner Straightaways ◎ (id 201241)
10. Front Runner Corners ◎ (id 201251)
11. Moxie (id 201282)
12. Concentration (id 200431)
13. Rushing Gale! (id 200371)
14. Straightaway Acceleration (id 200372)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Short, Mile, Medium, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Trackblazer scenario overrides**

- Energy threshold: 40
- Consecutive race limit: 3
- Min stat gain for Charm: 30
- Max retries per race: 1
- Whistle forces training: true
- Shop check grades: G1, G2
- Shop check frequency: every 1 race(s)
- Retry races before final at grades: G1
- Irregular training: enabled (min stat gain 20)
- Excluded shop items: Stamina Scroll, Stamina Manual, Guts Scroll, Guts Manual

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 13 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (13)</summary>

| Event | Picked option |
|-------|---------------|
| Bakushin for Love! | Option 1 |
| A Day Without a Class Rep | Option 1 |
| Bakushin in Signature Racewear! | Option 2 |
| The Bakushin Book! | Option 1 |
| The Voices of the Students | Option 2 |
| Solving Riddles, Bakushin Style! | Option 1 |
| Bakushin?! Class?! | Option 1 |
| Bakushin-ing with a Classmate! | Option 2 |
| The Best Bakushin! | Option 1 |
| Bakushin, Now and Forever! | Option 1 |
| Together with Someone Important! | Option 2 |
| The Speed King | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### Unity Cup

**Build identity**

- Distance: `Sprint`
- Style: `Front Runner`
- Surface: `Turf`
- Distance override (training): `Sprint`

**Stat targets — Sprint (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 400 | 900 | 400 | 600 |

**Training**

- Stat priority: Speed > Power > Wit > Guts > Stamina
- Spark stat targets (3★ focus): Speed, Power
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (14 skills, in priority order)**

1. Adored by All (id 201011)
2. Turbo Sprint (id 200651)
3. Escape Artist (id 200541)
4. Professor of Curvature (id 200331)
5. Swinging Maestro (id 200351)
6. Final Push (id 200552)
7. Sprint Straightaways ◎ (id 200961)
8. Sprint Corners ◎ (id 200971)
9. Front Runner Straightaways ◎ (id 201241)
10. Front Runner Corners ◎ (id 201251)
11. Moxie (id 201282)
12. Concentration (id 200431)
13. Rushing Gale! (id 200371)
14. Straightaway Acceleration (id 200372)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Short, Mile, Medium, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 13 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (13)</summary>

| Event | Picked option |
|-------|---------------|
| Bakushin for Love! | Option 1 |
| A Day Without a Class Rep | Option 1 |
| Bakushin in Signature Racewear! | Option 2 |
| The Bakushin Book! | Option 1 |
| The Voices of the Students | Option 2 |
| Solving Riddles, Bakushin Style! | Option 1 |
| Bakushin?! Class?! | Option 1 |
| Bakushin-ing with a Classmate! | Option 2 |
| The Best Bakushin! | Option 1 |
| Bakushin, Now and Forever! | Option 1 |
| Together with Someone Important! | Option 2 |
| The Speed King | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### URA Finale

**Build identity**

- Distance: `Sprint`
- Style: `Front Runner`
- Surface: `Turf`
- Distance override (training): `Sprint`

**Stat targets — Sprint (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 400 | 900 | 400 | 600 |

**Training**

- Stat priority: Speed > Power > Wit > Guts > Stamina
- Spark stat targets (3★ focus): Speed, Power
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (14 skills, in priority order)**

1. Adored by All (id 201011)
2. Turbo Sprint (id 200651)
3. Escape Artist (id 200541)
4. Professor of Curvature (id 200331)
5. Swinging Maestro (id 200351)
6. Final Push (id 200552)
7. Sprint Straightaways ◎ (id 200961)
8. Sprint Corners ◎ (id 200971)
9. Front Runner Straightaways ◎ (id 201241)
10. Front Runner Corners ◎ (id 201251)
11. Moxie (id 201282)
12. Concentration (id 200431)
13. Rushing Gale! (id 200371)
14. Straightaway Acceleration (id 200372)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Short, Mile, Medium, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 13 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (13)</summary>

| Event | Picked option |
|-------|---------------|
| Bakushin for Love! | Option 1 |
| A Day Without a Class Rep | Option 1 |
| Bakushin in Signature Racewear! | Option 2 |
| The Bakushin Book! | Option 1 |
| The Voices of the Students | Option 2 |
| Solving Riddles, Bakushin Style! | Option 1 |
| Bakushin?! Class?! | Option 1 |
| Bakushin-ing with a Classmate! | Option 2 |
| The Best Bakushin! | Option 1 |
| Bakushin, Now and Forever! | Option 1 |
| Together with Someone Important! | Option 2 |
| The Speed King | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

## Taiki Shuttle

### Trackblazer

**Build identity**

- Distance: `Mile`
- Style: `Pace Chaser`
- Surface: `Turf`
- Distance override (training): `Mile`

**Stat targets — Mile (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 600 | 1000 | 400 | 800 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: false
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (14 skills, in priority order)**

1. Speed Star (id 200581)
2. Mile Maven (id 200681)
3. Changing Gears (id 201051)
4. Corner Connoisseur (id 200341)
5. Professor of Curvature (id 200331)
6. Concentration (id 200431)
7. Prepared to Pass (id 200582)
8. Mile Straightaways ◎ (id 201031)
9. Mile Corners ◎ (id 201041)
10. Pace Chaser Straightaways ◎ (id 201311)
11. Pace Chaser Corners ◎ (id 201321)
12. Head-On (id 201902)
13. Swinging Maestro (id 200351)
14. Homestretch Haste (id 200512)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Mile, Short, Medium, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Trackblazer scenario overrides**

- Energy threshold: 40
- Consecutive race limit: 3
- Min stat gain for Charm: 30
- Max retries per race: 1
- Whistle forces training: true
- Shop check grades: G1, G2
- Shop check frequency: every 1 race(s)
- Retry races before final at grades: G1
- Irregular training: enabled (min stat gain 20)
- Excluded shop items: Stamina Scroll, Stamina Manual, Guts Scroll, Guts Manual

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 13 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (13)</summary>

| Event | Picked option |
|-------|---------------|
| Quick Draw Showdown | Option 1 |
| Must-Win Match | Option 1 |
| To the Top! | Option 2 |
| Hide-and-Seek | Option 1 |
| Embracing Guidance | Option 1 |
| Harvest Festival | Option 1 |
| Meaty Heaven | Option 1 |
| Rainy Power | Option 2 |
| Rainy Choice | Option 1 |
| Rainy Rescue | Option 1 |
| Let's Patrol! | Option 1 |
| Going Home Together | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### Unity Cup

**Build identity**

- Distance: `Mile`
- Style: `Pace Chaser`
- Surface: `Turf`
- Distance override (training): `Mile`

**Stat targets — Mile (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 600 | 1000 | 400 | 800 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (14 skills, in priority order)**

1. Speed Star (id 200581)
2. Mile Maven (id 200681)
3. Changing Gears (id 201051)
4. Corner Connoisseur (id 200341)
5. Professor of Curvature (id 200331)
6. Concentration (id 200431)
7. Prepared to Pass (id 200582)
8. Mile Straightaways ◎ (id 201031)
9. Mile Corners ◎ (id 201041)
10. Pace Chaser Straightaways ◎ (id 201311)
11. Pace Chaser Corners ◎ (id 201321)
12. Head-On (id 201902)
13. Swinging Maestro (id 200351)
14. Homestretch Haste (id 200512)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Mile, Short, Medium, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 13 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (13)</summary>

| Event | Picked option |
|-------|---------------|
| Quick Draw Showdown | Option 1 |
| Must-Win Match | Option 1 |
| To the Top! | Option 2 |
| Hide-and-Seek | Option 1 |
| Embracing Guidance | Option 1 |
| Harvest Festival | Option 1 |
| Meaty Heaven | Option 1 |
| Rainy Power | Option 2 |
| Rainy Choice | Option 1 |
| Rainy Rescue | Option 1 |
| Let's Patrol! | Option 1 |
| Going Home Together | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### URA Finale

**Build identity**

- Distance: `Mile`
- Style: `Pace Chaser`
- Surface: `Turf`
- Distance override (training): `Mile`

**Stat targets — Mile (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 600 | 1000 | 400 | 800 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (14 skills, in priority order)**

1. Speed Star (id 200581)
2. Mile Maven (id 200681)
3. Changing Gears (id 201051)
4. Corner Connoisseur (id 200341)
5. Professor of Curvature (id 200331)
6. Concentration (id 200431)
7. Prepared to Pass (id 200582)
8. Mile Straightaways ◎ (id 201031)
9. Mile Corners ◎ (id 201041)
10. Pace Chaser Straightaways ◎ (id 201311)
11. Pace Chaser Corners ◎ (id 201321)
12. Head-On (id 201902)
13. Swinging Maestro (id 200351)
14. Homestretch Haste (id 200512)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Mile, Short, Medium, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 13 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (13)</summary>

| Event | Picked option |
|-------|---------------|
| Quick Draw Showdown | Option 1 |
| Must-Win Match | Option 1 |
| To the Top! | Option 2 |
| Hide-and-Seek | Option 1 |
| Embracing Guidance | Option 1 |
| Harvest Festival | Option 1 |
| Meaty Heaven | Option 1 |
| Rainy Power | Option 2 |
| Rainy Choice | Option 1 |
| Rainy Rescue | Option 1 |
| Let's Patrol! | Option 1 |
| Going Home Together | Option 1 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

## Vodka

### Trackblazer

**Build identity**

- Distance: `Medium`
- Style: `Late Surger`
- Surface: `Turf`
- Distance override (training): `Medium`

**Stat targets — Medium (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 700 | 900 | 400 | 600 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: false
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (14 skills, in priority order)**

1. All-Seeing Eyes (id 201441)
2. On Your Left! (id 200601)
3. Fast & Furious (id 200591)
4. Rising Dragon (id 200611)
5. Lie in Wait (id 201691)
6. Professor of Curvature (id 200331)
7. Mile Maven (id 200681)
8. Medium Corners ◎ (id 201111)
9. Medium Straightaways ◎ (id 201101)
10. Late Surger Corners ◎ (id 201391)
11. Late Surger Straightaways ◎ (id 201381)
12. Swinging Maestro (id 200351)
13. Slick Surge (id 200602)
14. Homestretch Haste (id 200512)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Medium, Mile, Short, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Trackblazer scenario overrides**

- Energy threshold: 40
- Consecutive race limit: 3
- Min stat gain for Charm: 30
- Max retries per race: 1
- Whistle forces training: true
- Shop check grades: G1, G2
- Shop check frequency: every 1 race(s)
- Retry races before final at grades: G1
- Irregular training: enabled (min stat gain 20)
- Excluded shop items: Guts Scroll, Guts Manual

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 14 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (14)</summary>

| Event | Picked option |
|-------|---------------|
| Vintage Style | Option 1 |
| Makings of a Friend | Option 1 |
| Hot and Cool | Option 1 |
| Like a Kid | Option 1 |
| Challenging Fate | Option 2 |
| Showdown by the River! | Option 1 |
| The Ultimate Choice | Option 1 |
| Awkward Honesty | Option 1 |
| The Standards of Coolness | Option 1 |
| Ring Out, Passionate Sound! | Option 1 |
| The Way of Cool | Option 2 |
| Let's Take a Little Detour | Option 1 |
| Sugar and Spice | Option 2 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### Unity Cup

**Build identity**

- Distance: `Medium`
- Style: `Late Surger`
- Surface: `Turf`
- Distance override (training): `Medium`

**Stat targets — Medium (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 700 | 900 | 400 | 600 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (14 skills, in priority order)**

1. All-Seeing Eyes (id 201441)
2. On Your Left! (id 200601)
3. Fast & Furious (id 200591)
4. Rising Dragon (id 200611)
5. Lie in Wait (id 201691)
6. Professor of Curvature (id 200331)
7. Mile Maven (id 200681)
8. Medium Corners ◎ (id 201111)
9. Medium Straightaways ◎ (id 201101)
10. Late Surger Corners ◎ (id 201391)
11. Late Surger Straightaways ◎ (id 201381)
12. Swinging Maestro (id 200351)
13. Slick Surge (id 200602)
14. Homestretch Haste (id 200512)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Medium, Mile, Short, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 14 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (14)</summary>

| Event | Picked option |
|-------|---------------|
| Vintage Style | Option 1 |
| Makings of a Friend | Option 1 |
| Hot and Cool | Option 1 |
| Like a Kid | Option 1 |
| Challenging Fate | Option 2 |
| Showdown by the River! | Option 1 |
| The Ultimate Choice | Option 1 |
| Awkward Honesty | Option 1 |
| The Standards of Coolness | Option 1 |
| Ring Out, Passionate Sound! | Option 1 |
| The Way of Cool | Option 2 |
| Let's Take a Little Detour | Option 1 |
| Sugar and Spice | Option 2 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---

### URA Finale

**Build identity**

- Distance: `Medium`
- Style: `Late Surger`
- Surface: `Turf`
- Distance override (training): `Medium`

**Stat targets — Medium (active column)**

| Speed | Stamina | Power | Guts | Wit |
|------:|--------:|------:|-----:|----:|
| 1200 | 700 | 900 | 400 | 600 |

**Training**

- Stat priority: Speed > Power > Stamina > Wit > Guts
- Spark stat targets (3★ focus): Speed, Power, Stamina
- Max failure %: 15
- Disable training on maxed stat: true
- Rainbow training bonus: false
- Rest before summer: true
- Risky training: disabled
- Train Wit during finale: true
- Prioritize skill hints: true

**Skill buying**

- Mid-run check: enabled @ ≥1200 SP
  - Strategy: `optimize_skills` · Buy inherited uniques: true · Buy negative skills: false
- Pre-finals: enabled (`optimize_skills`) · inherited uniques: true
- Career complete: enabled (`optimize_skills`) · inherited uniques: true

**Skill priority list (14 skills, in priority order)**

1. All-Seeing Eyes (id 201441)
2. On Your Left! (id 200601)
3. Fast & Furious (id 200591)
4. Rising Dragon (id 200611)
5. Lie in Wait (id 201691)
6. Professor of Curvature (id 200331)
7. Mile Maven (id 200681)
8. Medium Corners ◎ (id 201111)
9. Medium Straightaways ◎ (id 201101)
10. Late Surger Corners ◎ (id 201391)
11. Late Surger Straightaways ◎ (id 201381)
12. Swinging Maestro (id 200351)
13. Slick Surge (id 200602)
14. Homestretch Haste (id 200512)

**Racing**

- Preferred terrain: Turf
- Preferred grades: G1, G2, G3
- Preferred distances (search order): Medium, Mile, Short, Long
- Days to run extra races: 5
- Min fans threshold: 0
- Free race retries: true · Complete career on failure: true
- Junior strategy: Default · Original strategy: Default

**Training event overrides**

- Special events (cross-character): 11 entries
- Character-specific: 14 entries
- Support-card-specific: 0 entries
- Scenario-specific: 0 entries
- OCR confidence: 90% · Auto retry: true
- Prioritize energy options: true

<details><summary>Character event picks (14)</summary>

| Event | Picked option |
|-------|---------------|
| Vintage Style | Option 1 |
| Makings of a Friend | Option 1 |
| Hot and Cool | Option 1 |
| Like a Kid | Option 1 |
| Challenging Fate | Option 2 |
| Showdown by the River! | Option 1 |
| The Ultimate Choice | Option 1 |
| Awkward Honesty | Option 1 |
| The Standards of Coolness | Option 1 |
| Ring Out, Passionate Sound! | Option 1 |
| The Way of Cool | Option 2 |
| Let's Take a Little Detour | Option 1 |
| Sugar and Spice | Option 2 |
| Acupuncture (Just an Acupuncturist, No Worries! ☆) | Option 1 |

</details>

**General**

- Stop before finals: false · Stop at date: false
- Crane game attempt: true · Popup check: false

---
