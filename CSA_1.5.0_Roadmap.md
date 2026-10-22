# Create: Signalling Additions (CSA) 1.5.0 — Development Roadmap

**Status:** Approved development plan  
**Baseline:** CSA 1.4.1  
**Target platform:** Minecraft 1.21.1, NeoForge 21.1.243, Create 6.0.10, Java 21  
**Build delivery:** Complete Windows-buildable source ZIP with `BUILD.bat`; user compiles locally.

## Release sequence

| Stage | Focus | Completion criteria |
|---|---|---|
| **Alpha 1** | Persistent speed restrictions | Save and restore active restrictions exactly across world reloads (including stationary trains); preserve pending higher-speed board transitions until the entire train clears the boundary; validate records; clean up permanently deleted trains; avoid needless writes and chunk loads. |
| **Alpha 2** | Shunting improvements | Preserve shunting through stops and reversals; correct entry and exit conditions; maintain 25 km/h default shunt limit; restore underlying speed restriction on exit. |
| **Alpha 3** | Closest-train priority | Identify closest relevant approaching train to determine displayed aspect; when identification is uncertain, use the most restrictive valid aspect; handle stopped/reversing trains safely. |
| **Alpha 4** | Reservation handling | Interpret each train's authorised route correctly; prevent signalling authority beyond its own clear route; leave Create's reservation management authoritative. |
| **Alpha 5** | Configuration and performance | Make operational limits configurable with existing defaults; minimise redundant per-tick scans, recalculations, allocations, chunk loading and saves; preserve signalling correctness. |
| **Alpha 6** | Low Speed signalling | Implement Low Speed aspect and behaviour; initially reuse the existing Shunt Add-on model with a green lamp, while main lights show Red over Red; integrate relevant speed controls. |
| **Alpha 7** | Railway map integration | Track linked CSA signals at their authoritative Create track-graph positions; display live aspects by default; offer optional detailed route/reservation/train information via client config; keep external map integrations optional and assess supported APIs. |
| **Beta 1** | Multiplayer/server testing | Begin server testing, compatibility checks and profiling after locally tested alphas. |

## Agreed design principles

- Each alpha includes the features from preceding alphas; avoid introducing later-stage features prematurely.
- **Alpha builds are tested locally only.** Server/multiplayer testing starts at beta.
- Signal and reservation safety take priority over performance shortcuts. If approaching-train priority is uncertain, choose the most restrictive valid indication.
- Restore saved speed restrictions exactly on reload, while still allowing new, more restrictive instructions to apply.
- Keep existing defaults when adding configurable operational limits; server remains authoritative for operational rules.
- Avoid unnecessary bloat, repeated whole-network scans, forced chunk loads, excessive allocations and redundant persistence writes.
- Use Create's signalling and reservation systems as the foundation rather than replacing them.
- Signal mapping is **not** in Alpha 1. For future map support, live aspects are the default and detailed signalling information is an opt-in client setting.

## Packaging and correction conventions

1. Prepare a complete source package for the stage, with Java source, resources, build configuration and a Windows `BUILD.bat`.
2. Perform **two reviews** of each package before delivery; correct issues discovered.
3. State clearly whether compilation was actually verified. A ZIP integrity check is not a successful Java build.
4. For an initial alpha that fails to compile, name the first repair **Alpha N Fix**. After that Fix builds, use **Alpha N-1**, **Alpha N-2**, etc. for later implementation corrections.
5. Preserve original Blockbench-exported geometry, UVs, texture assignments, rotations and proportions unless an agreed change is necessary.
6. Keep the CSA Signal Linking Tool out of the `create_signal_extensions:heightvariable` item tag.

## Immediate next milestone: Alpha 1

- Finish and connect saved restriction and pending-boundary restoration.
- Handle train deletion and stale data safely.
- Review edge cases: stops, reversals, graph changes and world reloads.
- Audit persistence for unnecessary processing and disk writes.
- Deliver `CSA_1.5.0_Alpha_1.zip` for a **local Windows build attempt**, not as a claimed precompiled JAR.

## Progress log

- **2026-10-22:** Alpha 1a integrates the checkpointed local speed-zone overlay, corrects reverse-direction route selection and automatic braking, and completes pending-boundary/deleted-train persistence handling. `BUILD.bat` compilation and deterministic regression checks are verified; in-game acceptance is pending. See `ALPHA_1A_RELEASE_NOTES.md`. This track inspection overlay is separate from the railway map integrations scheduled for Alpha 7.

- **2026-10-21:** Roadmap approved. Previous Alpha 1 WIP/checkpoints are **not** considered completed or compilation-verified releases.

_Update this document as each alpha is built, tested and accepted._
