# CSA 1.5.0 Alpha 1

**Scope:** Persistent speed restrictions only. Shunting improvements, closest-train priority, reservation logic, configuration overhaul, Low Speed aspects and railway maps are scheduled for later alphas.

Extract this archive and run `BUILD.bat` from the project directory on Windows 11 with JDK 21. The script handles Gradle bootstrap. This is source for a local build attempt, not a precompiled JAR.

**Known limitation:** No confirmed Create train-deletion hook has been wired to the saved-state cleanup API; records for permanently deleted trains may remain until that integration is added. We intentionally avoid deleting records merely because a train is temporarily missing from a tick.

See `IMPLEMENTATION_STATUS.md` for local tests and verification status.
