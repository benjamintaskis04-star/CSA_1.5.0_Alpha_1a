# Alpha 1a track-zone renderer — geometry checkpoint

Implemented: `SpeedZoneTrackMesh` provides deterministic translucent ribbon geometry
from sampled 3D track centrelines, retaining curvature and slopes at the sampling
resolution. Existing `SpeedZoneColour` and client config remain unchanged.

**Not yet implemented:** locating complete speed-board-to-speed-board graph zones,
branch/direction handling, sampling Create's edge geometry, actual client render-stage
submission, sleeper-only material masking, or crosshair selection. The helper alone
renders nothing. It must not be represented as a playable visualisation.

Reason for separating: a simplistic straight-line connection between board targets
would miscolour switches and curves, and cannot faithfully reproduce Create's
assembly sleeper tint. Inspect Create 6.0.10's actual rendering and graph APIs before
wiring this into the live renderer.
