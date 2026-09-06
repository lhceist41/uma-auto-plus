# UI Truthfulness

These rules govern project-owned player-facing presentation: Home, settings pages, queue and run
status, banners, cards, labels, buttons, warnings, error copy, empty and loading states, and
player-facing operational controls. When you build or change any of those surfaces, they apply.

They are not authority for OCR and CV, detection thresholds, template matching, reverse
engineering, race logic, training logic, campaign strategy, queue execution semantics, navigation
state machines, automation timing, concurrency, persistence semantics, device safety, ADB policy,
or data recovery. Those areas stay under the source, tests, subsystem documentation, and safety
rules that already govern them, and nothing here authorizes changing their behavior to suit a
presentation. Do not push UI-specific prescriptions into them.

These are truthfulness and operational-quality guardrails, not a visual style guide. They mandate
no color, font, gradient, spacing system, card shape, illustration style, or marketing aesthetic.
Match the existing UMA Auto+ design unless a task explicitly asks for a redesign. Do not install or
depend on a third-party UI rules package for this project unless a future task reopens that
decision; this document is the authority.

## Rules

**1. Show only state a producer can prove.** No invented progress, guessed status, placeholder
percentages, fabricated success or failure detail, or derived state dressed up as live telemetry.
Unknown stays unknown, presented with neutral copy.
*Can every displayed value be traced to a real producer or to deterministic local state?*

**2. A visible control must be a working control.** Do not offer an action that cannot currently
succeed, do not label one action as another, and do not leave a control on screen after the state
that made it valid has ended.
*Does this control exist only while its action is valid, and does its label match what the action does?*

**3. Materially different states must look different.** Two operational states that lead to
different player decisions do not get collapsed because they share a visual shell. Armed and
waiting is not running; stopped is not completed; halted is not failed; the run reached is not the
number of runs completed.
*Can the player tell these states apart without inferring the wrong one?*

**4. Player copy describes what happened, not how it is implemented.** No exception text, stack
traces, class names, database wording, raw machine status strings, navigation internals, or
implementation-only enum values on a player surface. Internal planning terminology (phase numbers,
task or batch names, audit wording) is equally out of place there. A surface that is deliberately
diagnostic is the exception, and says so on the surface itself.
*Would this text make sense to a player who has never seen the source?*

**5. Unknown and malformed states fail closed.** For stringly typed or externally produced state,
map anything unrecognized to one safe generic presentation. Never interpolate the raw unknown value
into player-facing text.
*If the producer adds a state tomorrow, does this UI degrade safely instead of leaking or lying?*

**6. Operational access outranks ornament.** The control or status a player needs most often stays
the easiest to reach. Do not bury it under hierarchy, and do not let decoration outweigh the thing
being operated.
*Is the most operationally important information or action still the fastest to find and use?*

**7. Consequence is stated where the choice is made.** An action that discards saved state, stops a
queue, spends a resource, abandons progress, or is otherwise irreversible carries its consequence
at the control itself, not in unrelated text elsewhere on the screen.
*Can the player understand the consequence before committing to the action?*

**8. Interaction semantics are real, not decorative.** Keep touch targets usable, keep existing
accessibility labels and roles unless you replace them, and do not add a control that is visible
but not operable through the interaction semantics the app already supports.
*Is this control actually operable and understandable through the semantics the app supports?*

**9. Validation matches the risk and never manufactures it.** Deterministic presentation logic gets
pure helpers and behavior tests. Runtime-sensitive UI gets live validation of the states that are
safe to reach; do not fabricate a dangerous state or spend real game state merely to photograph a
banner. Partial runtime coverage is an acceptable result when source and test evidence is stronger
than the safety cost of producing the rest, as long as the report names the states that were not
observed. The repository's normal verification requirements for the files you changed still apply
on top of this.
*Is the evidence strong enough for the claim without creating more risk than the behavior itself?*

**10. The UI adapts to the source contract, never the reverse.** Visual consistency is not a reason
to change what a state means, to widen a state machine, or to make persistence report something it
does not know. If the UI cannot express the real contract, change the UI. The source and the state
it actually produces decide what the truth is.
*Did the UI adapt to the source contract, or was the contract bent to fit a preferred UI?*
