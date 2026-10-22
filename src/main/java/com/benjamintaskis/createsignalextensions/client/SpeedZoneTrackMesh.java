package com.benjamintaskis.createsignalextensions.client;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Alpha 1a renderer geometry foundation. Creates sleeper-width translucent strips
 * from sampled track-centreline points; no rail texture or block state is modified.
 * Geometry is independent of the world renderer, allowing the future Create track
 * graph traversal and rendering hook to be tested separately.
 */
public final class SpeedZoneTrackMesh {
    private SpeedZoneTrackMesh() {}
    public record Quad(Vec3 leftStart, Vec3 rightStart, Vec3 rightEnd, Vec3 leftEnd) {}

    /**
     * Build a ribbon from a polyline sampled from the actual Create track geometry.
     * Degenerate segments are ignored. Y is raised slightly to avoid z-fighting.
     * The calling renderer must clip to loaded/rendered sections and apply colour.
     */
    public static List<Quad> build(List<Vec3> samples, double width, double yOffset) {
        List<Quad> result = new ArrayList<>();
        if (samples == null || samples.size() < 2 || width <= 0) return result;
        double half = width / 2.0;
        for (int i = 1; i < samples.size(); i++) {
            Vec3 a = samples.get(i - 1);
            Vec3 b = samples.get(i);
            if (a == null || b == null) continue;
            double dx = b.x - a.x, dz = b.z - a.z;
            double horizontal = Math.hypot(dx, dz);
            if (horizontal < 1.0e-5) continue;
            Vec3 sideways = new Vec3(-dz / horizontal * half, 0, dx / horizontal * half);
            Vec3 lift = new Vec3(0, yOffset, 0);
            result.add(new Quad(a.add(sideways).add(lift), a.subtract(sideways).add(lift),
                    b.subtract(sideways).add(lift), b.add(sideways).add(lift)));
        }
        return result;
    }
}
