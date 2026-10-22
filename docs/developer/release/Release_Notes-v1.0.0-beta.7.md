# LeTrain v1.0.0-beta.7 — Programming, reliability & polish

> Draft for the GitHub release body. Update the “Known issues” section after the final smoke test and triage, then copy this summary into the release (the workflow appends the auto-generated “What's Changed” list).

This beta is the quality-and-tooling step after `beta.6`: four internal quality sweeps, a faster and more predictable pathfinder, a rebuilt Programming editor in both clients, and two autopilot/DSL fixes found during playtesting.

## Highlights

- **Quality tandas 1–4** — core hardening, playtest polish, performance/2D–3D parity and repository hygiene (dead code removed, one SLF4J binding, docs corrected).
- **A\* rewrite** — priority queue instead of the linear open-set scan, admissible entry-port heuristic, hot-path logs moved to DEBUG.
- **Programming v2** — honest quick reference (no `#` placeholders, prose no longer insertable), `<placeholders>` with Tab navigation, preview, scope hints (`[top]` / `[in {}]`), correct scopes inside `program { … }`, editor comfort (Tab indent, auto-indent, `Ctrl+Tab`/`F6`) and error navigation with line:column, in 2D and 3D.
- **Direction-aware fork orientation** — the autopilot now sets the fork ahead on parallel-segment loops (passing loops/sidings) instead of the first shared node; fixes itineraries that skipped a station on a closed loop.
- **`wait N` stops the train** — if the train is moving, it brakes, waits `N` simulation seconds and resumes; `wait 0` is a no-op and negative values are rejected with a visible notice.
- **Docs** — ADR-031/032/033, grammar EN/ES, editor guide and autopilot analyses synced with the code.

## Fixes and quality

- 2D and 3D keep the same behaviour and keyboard flow for the editor and quick reference.
- Save/load, scenarios (`on build`/`on start`/`program`/`configuration`) and old savegames were exercised during the quality sweeps.
- CI checks the class index and formatting on every change.

## Known issues

- **Save/load during a manoeuvre**: saving while a deferred `wait`/`load` is running may lose the pending action, and the train may stay stopped after loading. Planned for beta.8; avoid saving during a manoeuvre for now (#842).
- #239 wagons may clip into the track in the 3D view (deferred).
- #250 train-sound synchronization; #795 2D fork visibility (if not fixed before the tag).
- #712 savegame serialization audit is planned before 1.0, not a blocker for this beta.

## Install

- GitHub release: `LeTrain-Linux.zip` / `LeTrain-Windows.zip` (self-contained launchers plus `letrain-check`).
- itch.io: `avaraujo/letrain-procedural-tycoon`.
- Snap Store: `letrain`.

**Full changelog**: https://github.com/antoniovazquezaraujo/LeTrain/compare/v1.0.0-beta.6...v1.0.0-beta.7
