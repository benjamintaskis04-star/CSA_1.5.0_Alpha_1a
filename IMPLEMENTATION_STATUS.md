# CSA 1.5.0 Alpha 1 — persistent speed restrictions

This alpha is scoped to persistence only, per the approved staged roadmap.

## Implemented in source

**Historical Alpha 1 snapshot.** Current Alpha 1a implementation, verified compilation, stale-train cleanup and local acceptance checklist: [Alpha 1a release notes](ALPHA_1A_RELEASE_NOTES.md). Limitations below describe the earlier snapshot.
- SavedData for posted and route speed limits, shunt authority, route serial, and pending higher-speed boundaries.
- Restoration when each train first enters the speed manager after world load.
- Change-detected writes to SavedData, with no empty record for an unrestricted train.
- Defensive loading of malformed saved boundary entries.
- Explicit record-removal API for confirmed permanent train deletion.

## Important limitations
- Permanent train deletion is not yet connected to a verified Create train-deletion callback. Records are retained conservatively rather than erased when a train is merely absent from the current tick.
- No Java compilation or in-game tests were performed in this environment. Run BUILD.bat with JDK 21.
- Alpha 2 through Alpha 7 features are intentionally excluded.

## Local test checklist
1. Build with BUILD.bat and launch a copy of a test world.
2. Cross a lower speed board; save and reload while stationary; verify the restriction remains.
3. Cross a higher speed board with a long train; save while its rear is before the board; reload; verify the increase waits until the rear clears it.
4. Repeat with a stop/reversal near the board, and with trains on different track graphs.
5. Confirm no chunks are force-loaded by the persistence feature.
6. Inspect the log for errors and supply the build log if compilation fails.
