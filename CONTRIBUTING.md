# Contributing to UMA Auto+

This project automates a game that is tied to a personal account. Almost everything useful while
developing it (screenshots, logs, career results, settings databases) is also a record of somebody's
account. The rules below exist so that working material stays local and only durable, generic,
reusable content is published.

Read the publication policy before your first commit. The automated check described at the end
enforces the parts a script can check; the rest is a review step.

## The one-question test

Before adding a file, ask: **would this still be useful to a stranger who does not have my account,
my emulator, or my current investigation?**

If yes, it may belong in the repository. If no, keep it local.

## Allowed in the public repository

- Source code required to build, test, and run the app.
- Stable build and runtime configuration.
- Dependency manifests and lockfiles.
- Reproducible scripts and CI workflows.
- Issue and pull request templates.
- Stable player-facing documentation.
- Stable contributor-facing engineering documentation.
- Changelog entries describing shipped behavior.
- Source artwork and templates intentionally shipped in the app.
- Minimal, sanitized fixtures that an active test actually references.
- Generated files only when they cannot be reliably regenerated, with the exception documented.

## Forbidden in the public repository

**Account and player identity**

- Trainer names, player IDs, friend IDs, or any account identifier.
- Account-specific decks, parents, borrowed-support owners, inheritance data, currencies,
  inventories, or private settings.
- One-off career results, control-run write-ups, config fingerprints, settings revisions, local
  install times, or account telemetry.

**Captures and diagnostics**

- Raw screenshots, device captures, OCR crops, recordings, or video, other than sanitized test
  fixtures.
- Raw logs, JSONL telemetry, databases, SQLite journals, caches, or temporary captures.
- Emulator serials, local IP addresses, machine names, user-home paths, drive letters, or any
  private filesystem location.

**Credentials**

- Secrets, tokens, passwords, environment files, certificates, signing keys, release keystores, or
  private keys of any kind.

**Build output**

- APK, AAB, APKS, IDSIG, DM, the JS bundle, coverage output, and Gradle, Metro, or Expo caches.
- Release binaries must never enter Git history; they belong on the Releases page.

**Working material**

- Assistant prompts, pasted assistant output, transcripts, scratch instructions, task files,
  validation write-ups, or research requests.
- Disposable benchmark or experiment output.
- Player-facing documentation containing internal task identifiers, phase numbers, audit language,
  or private implementation sequencing.

## Conditional material

### Screenshots and fixtures

A fixture is a test input, not a memento.

- Keep the smallest image region the test actually needs.
- Crop or redact unrelated account UI: currencies, trainer name, friend lists, inventory counts.
- Remove account identifiers and private values.
- Every tracked fixture must be referenced by an active test. Unreferenced fixtures get deleted.
- Keep raw source captures in an ignored private directory and commit only the cropped result.
- When a fixture changes, update the tests and any recorded hashes in the same change.
- Do not fabricate a synthetic screenshot when the test depends on real screen geometry.

### Research and technical notes

Publish a note only when it is durable, generic, accurate, and useful to a future contributor.

- A single investigation's narrative is not documentation. Keep it local.
- No account-specific evidence: no scores, no career-by-career tables, no fingerprints.
- Prefer to encode a durable conclusion as a test, a short comment where the code makes the
  decision, or a paragraph in the contributor documentation.
- A hypothesis that was investigated and rejected does not earn a permanent home in production
  source as an archive. Record the conclusion, drop the chronology.

### Generated files

- Prefer regeneration in a script or in CI over tracking the output.
- Do not untrack existing generated output until a fresh clone proves the build regenerates it.
- Every generated file that stays tracked must have its reason written down, and an allowlist entry
  in the hygiene check.
- The one standing tracked exception: the React Native asset drawables under
  `android/app/src/main/res/drawable-*` (bundler output with `node_modules_*` and `src_*` names).
  Release builds regenerate their own copies during the build, but debug APKs are built without
  that bundling step and take these from the tree, and regenerating the set has been verified
  byte-identical against the tracked copies. Never edit them by hand: they change only when
  `src/assets`, a page's icons, or a dependency's shipped assets change, and the regenerated
  files ride in the same change that caused them.

### Public documentation

- Describe shipped behavior, defaults, limitations, and any action a user needs to take.
- Exclude private validation detail and internal task terminology.
- Do not present a conclusion drawn from one run as a general property.

### Commit messages

- No personal data, local paths, prompts, transcripts, account values, or private diagnostics.
- Describe what changed and why.
- Do not falsify authorship and do not strip legitimate attribution.

## Before you open a pull request

1. `yarn test:hygiene` passes.
2. `node_modules/.bin/tsc --noEmit` reports no errors.
3. `yarn jest` passes.
4. `cd android && ./gradlew testDebugUnitTest` passes when Kotlin changed.
5. You have read your own diff and confirmed it contains no account data, capture, log, or
   generated output.

## The automated hygiene check

`scripts/check-repo-hygiene.mjs` inspects tracked paths only, using `git ls-files`. It fails on
private directories, databases and journals, raw logs and JSONL telemetry, installable and
intermediate build output, credentials, `.env` files other than `.env.example`, and any Markdown at
the repository root outside the documented public set.

Run it locally:

```
yarn test:hygiene
```

It runs on every pull request and on pushes to `main`.

The check is deliberately path based. It does not scan file contents for secrets, because a
heuristic content scanner produces false positives that reviewers learn to ignore, which is worse
than having no check. Judging content is the reviewer's job, guided by the policy above.

If a path is refused but genuinely belongs in the repository, add it to the `ALLOWLIST` in the
script together with the reason. An exception without a reason will be rejected in review.

## Building from a fresh clone

Two files the Android build needs are deliberately not tracked, so a first build needs one extra
step each.

**`android/local.properties`** points at your own Android SDK. It is machine specific and stays
local. Create it with a single line:

```
sdk.dir=/path/to/your/Android/Sdk
```

**`android/gradle/wrapper/gradle-wrapper.jar`** is not tracked. Gradle refuses to start without it
with a bare `Unable to access jarfile` message, which is not obviously a bootstrap problem. Fetch
and verify it with:

```
yarn bootstrap:android
```

The script reads the pinned Gradle version from `gradle-wrapper.properties`, downloads the matching
wrapper jar, and refuses to install it unless its SHA-256 matches the hash pinned in
`scripts/bootstrap-gradle-wrapper.mjs`. When Gradle is upgraded, bump `distributionUrl` and add the
new version's jar hash to that script in the same change, so the wrapper and the distribution never
drift apart. The build workflows run the same script, so CI bootstraps exactly the way a fresh
clone does.

**Icon fonts.** The vector-icon TTFs ship from `node_modules`, not from the repository, and a
release APK built without them renders icons as boxes. The release workflow copies them in its own
step; for a local build run the same copy once after installing dependencies:

```
mkdir -p android/app/src/main/assets/fonts
cp node_modules/@expo/vector-icons/build/vendor/react-native-vector-icons/Fonts/*.ttf android/app/src/main/assets/fonts/
```

**Debug signing.** `android/app/debug.keystore` is not tracked. Release builds and unit tests do
not use it, but `assembleDebug` needs it. Generate a standard debug keystore once:

```
keytool -genkeypair -keystore android/app/debug.keystore -alias androiddebugkey -storepass android -keypass android -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -keysize 2048 -validity 10000
```

**Windows path length.** Clone into a short path such as `C:\dev\uma-auto-plus`. The NDK stage
nests intermediate directories deeply, and a clone in a long directory fails with misleading CMake
and ninja errors ("unknown target CPU", "manifest 'build.ninja' still dirty") that look nothing
like the real problem.

## Versioning

The Android build metadata is the single authority for the application version:
`android/gradle/libs.versions.toml` defines `app-versionName` and `app-versionCode`. The `version`
field in `package.json` is npm packaging metadata and is not the app version; do not treat the two
as needing to agree, and do not bump either one as part of unrelated work.
