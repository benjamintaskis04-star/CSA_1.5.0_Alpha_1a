# Alpha 1a implementation status

**Historical checkpoint.** The current implementation and verified build are described in [Alpha 1a release notes](ALPHA_1A_RELEASE_NOTES.md). The unfinished-work statements below apply only to the earlier snapshot.

This is NOT a complete Alpha 1a release. Do not deploy as a finished version.

Changes made in this pass:
- Deduplicate pending higher-speed rear-clearance boundaries on crossing and on saved-state restoration.
- Add sampled NAV diagnostics (actual speed, target speed, effective/route/upcoming caps) under the existing speedDiagnostics switch.

Previously present: speed colour palette, graph tracer, ribbon geometry generator, directional boundary resolver.

Still not implemented or validated: complete client-side graph-to-board mapping, sleeper tint render stage, global zone visibility and crosshair-zone rendering, verified X20 braking fix, reverse-driving aspect correction.

Compilation against Minecraft/NeoForge/Create has not been performed in this environment. BUILD.bat is included for Windows JDK 21.
