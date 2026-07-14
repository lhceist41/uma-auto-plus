# Engineering notes

Standing trade-offs. None of these is a prohibition — each is a cost/benefit call to be made on the
merits, with the trade stated. They are recorded here so the same argument does not get re-litigated
from scratch every few months.

## ABI set

The release currently splits `arm64-v8a`, `armeabi-v7a`, and `x86_64` (`universalApk false`).
`x86_64` was added for emulator-native speed on MuMu, which is the primary target.

The open question is whether to drop 32-bit ARM. Dropping it simplifies the build and shrinks the
release matrix, at the cost of users on 32-bit-only devices. Every development device is 64-bit, so
this is a question about the *external* user base, not about our own convenience. Decide it on
download data, not on assumption.

Authoritative source for the current set: `android/app/build.gradle`. Do not restate the split in
prose — it drifts.

## Large upstream refactors

Upstream's bigger features — most notably the MILP race scheduler under `bot/solver/`, and the
`findSuitableRace` extraction — are welded to a restructured `bot/` package. They are not casual
merges into our divergent `Racing`/`Trackblazer`; they are a **re-baseline** decision. See
`docs/UPSTREAM.md`.

The re-baseline has been evaluated once and declined: the solver's value is weighted toward one
scenario, it is orthogonal to our scorer, and adopting its executor would regress our single-star
prediction perception (see `docs/INCIDENTS.md`, entry 5). That decision stands until new evidence
arrives — but "declined once" is not "forbidden forever". Re-open it with data.

The counter-example worth remembering: DecisionTracer was assumed to be welded to the restructured
package and was skipped on that basis for months. When finally diffed at the function level it turned
out to be self-contained and ported cleanly. **Diff the function, not the file, before concluding
that a feature requires a re-baseline.**

## Model, solver, and other heavyweight libraries

The bot already ships machine learning at runtime — ML Kit OCR, Tesseract, and a YOLOv8 model via
ONNX Runtime. Adding another such dependency is a normal technical decision, not a special case.

Apply a cost test before adopting one:

- APK size and any model-download weight.
- ABI impact.
- Supply chain and licence.
- **The real bar:** does it measurably improve hands-off play across scenarios? "Sounds general" is
  not "is general."

Two worked examples. A pure-JVM solver library (for instance ojAlgo) carries almost no size or ABI
cost and is fine *if we adopt the feature it backs*. Conversely, a language-model module that only
answers questions about documentation fails the test on merit — it never touches an automation
decision. A model that chose options for training events missing from the local event database would
be a genuinely different proposition, and would pass.

For a local, offline automation tool, prefer on-device or open-weights options over network-dependent
vendor SDKs: no API key, works without connectivity, better latency, and no runtime dependency on a
third party's uptime.

## Heavy diagnostics

Performance loggers, decision tracers, and log-stream servers are fine to build and use. Gate them
off by default in *release* builds for performance and APK size — that is a performance decision,
not a ban.

DecisionTracer is the shipped example: ported standalone, DEBUG-gated, and emitting one `[DECISION]`
block per turn containing the chosen action or training, the rejected alternatives, the full runner-up
contest, and extra-race eligibility. It is wired into `Campaign`, `Training`, and `Trackblazer`.

**Placement lesson:** put a per-turn decision record at the *caller* of the decision, not inside the
function that computes it. Records written inside the computation miss the cases where the caller
never reaches it — which are exactly the cases worth tracing.
