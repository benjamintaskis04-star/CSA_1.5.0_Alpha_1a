package com.benjamintaskis.createsignalextensions.client;

import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Conservative client-side geometry traversal for visual speed zones.
 *
 * Starts on an actual Create graph edge and walks only unambiguous continuations.
 * At a switch it stops rather than claiming that the same posted limit applies
 * to every exit. A later zone resolver must supply explicit route choices.
 * This class does not load chunks, modify tracks, or change train behaviour.
 */
public final class SpeedZoneGraphTracer {
    private SpeedZoneGraphTracer() {}

    public record Segment(List<Vec3> centreline, List<SpeedZoneTrackMesh.Quad> quads,
                          double metres, boolean stoppedAtJunction) {}

    /**
     * @param from first node of the oriented edge
     * @param to second node of the oriented edge
     * @param startFraction position on that edge, in the from -> to direction
     * @param maxDistance maximum distance to trace, in Create graph units
     * @param width approximate sleeper-width ribbon (blocks)
     * @param yOffset offset above track geometry (blocks)
     */
    public static Segment trace(TrackGraph graph, TrackNode from, TrackNode to,
                                double startFraction, double maxDistance,
                                double width, double yOffset) {
        List<Vec3> samples = new ArrayList<>();
        if (graph == null || from == null || to == null || maxDistance <= 0 || width <= 0)
            return new Segment(List.of(), List.of(), 0, false);
        if (!Double.isFinite(startFraction) || !Double.isFinite(maxDistance))
            return new Segment(List.of(), List.of(), 0, false);

        startFraction = Math.max(0, Math.min(1, startFraction));
        TrackNode previous = from;
        TrackNode current = to;
        double fraction = startFraction;
        double travelled = 0;
        boolean junction = false;
        Set<TrackNode> visited = new HashSet<>();
        visited.add(previous);

        // Bound traversal even on malformed or cyclic graphs.
        for (int edgeCount = 0; edgeCount < 256 && travelled < maxDistance; edgeCount++) {
            Map<TrackNode, TrackEdge> edges = graph.getConnectionsFrom(previous);
            TrackEdge edge = edges == null ? null : edges.get(current);
            if (edge == null || edge.getLength() <= 1.0e-6) break;
            double remaining = edge.getLength() * (1 - fraction);
            double length = Math.min(maxDistance - travelled, remaining);
            double endFraction = fraction + length / edge.getLength();
            int steps = Math.max(1, Math.min(128, (int)Math.ceil(length * 2)));
            for (int i = 0; i <= steps; i++) {
                double t = fraction + (endFraction - fraction) * i / steps;
                Vec3 point = edge.getPosition(graph, t);
                if (point != null && (samples.isEmpty() || samples.get(samples.size()-1).distanceToSqr(point) > 1.0e-8))
                    samples.add(point);
            }
            travelled += length;
            if (endFraction < 1 - 1.0e-6 || travelled >= maxDistance) break;
            if (!visited.add(current)) break;

            Map<TrackNode, TrackEdge> exits = graph.getConnectionsFrom(current);
            if (exits == null) break;
            TrackNode next = null;
            int count = 0;
            for (TrackNode candidate : exits.keySet()) {
                if (candidate.equals(previous)) continue;
                count++;
                next = candidate;
                if (count > 1) break;
            }
            if (count > 1) { junction = true; break; }
            if (count == 0) break;
            previous = current;
            current = next;
            fraction = 0;
        }
        return new Segment(List.copyOf(samples),
                SpeedZoneTrackMesh.build(samples, width, yOffset), travelled, junction);
    }
}
