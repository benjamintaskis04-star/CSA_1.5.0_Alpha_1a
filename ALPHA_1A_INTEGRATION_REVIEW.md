# Alpha 1a integration review — 22 October 2026

## Added in this pass
- `SpeedZoneBoundaryResolver`: pure, deterministic directional board-boundary resolution for a pre-resolved track path. Ends each zone at the next posted board; excludes turnout-specific restrictions because these are route-dependent. Handles invalid input and coincident board positions.

## Remaining blockers — not a release
- Map linked boards to oriented Create graph edges and construct a route path; do not automatically propagate across ambiguous junctions.
- Sync all configured boards in range to clients (only loaded block entities are locally visible).
- Integrate the translucent mesh into NeoForge world rendering and crosshair targeting; match the assembly sleeper highlight.
- Diagnose/correct actual train speed enforcement, reverse-driving displayed aspects, and duplicate boundary events.
- Restore Gradle wrapper, compile against Create/NeoForge, perform two QA passes, and in-game test.

The new resolver has not been compiled in the full mod. Do not replace the working Alpha 1 Diag JAR.
