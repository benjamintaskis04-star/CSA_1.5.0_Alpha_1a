# Alpha 1a implementation and verification

Current implementation: **1.5.0-alpha.1a**, 22 October 2026.
The checkpoint documents describe earlier snapshots; this file supersedes their unfinished-work lists.

## Implemented

- Client world rendering for directional speed zones using Create's assembly sleeper mask, sampled graph curves/slopes, the existing continuous colour palette, configured opacity and render distance.
- Independent crosshair-zone inspection and optional global visibility (`speed_zone_visualisation.show_all_speed_zones`, default false).
- Server-to-client board snapshots and a per-dimension saved visual board index. Previously indexed unloaded signs remain usable without loading their chunks. Existing worlds populate the index as their signs load normally.
- Straight-track graph mapping without walking world chunks; curved targets use Create's selected Bezier graph location. Tracing stops at ambiguous junctions, missing edges, portals, cycles and work limits. Unrestricted boards end the preceding coloured zone. Turnout boards do not create unconditional fixed zones. Colours describe the default speed panel, independently of train-category enforcement.
- Automatic braking envelopes derived from Create's actual acceleration, with discretisation margin and braking-distance-based look-ahead (640 to 8192 blocks). Existing automatic navigation target capping, player throttle behaviour and speed diagnostics are retained.
- Reverse-direction look-ahead, leading-end selection through automatic stops, and signal-side checks for manual routes, scheduled routes and following-signal selection.
- Duplicate pending-boundary suppression. Newly saved higher-speed boundaries retain their original target and travel direction; rear release requires directional passage instead of mere proximity. Older saves upgrade when the linked sign becomes available.
- Restoration before an automatic train first consumes its navigation target; static state is cleared when leaving a world. Saved deleted-train records are pruned every 200 ticks against Create's full train registry, including virtual and temporarily graphless trains.
- Windows builds use a temporary local workspace to avoid observed OneDrive source-read failures, then copy successful JARs to `build/libs`. Build failure still returns a nonzero status.

## Verification

`BUILD.bat` was run with JDK 21 against Minecraft 1.21.1, NeoForge 21.1.243 and Create 6.0.10-280. Java compilation and the Gradle build succeeded.

The build runs `alpha1aChecks`: 25 deterministic regression checks for directional/swept rear clearance, stationary restoration, reversal, parallel tracks, unrestricted/coincident boundaries, colour interpolation, sloped geometry and automatic X20 braking with several acceleration settings. These are code-level checks, not an in-game train test.

Two source review passes covered (1) API compatibility, operational direction and braking, and (2) persistence, lifecycle, chunk access, visual boundaries and packaging. Corrections from both reviews are included.

In-game visual alignment, real X20 operation, world reloads with long trains, and reverse-driving signal behaviour still need local acceptance testing. Multiplayer testing remains deferred to beta. No in-game or multiplayer test is claimed.

## Local acceptance checklist

1. Load a copy of a test world with the new JAR. Let existing configured signs load once so the visual index is populated.
2. Inspect straight, curved and sloped zones with the crosshair; toggle global visibility and adjust opacity/distance. Confirm the next same-direction board ends each zone, including unrestricted boards, and no ambiguous turnout exit is painted.
3. Run an automatic train toward an X20 diverging route from line speed, then repeat in reverse and on the straight route. Check NAV diagnostics against actual speed.
4. Stop after a restrictive board, save, reload and confirm the same restriction. Repeat with several pending increases while the rear is before their targets.
5. Reverse before the rear clears an increase: the original increase must not release from proximity or backwards movement. Then pass the boundary in its original direction.
6. Check both physical signal directions and the existing manual throttle, shunt, route, theatre and fixed-red features.
7. Delete a train, allow 200 server ticks, and confirm its saved speed record is removed. Leave another train virtual/graphless and confirm its record remains.

Visual work limits are conservative: snapshots contain at most 4096 boards per dimension, and each zone traversal stops after 256 edges or 2048 blocks. In uncertain or incomplete graph geometry the overlay stops; it never changes train authority.
