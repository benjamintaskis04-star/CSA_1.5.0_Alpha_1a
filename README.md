# Create: Signalling Additions 1.5.0 Alpha 1a

Minecraft 1.21.1 | NeoForge 21.1.243 | Create 6.0.10 | JDK 21

Run `BUILD.bat` on Windows. It downloads Gradle 8.10 when needed, builds from a temporary local folder to avoid OneDrive file-read failures, runs regression checks, and copies successful JARs to `build/libs`. Keep `build-support/static-resources.zip`.

Crosshair zone inspection is enabled independently of the global overlay. Set `speed_zone_visualisation.show_all_speed_zones = true` in the client config to show all indexed zones in range. Colour represents the default speed panel. Existing boards enter the saved visual index when their chunks load normally.

Compilation and code-level checks have been verified. In-game acceptance testing remains outstanding. See [Alpha 1a release notes](ALPHA_1A_RELEASE_NOTES.md) for changes, limits and verification details. Earlier checkpoint documents are historical.

## Historical 1.4.1 baseline release notes

Create: Signalling Additions 1.4.1

Minecraft 1.21.1 | NeoForge 21.1.243 | Create 6.0.10
Internal mod version: 1.4.1

RELEASE BASIS
Promoted from 1.4.1 Dev 2 following local in-game testing.
No signalling behaviour changes were made during promotion.

1.4.1 CHANGES
- Two-aspect NSW single-colour light signal: Stop (red) and Proceed (green) only, based on the directly linked Create signal.
- Adjusted normal NSW single-colour look-ahead behaviour when the next signal is two-aspect.
- Adjustable height for single- and double-colour fixed-red signals.
- Corrected mainline display handling where chain-passage hold could show Proceed for an occupied linked Create signal.
- Existing signal models, routes, shunt/dead-end features and earlier performance improvements retained.

BUILD
Run BUILD.bat on Windows with JDK 21.
The build downloads Gradle 8.10 if needed.
Keep build-support/static-resources.zip: safeProcessResources requires it.

VALIDATION
Dev 2 was locally tested by the user. This release package has been checked for ZIP integrity and release metadata; it has not been separately compiled or tested in-game.
