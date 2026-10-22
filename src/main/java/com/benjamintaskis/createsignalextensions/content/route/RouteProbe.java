package com.benjamintaskis.createsignalextensions.content.route;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import com.benjamintaskis.createsignalextensions.config.CommonConfig;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Navigation;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.SignalBlock.SignalType;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 0.1.1 Alpha 8 shared-boundary-node route detection.
 *
 * Alpha 5 showed that Create's planned path does not contain the signal
 * boundary edge itself. Instead, the first route edge begins at one endpoint
 * of the SignalBoundary and continues along the selected route. Alpha 8 uses
 * that relationship directly while keeping the navigation access read-only.
 */
public final class RouteProbe {
    private static final double DEFAULT_AUTOMATIC_ROUTE_LOOKAHEAD = 128.0;
    private static final double ROUTE_DECISION_TAIL = 32.0;
    private static Field currentPathField;
    private static boolean currentPathLookupAttempted;
    private static final Map<String, String> a7LoggedPathStates = new ConcurrentHashMap<>();

    private RouteProbe() {}

    @SuppressWarnings("unchecked")
    private static List<Couple<TrackNode>> getCurrentPath(Navigation navigation) {
        if (navigation == null)
            return null;

        try {
            if (!currentPathLookupAttempted) {
                currentPathLookupAttempted = true;
                currentPathField = Navigation.class.getDeclaredField("currentPath");
                currentPathField.setAccessible(true);
            }
            if (currentPathField == null)
                return null;

            Object value = currentPathField.get(navigation);
            return value instanceof List<?> ? (List<Couple<TrackNode>>) value : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            currentPathField = null;
            CreateSignallingAdditions.LOGGER.warn("Could not read Create Navigation.currentPath", e);
            return null;
        }
    }

    public record PathPoint(double x, double y, double z) {}

    public record Result(boolean resolved, String summary, String trainName, String routeKey, String destinationName, String routeType, List<PathPoint> visualPath) {
        public Result {
            visualPath = visualPath == null ? List.of() : List.copyOf(visualPath);
        }

        public static Result unresolved(String summary) {
            return new Result(false, summary, "", "", "", "UNKNOWN", List.of());
        }
    }

    public static Result probe(SignalBoundary boundary, BlockPos linkedSignalPos) {
        return probe(boundary, linkedSignalPos, DEFAULT_AUTOMATIC_ROUTE_LOOKAHEAD);
    }

    public static Result probe(SignalBoundary boundary, BlockPos linkedSignalPos, double automaticRouteLookahead) {
        automaticRouteLookahead = Math.max(16.0, Math.min(256.0, automaticRouteLookahead));
        if (boundary == null)
            return Result.unresolved("DIAG boundary=MISSING");
        if (boundary.edgeLocation == null)
            return Result.unresolved("DIAG boundary=OK edge=MISSING");

        TrackGraph graph = findOwningGraph(boundary);
        Boolean signalSide = getSignalSide(boundary, linkedSignalPos);
        String boundaryEdge = describeEdge(boundary.edgeLocation);

        debug(
                "Route probe B5: signal={} boundary={} graphFound={} signalSide={} edge={}",
                linkedSignalPos, boundary.getId(), graph != null, signalSide, boundaryEdge);

        if (graph == null)
            return Result.unresolved("DIAG boundary=OK graph=NO edge=" + boundaryEdge);
        if (signalSide == null)
            return Result.unresolved("DIAG graph=OK side=UNKNOWN edge=" + boundaryEdge);

        int sameGraph = 0;
        int active = 0;
        int liveReadable = 0;
        int liveEdges = 0;
        int liveShared = 0;
        int waitingHere = 0;
        String boundaryTrainName = "";
        String boundaryDestination = "";
        double boundaryTrainDistance = Double.MAX_VALUE;
        Result bestResolved = null;
        double bestResolvedDistance = Double.MAX_VALUE;
        Vec3 boundaryMidpoint = boundaryMidpoint(boundary.edgeLocation);

        for (Train train : Create.RAILWAYS.trains.values()) {
            if (train.graph != graph || train.navigation == null)
                continue;

            sameGraph++;
            Navigation nav = train.navigation;
            boolean navActive = nav.isActive();
            if (navActive)
                active++;

            String trainName = train.name == null ? train.id.toString() : train.name.getString();
            String destinationName = nav.destination == null ? "NONE" : nav.destination.name;

            // Alpha 6: manual-driving fallback. Create exposes the driver's current
            // A/D steering request directly as Train.manualSteer. Reproduce the same
            // dot-product choice used by TravellingPoint.steer(), but against the
            // outgoing edges at this signal boundary. This predicts the selected
            // junction route before the train physically enters the turnout.
            if (train.manualSteer != TravellingPoint.SteerDirection.NONE) {
                Result manual = resolveManualSteer(train, graph, boundary, signalSide, destinationName);
                if (manual != null) {
                    double distance = nearestTrainPointDistance(train, graph, boundaryMidpoint);
                    if (distance < bestResolvedDistance) {
                        bestResolved = manual;
                        bestResolvedDistance = distance;
                    }
                }
            }

            boolean waitsHere = nav.waitingForSignal != null
                    && boundary.getId().equals(nav.waitingForSignal.getFirst())
                    && nav.waitingForSignal.getSecond() == signalSide;
            if (waitsHere)
                waitingHere++;

            List<Couple<TrackNode>> livePath = getCurrentPath(nav);
            if (!waitsHere && !pathMatchesSide(boundary, signalSide, livePath)) continue;
            if (livePath != null) {
                liveReadable++;
                liveEdges += livePath.size();
            }

            SharedMatch liveMatch = findOutgoingSharedNodeMatch(livePath, boundary.edgeLocation);
            if (pathContainsBoundaryEdge(livePath, boundary.edgeLocation) &&
                    navActive && nav.destination != null) {
                double distance = nearestTrainPointDistance(train, graph, boundaryMidpoint);
                if (distance < boundaryTrainDistance) {
                    boundaryTrainDistance = distance;
                    boundaryTrainName = trainName;
                    boundaryDestination = destinationName;
                }
            }
            liveShared += liveMatch.matches;

            debug("Route probe train: name='{}' active={} destination='{}' waitingHere={} currentPath={} liveShared={} liveIndex={} source=CURRENT_PATH",
                    trainName, navActive, destinationName, waitsHere,
                    sizeOf(livePath), liveMatch.matches, liveMatch.firstIndex);

            // Runtime path fallback. This is useful while the train is already
            // traversing the junction and also lets us compare both sources.
            Result resolvedLive = navActive ? resolveA8Route(train, graph, boundary.edgeLocation, livePath, liveMatch, waitsHere ? "WAITING" : "CURRENT_PATH", destinationName, automaticRouteLookahead) : null;
            if (resolvedLive != null) {
                double distance = nearestTrainPointDistance(train, graph, boundaryMidpoint);
                if (distance < bestResolvedDistance) {
                    bestResolved = resolvedLive;
                    bestResolvedDistance = distance;
                }
            }
        }

        String summary = "DIAG trains=" + sameGraph
                + " active=" + active
                + " livePaths=" + liveReadable
                + " liveEdges=" + liveEdges
                + " liveShared=" + liveShared
                + " repaths=DISABLED"
                + " waiting=" + waitingHere;

        // A train whose planned path contains the signal boundary is still a valid
        // candidate for destination rules even when Create compresses the path so
        // much that no outgoing shared-node route edge can be resolved. Compare it
        // with fully-resolved candidates by proximity instead of allowing a farther
        // train to overwrite the nearer train's destination indication.
        if (!boundaryDestination.isBlank() && boundaryTrainDistance < bestResolvedDistance) {
            debug("Route probe B5 selected nearest boundary-path train '{}' at {} blocks",
                    boundaryTrainName, String.format("%.2f", boundaryTrainDistance));
            return new Result(false, summary + " boundaryPath=YES nearest=YES", boundaryTrainName, "", boundaryDestination, "UNKNOWN", List.of());
        }
        if (bestResolved != null) {
            debug("Route probe B5 selected nearest resolved train '{}' at {} blocks from linked signal",
                    bestResolved.trainName(), String.format("%.2f", bestResolvedDistance));
            return bestResolved;
        }

        debug("Route probe B5 unresolved: {}", summary);
        if (!boundaryDestination.isBlank())
            return new Result(false, summary + " boundaryPath=YES", boundaryTrainName, "", boundaryDestination, "UNKNOWN", List.of());
        return Result.unresolved(summary);
    }

    /**
     * Find a path edge which STARTS at either endpoint of the linked signal
     * boundary but does not simply travel along the boundary edge itself.
     *
     * Alpha 5's test geometry showed exactly this shape:
     * boundary: 64 <-> 79
     * route A:  79 -> 80
     * route B:  79 -> 88.5,-43
     *
     * Because Create's path couples are traversal-ordered, requiring the
     * shared boundary node to be pair.getFirst() selects the outgoing route
     * rather than an edge approaching the boundary from the other direction.
     */
    private record SharedMatch(int firstIndex, int matches, TrackNodeLocation sharedNode) {}

    private static boolean pathMatchesSide(SignalBoundary boundary, boolean side, List<Couple<TrackNode>> path) {
        if (path == null) return false;
        int index = findBoundaryEdgeIndex(path, boundary.edgeLocation);
        if (index >= 0) return (!boundary.isPrimary(path.get(index).getFirst())) == side;
        SharedMatch match = findOutgoingSharedNodeMatch(path, boundary.edgeLocation);
        if (match.firstIndex < 0) return false;
        // Compressed paths start at the exit endpoint. Its opposite endpoint
        // is the approach node used by Create to identify this signal side.
        TrackNodeLocation approach = match.sharedNode.equals(boundary.edgeLocation.getFirst())
                ? boundary.edgeLocation.getSecond() : boundary.edgeLocation.getFirst();
        return (!approach.equals(boundary.edgeLocation.getSecond())) == side;
    }

    private static boolean pathContainsBoundaryEdge(List<Couple<TrackNode>> path, Couple<TrackNodeLocation> boundaryEdge) {
        if (path == null || boundaryEdge == null)
            return false;
        TrackNodeLocation a = boundaryEdge.getFirst();
        TrackNodeLocation b = boundaryEdge.getSecond();
        for (Couple<TrackNode> pair : path) {
            TrackNodeLocation from = pair.getFirst().getLocation();
            TrackNodeLocation to = pair.getSecond().getLocation();
            if ((from.equals(a) && to.equals(b)) || (from.equals(b) && to.equals(a)))
                return true;
        }
        return false;
    }

    /** Alpha 1: follow the active train path beyond this signal and return the first linked Create signal on it. */
    public static BlockPos findNextLinkedSignal(SignalBoundary currentBoundary, BlockPos currentSignalPos,
                                                 List<BlockPos> candidates, net.minecraft.server.level.ServerLevel level) {
        if (currentBoundary == null || currentBoundary.edgeLocation == null || candidates == null) return null;
        TrackGraph graph = findOwningGraph(currentBoundary);
        if (graph == null) return null;
        Boolean currentSide = getSignalSide(currentBoundary, currentSignalPos);
        if (currentSide == null) return null;
        int bestIndex = Integer.MAX_VALUE;
        BlockPos best = null;
        for (Train train : Create.RAILWAYS.trains.values()) {
            if (train.graph != graph || train.navigation == null || !train.navigation.isActive()) continue;
            List<Couple<TrackNode>> path = getCurrentPath(train.navigation);
            if (!pathMatchesSide(currentBoundary, currentSide, path)) continue;
            int currentIndex = findBoundaryEdgeIndex(path, currentBoundary.edgeLocation);
            if (currentIndex < 0) {
                SharedMatch shared = findOutgoingSharedNodeMatch(path, currentBoundary.edgeLocation);
                currentIndex = shared.firstIndex - 1;
            }
            if (currentIndex < -1) continue;
            for (BlockPos candidate : candidates) {
                if (candidate == null || candidate.equals(currentSignalPos) || !level.hasChunkAt(candidate)) continue;
                if (!(level.getBlockEntity(candidate) instanceof com.simibubi.create.content.trains.signal.SignalBlockEntity signal)) continue;
                SignalBoundary boundary = signal.getSignal();
                if (boundary == null || boundary.edgeLocation == null) continue;
                Boolean candidateSide = getSignalSide(boundary, candidate);
                if (candidateSide == null || !pathMatchesSide(boundary, candidateSide, path)) continue;
                int idx = findBoundaryEdgeIndex(path, boundary.edgeLocation);
                // Create commonly omits the signal boundary edge itself from Navigation.currentPath.
                // Match the first route edge leaving either endpoint as the signal's position on the
                // active route, exactly as we already do for the protecting signal above. This lets
                // a straight route through a turnout select the linked NSW signal actually lying on
                // that route instead of returning MISSING when several following heads are linked.
                if (idx < 0) {
                    SharedMatch candidateShared = findOutgoingSharedNodeMatch(path, boundary.edgeLocation);
                    if (candidateShared.firstIndex >= 0) idx = candidateShared.firstIndex - 1;
                }
                if (idx > currentIndex && idx < bestIndex) { bestIndex = idx; best = candidate.immutable(); }
            }
        }
        return best;
    }

    /**
     * 0.1.3a passive chain look-ahead target.
     *
     * The target is the next Create signal encountered on the least-deviating
     * (STRAIGHT) continuation from the supplied signal.  The returned side is
     * the side facing the direction of travel, allowing callers to distinguish
     * CROSS/chain signals from terminal ENTRY signals without relying on the
     * chain signal's aggregate yellow/green state.
     */
    public record StraightSignalTarget(SignalBoundary boundary, BlockPos signalPos, boolean side, boolean chain) {}

    public static StraightSignalTarget findNextStraightSignal(
            SignalBoundary currentBoundary, BlockPos currentSignalPos,
            net.minecraft.server.level.ServerLevel level) {
        if (currentBoundary == null || currentBoundary.edgeLocation == null || currentSignalPos == null)
            return null;

        TrackGraph graph = findOwningGraph(currentBoundary);
        if (graph == null)
            return null;

        Boolean currentSide = getSignalSide(currentBoundary, currentSignalPos);
        if (currentSide == null)
            return null;

        Couple<TrackNode> startNodes = currentBoundary.edgeLocation.map(graph::locateNode);
        TrackNode node1 = startNodes.get(currentSide);
        TrackNode node2 = startNodes.get(!currentSide);
        if (node1 == null || node2 == null)
            return null;

        TrackEdge startEdge = graph.getConnectionsFrom(node1).get(node2);
        if (startEdge == null)
            return null;

        // First check the remainder of the same directed edge.
        SignalBoundary immediate = startEdge.getEdgeData()
                .next(EdgePointType.SIGNAL, currentBoundary.getLocationOn(startEdge));
        if (immediate != null && !immediate.getId().equals(currentBoundary.getId())) {
            StraightSignalTarget target = targetFromApproach(immediate, node1);
            if (target != null)
                return target;
        }

        TrackNode previous = node1;
        TrackNode current = node2;
        TrackEdge incoming = startEdge;
        java.util.HashSet<String> visited = new java.util.HashSet<>();

        for (int guard = 0; guard < 256; guard++) {
            String visitKey = previous.getLocation().toString() + "->" + current.getLocation().toString();
            if (!visited.add(visitKey))
                return null;

            Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(current);
            if (connections == null || connections.isEmpty())
                return null;

            List<Map.Entry<TrackNode, TrackEdge>> outgoing = new ArrayList<>();
            for (Map.Entry<TrackNode, TrackEdge> entry : connections.entrySet()) {
                if (sameNode(entry.getKey(), previous))
                    continue;
                if (incoming.canTravelTo(entry.getValue()))
                    outgoing.add(entry);
            }
            if (outgoing.isEmpty())
                return null;

            Map.Entry<TrackNode, TrackEdge> selected = null;
            if (outgoing.size() == 1) {
                selected = outgoing.get(0);
            } else {
                // At a turnout, passive look-ahead deliberately follows the
                // straight/default continuation.  An active train path is handled
                // separately by SignalAspectLinkManager and can override this.
                for (Map.Entry<TrackNode, TrackEdge> entry : outgoing) {
                    if ("STRAIGHT".equals(classifyAtJunction(graph, previous, current, entry.getKey()))) {
                        selected = entry;
                        break;
                    }
                }
                if (selected == null)
                    return null;
            }

            TrackNode nextNode = selected.getKey();
            TrackEdge edge = selected.getValue();
            SignalBoundary boundary = edge.getEdgeData().next(EdgePointType.SIGNAL, 0);
            if (boundary != null) {
                StraightSignalTarget target = targetFromApproach(boundary, current);
                if (target != null)
                    return target;
            }

            previous = current;
            current = nextNode;
            incoming = edge;
        }
        return null;
    }

    private static StraightSignalTarget targetFromApproach(SignalBoundary boundary, TrackNode approachNode) {
        if (boundary == null || approachNode == null)
            return null;
        boolean side = !boundary.isPrimary(approachNode);
        Map<BlockPos, Boolean> blocks = boundary.blockEntities.get(side);
        if (blocks == null || blocks.isEmpty())
            return null;
        BlockPos pos = blocks.keySet().iterator().next().immutable();
        boolean chain = boundary.types.get(side) == SignalType.CROSS_SIGNAL;
        return new StraightSignalTarget(boundary, pos, side, chain);
    }

    /**
     * Alpha 2f: choose the following linked Create signal from static track topology.
     *
     * This deliberately does not depend on Navigation.currentPath. Starting at either
     * end of the protecting signal boundary, it walks through plain track until the
     * first real routing decision, classifies that decision as STRAIGHT/LEFT/RIGHT
     * using the same geometry as the Route Interface, and then searches that branch
     * for the nearest linked following signal.
     *
     * Trying both boundary orientations avoids guessing Create's internal signal-side
     * endpoint convention. In a normal following-signal setup the correct direction
     * reaches one of the explicitly linked candidates first; the reverse direction
     * either reaches none or only reaches one after a much longer loop.
     */
    public static BlockPos findNextLinkedSignalByTopology(
            SignalBoundary currentBoundary, BlockPos currentSignalPos, List<BlockPos> candidates,
            String desiredRouteType, net.minecraft.server.level.ServerLevel level) {
        if (currentBoundary == null || currentBoundary.edgeLocation == null || candidates == null || candidates.isEmpty())
            return null;

        String desired = desiredRouteType == null ? "UNKNOWN" : desiredRouteType.toUpperCase(java.util.Locale.ROOT);
        if (!desired.equals("STRAIGHT") && !desired.equals("DIVERGING LEFT") && !desired.equals("DIVERGING RIGHT"))
            return null;

        TrackGraph graph = findOwningGraph(currentBoundary);
        if (graph == null)
            return null;

        Map<BlockPos, Couple<TrackNodeLocation>> candidateEdges = new HashMap<>();
        for (BlockPos candidate : candidates) {
            if (candidate == null || candidate.equals(currentSignalPos) || !level.hasChunkAt(candidate))
                continue;
            if (!(level.getBlockEntity(candidate) instanceof com.simibubi.create.content.trains.signal.SignalBlockEntity signal))
                continue;
            SignalBoundary boundary = signal.getSignal();
            if (boundary != null && boundary.edgeLocation != null)
                candidateEdges.put(candidate.immutable(), boundary.edgeLocation);
        }
        if (candidateEdges.isEmpty())
            return null;

        TrackNode a = graph.locateNode(currentBoundary.edgeLocation.getFirst());
        TrackNode b = graph.locateNode(currentBoundary.edgeLocation.getSecond());
        if (a == null || b == null)
            return null;

        TopologyCandidate best = null;
        TopologyCandidate fromA = findTopologyCandidate(graph, a, b, candidateEdges, desired, 1024.0);
        TopologyCandidate fromB = findTopologyCandidate(graph, b, a, candidateEdges, desired, 1024.0);
        if (fromA != null) best = fromA;
        if (fromB != null && (best == null || fromB.distance < best.distance)) best = fromB;

        if (best != null) {
            debug(
                    "Following topology A2f: signal={} desired={} selected={} distance={}m",
                    currentSignalPos, desired, best.pos, String.format("%.1f", best.distance));
            return best.pos;
        }
        return null;
    }

    private record TopologyCandidate(BlockPos pos, double distance) {}
    private record TopologyState(TrackNode previous, TrackNode current, TrackEdge incomingEdge, double distance) {}
    private record TopologyVisit(TrackNodeLocation previous, TrackNodeLocation current) {}

    private static TopologyCandidate findTopologyCandidate(
            TrackGraph graph, TrackNode previous, TrackNode current,
            Map<BlockPos, Couple<TrackNodeLocation>> candidateEdges,
            String desiredRouteType, double maxDistance) {
        TrackEdge incoming = findEdgeTo(graph.getConnectionsFrom(previous), current);
        if (incoming == null)
            return null;

        double travelled = 0.0;
        TrackNode prev = previous;
        TrackNode at = current;
        TrackEdge in = incoming;

        // First walk through any unbranched track. A candidate encountered before
        // a turnout is unambiguously the next signal for every route.
        for (int guard = 0; guard < 256 && travelled <= maxDistance; guard++) {
            Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(at);
            if (connections == null || connections.isEmpty())
                return null;

            List<Map.Entry<TrackNode, TrackEdge>> outgoing = new ArrayList<>();
            for (Map.Entry<TrackNode, TrackEdge> entry : connections.entrySet()) {
                if (sameNode(entry.getKey(), prev))
                    continue;
                if (in.canTravelTo(entry.getValue()))
                    outgoing.add(entry);
            }
            if (outgoing.isEmpty())
                return null;

            if (outgoing.size() == 1) {
                Map.Entry<TrackNode, TrackEdge> only = outgoing.get(0);
                TopologyCandidate direct = candidateOnEdge(candidateEdges, at, only.getKey(), travelled + only.getValue().getLength());
                if (direct != null)
                    return direct;
                travelled += only.getValue().getLength();
                prev = at;
                at = only.getKey();
                in = only.getValue();
                continue;
            }

            // This is the first actual routing decision beyond the protecting signal.
            TopologyCandidate best = null;
            for (Map.Entry<TrackNode, TrackEdge> entry : outgoing) {
                String branchType = classifyAtJunction(graph, prev, at, entry.getKey());
                if (!desiredRouteType.equals(branchType))
                    continue;

                TopologyCandidate onFirst = candidateOnEdge(candidateEdges, at, entry.getKey(), travelled + entry.getValue().getLength());
                if (onFirst != null && (best == null || onFirst.distance < best.distance))
                    best = onFirst;

                TopologyCandidate downstream = searchBranchForCandidate(
                        graph, at, entry.getKey(), entry.getValue(), candidateEdges,
                        travelled + entry.getValue().getLength(), maxDistance);
                if (downstream != null && (best == null || downstream.distance < best.distance))
                    best = downstream;
            }
            return best;
        }
        return null;
    }

    private static TopologyCandidate searchBranchForCandidate(
            TrackGraph graph, TrackNode previous, TrackNode current, TrackEdge incoming,
            Map<BlockPos, Couple<TrackNodeLocation>> candidateEdges,
            double startingDistance, double maxDistance) {
        PriorityQueue<TopologyState> queue = new PriorityQueue<>(Comparator.comparingDouble(TopologyState::distance));
        Map<TopologyVisit, Double> bestDistance = new HashMap<>();
        TopologyState first = new TopologyState(previous, current, incoming, startingDistance);
        queue.add(first);
        bestDistance.put(new TopologyVisit(previous.getLocation(), current.getLocation()), startingDistance);

        while (!queue.isEmpty()) {
            TopologyState state = queue.poll();
            if (state.distance > maxDistance)
                continue;

            TopologyVisit visit = new TopologyVisit(state.previous.getLocation(), state.current.getLocation());
            if (state.distance > bestDistance.getOrDefault(visit, Double.MAX_VALUE) + 1.0e-6)
                continue;

            Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(state.current);
            if (connections == null)
                continue;
            for (Map.Entry<TrackNode, TrackEdge> entry : connections.entrySet()) {
                TrackNode target = entry.getKey();
                TrackEdge edge = entry.getValue();
                if (sameNode(target, state.previous))
                    continue;
                if (!state.incomingEdge.canTravelTo(edge))
                    continue;

                double distance = state.distance + edge.getLength();
                if (distance > maxDistance)
                    continue;

                TopologyCandidate candidate = candidateOnEdge(candidateEdges, state.current, target, distance);
                if (candidate != null)
                    return candidate;

                TopologyVisit nextVisit = new TopologyVisit(state.current.getLocation(), target.getLocation());
                if (distance + 1.0e-6 >= bestDistance.getOrDefault(nextVisit, Double.MAX_VALUE))
                    continue;
                bestDistance.put(nextVisit, distance);
                queue.add(new TopologyState(state.current, target, edge, distance));
            }
        }
        return null;
    }

    private static TopologyCandidate candidateOnEdge(
            Map<BlockPos, Couple<TrackNodeLocation>> candidates,
            TrackNode from, TrackNode to, double distance) {
        TrackNodeLocation a = from.getLocation();
        TrackNodeLocation b = to.getLocation();
        for (Map.Entry<BlockPos, Couple<TrackNodeLocation>> entry : candidates.entrySet()) {
            Couple<TrackNodeLocation> edge = entry.getValue();
            if (edge == null)
                continue;
            TrackNodeLocation x = edge.getFirst();
            TrackNodeLocation y = edge.getSecond();
            if ((a.equals(x) && b.equals(y)) || (a.equals(y) && b.equals(x)))
                return new TopologyCandidate(entry.getKey(), distance);
        }
        return null;
    }

    private static int findBoundaryEdgeIndex(List<Couple<TrackNode>> path, Couple<TrackNodeLocation> boundaryEdge) {
        if (path == null || boundaryEdge == null)
            return -1;
        TrackNodeLocation a = boundaryEdge.getFirst();
        TrackNodeLocation b = boundaryEdge.getSecond();
        for (int i = 0; i < path.size(); i++) {
            Couple<TrackNode> pair = path.get(i);
            TrackNodeLocation from = pair.getFirst().getLocation();
            TrackNodeLocation to = pair.getSecond().getLocation();
            if ((from.equals(a) && to.equals(b)) || (from.equals(b) && to.equals(a)))
                return i;
        }
        return -1;
    }


    private static SharedMatch findOutgoingSharedNodeMatch(
            List<Couple<TrackNode>> path, Couple<TrackNodeLocation> boundaryEdge) {
        if (path == null || path.isEmpty())
            return new SharedMatch(-1, 0, null);

        TrackNodeLocation boundaryA = boundaryEdge.getFirst();
        TrackNodeLocation boundaryB = boundaryEdge.getSecond();
        int first = -1;
        int matches = 0;
        TrackNodeLocation firstShared = null;

        for (int i = 0; i < path.size(); i++) {
            Couple<TrackNode> pair = path.get(i);
            TrackNodeLocation from = pair.getFirst().getLocation();
            TrackNodeLocation to = pair.getSecond().getLocation();

            boolean startsAtA = from.equals(boundaryA);
            boolean startsAtB = from.equals(boundaryB);
            if (!startsAtA && !startsAtB)
                continue;

            // Ignore the boundary edge itself if it ever appears in a future
            // Create version. We want the selected edge beyond the signal.
            if ((startsAtA && to.equals(boundaryB)) || (startsAtB && to.equals(boundaryA)))
                continue;

            matches++;
            if (first < 0) {
                first = i;
                firstShared = from;
            }
        }

        return new SharedMatch(first, matches, firstShared);
    }

    /**
     * Beta 5d / A8 route resolution.
     *
     * A route is now identified by the sequence of downstream routing decisions,
     * not by only the first branch after the linked signal. This matters where two
     * routes share an initial turnout and only separate at a later junction (for
     * example DaM P2 versus P3).
     *
     * We anchor the scan at the linked signal boundary/shared path entry, then
     * collect every later path edge that can be proven to be a selected
     * continuation at a genuine junction. The ordered set of those branch edges
     * forms the route signature.
     *
     * If Create's compressed path does not expose enough graph topology to prove
     * any junction, we retain Beta 5a's first-downstream-edge fallback.
     */
    private static Result resolveA8Route(
            Train train, TrackGraph graph, Couple<TrackNodeLocation> boundaryEdge,
            List<Couple<TrackNode>> path, SharedMatch match, String mode, String destinationName,
            double automaticRouteLookahead) {
        if (path == null || path.isEmpty())
            return null;

        int boundaryIndex = findBoundaryEdgeIndex(path, boundaryEdge);
        int anchorIndex = boundaryIndex >= 0 ? boundaryIndex : match.firstIndex;
        if (anchorIndex < 0)
            return null;

        int scanStart = boundaryIndex >= 0 ? boundaryIndex + 1 : anchorIndex;
        List<Couple<TrackNode>> decisions = new ArrayList<>();
        int firstDecisionIndex = -1;

        double downstreamDistance = 0.0;
        double lastDecisionDistance = -1.0;
        for (int i = scanStart; i < path.size(); i++) {
            Couple<TrackNode> candidate = path.get(i);
            if (isBoundaryPair(candidate, boundaryEdge))
                continue;

            // Beta 6a: each Route Interface supplies its own hard look-ahead ceiling.
            // Once a routing decision has been found, stop after 32 blocks without
            // another decision. This keeps one local interlocking from learning
            // unrelated junctions farther down the railway.
            if (downstreamDistance > automaticRouteLookahead)
                break;
            if (lastDecisionDistance >= 0.0 && downstreamDistance - lastDecisionDistance > ROUTE_DECISION_TAIL)
                break;

            if (isRouteDefiningBranch(graph, path, i)) {
                if (firstDecisionIndex < 0)
                    firstDecisionIndex = i;
                decisions.add(candidate);
                lastDecisionDistance = downstreamDistance;
            }

            downstreamDistance += pathEdgeLength(graph, candidate);
        }

        // Beta 7: visual geometry has the same hard ceiling as route probing.
        // It is clipped exactly at the configured distance and can never continue past the LIMIT marker.
        List<PathPoint> visualPath = buildVisualPath(graph, path, scanStart, boundaryEdge, automaticRouteLookahead);

        if (!decisions.isEmpty()) {
            String trainName = train.name == null ? train.id.toString() : train.name.getString();
            String routeType = classifyPathBranch(graph, path, firstDecisionIndex);
            String routeKey = describeRouteSignature(decisions);
            debug(
                    "Route probe B6a A8 SIGNATURE via {}: train='{}' anchorIndex={} decisions={} lookahead={} route={} type={}",
                    mode, trainName, anchorIndex, decisions.size(), String.format("%.0f", automaticRouteLookahead), routeKey, routeType);
            return resolvedWithKey(train, routeKey, mode + "_A8_SIGNATURE", destinationName, routeType, visualPath);
        }

        // Compressed-path compatibility fallback from Beta 5a. If graph topology
        // cannot prove any routing decision, the first entry after the anchor is
        // still preferable to falling back immediately to the common approach.
        int downstreamIndex = anchorIndex + 1;
        if (downstreamIndex >= 0 && downstreamIndex < path.size()) {
            Couple<TrackNode> downstream = path.get(downstreamIndex);
            if (!isBoundaryPair(downstream, boundaryEdge)) {
                String trainName = train.name == null ? train.id.toString() : train.name.getString();
                debug(
                        "Route probe B5d A8 DOWNSTREAM FALLBACK via {}: train='{}' anchorIndex={} pathIndex={} route={}",
                        mode, trainName, anchorIndex, downstreamIndex, describePair(downstream));
                return resolvedWithKey(train, describePair(downstream), mode + "_A8_DOWNSTREAM", destinationName,
                        classifyPathBranch(graph, path, downstreamIndex), visualPath);
            }
        }

        // Final compatibility fallback for simple terminal routes.
        if (match.firstIndex >= 0 && match.firstIndex < path.size()) {
            Couple<TrackNode> fallback = path.get(match.firstIndex);
            String trainName = train.name == null ? train.id.toString() : train.name.getString();
            debug(
                    "Route probe B5d A8 FINAL FALLBACK via {}: train='{}' pathIndex={} route={}",
                    mode, trainName, match.firstIndex, describePair(fallback));
            return resolvedWithKey(train, describePair(fallback), mode + "_A7_FALLBACK", destinationName,
                    classifyRoute(graph, boundaryEdge, fallback), visualPath);
        }

        return null;
    }

    /**
     * Beta 6: retain the continuous downstream geometry separately from the route
     * signature. The signature still identifies a route; this path is only for the
     * graphical Route Map and therefore cannot create additional route variants.
     */
    private static List<PathPoint> buildVisualPath(TrackGraph graph, List<Couple<TrackNode>> path,
                                                   int startIndex, Couple<TrackNodeLocation> boundaryEdge,
                                                   double maxDistance) {
        if (path == null || path.isEmpty() || startIndex < 0 || startIndex >= path.size())
            return List.of();

        List<PathPoint> points = new ArrayList<>();
        double distance = 0.0;
        for (int i = startIndex; i < path.size() && points.size() < 256; i++) {
            Couple<TrackNode> pair = path.get(i);
            if (isBoundaryPair(pair, boundaryEdge))
                continue;
            if (distance >= maxDistance)
                break;

            TrackNode fromNode = graph == null ? null : graph.locateNode(pair.getFirst().getLocation());
            TrackNode toNode = graph == null ? null : graph.locateNode(pair.getSecond().getLocation());
            TrackEdge edge = fromNode == null || toNode == null ? null : findEdgeTo(graph.getConnectionsFrom(fromNode), toNode);
            double length = edge != null ? edge.getLength() : pathEdgeLength(graph, pair);
            double remaining = Math.max(0.0, maxDistance - distance);
            double maxF = length <= 1.0e-6 ? 1.0 : Math.min(1.0, remaining / length);

            // Beta 6a: sample Create's own TrackEdge geometry. Curved/Bezier track
            // therefore appears as a curve on the Route Map instead of a long chord
            // between graph nodes. Straight edges still need only their endpoints.
            if (edge != null) {
                int samples = edge.isTurn() ? Math.max(2, Math.min(32, edge.getTurn().getSegmentCount())) : 1;
                int endSample = Math.max(1, (int)Math.ceil(samples * maxF));
                for (int sample = 0; sample <= endSample && points.size() < 256; sample++) {
                    double f = Math.min(maxF, sample / (double)samples);
                    Vec3 pos = edge.getPosition(graph, f);
                    addVisualPoint(points, pos);
                    if (f >= maxF - 1.0e-6)
                        break;
                }
            } else {
                Vec3 from = pair.getFirst().getLocation().getLocation();
                Vec3 to = pair.getSecond().getLocation().getLocation();
                addVisualPoint(points, from);
                Vec3 clipped = from.lerp(to, maxF);
                addVisualPoint(points, clipped);
            }

            distance += length * maxF;
            if (maxF < 1.0)
                break;
        }
        return List.copyOf(points);
    }

    private static void addVisualPoint(List<PathPoint> points, Vec3 point) {
        if (point == null)
            return;
        if (!points.isEmpty()) {
            PathPoint last = points.get(points.size() - 1);
            if (Math.abs(last.x - point.x) <= 1.0e-4 && Math.abs(last.y - point.y) <= 1.0e-4 && Math.abs(last.z - point.z) <= 1.0e-4)
                return;
        }
        points.add(new PathPoint(point.x, point.y, point.z));
    }

    private static double pathEdgeLength(TrackGraph graph, Couple<TrackNode> pair) {
        if (graph == null || pair == null)
            return 0.0;
        TrackNode from = graph.locateNode(pair.getFirst().getLocation());
        TrackNode to = graph.locateNode(pair.getSecond().getLocation());
        if (from != null && to != null) {
            TrackEdge edge = findEdgeTo(graph.getConnectionsFrom(from), to);
            if (edge != null)
                return edge.getLength();
        }

        // DiscoveredPath can occasionally be compressed. If a direct graph edge
        // cannot be recovered, use endpoint distance as a conservative fallback.
        Vec3 a = pair.getFirst().getLocation().getLocation();
        Vec3 b = pair.getSecond().getLocation().getLocation();
        return a.distanceTo(b);
    }

    private static String describeRouteSignature(List<Couple<TrackNode>> decisions) {
        StringBuilder out = new StringBuilder();
        for (Couple<TrackNode> decision : decisions) {
            if (out.length() > 0)
                out.append(" | ");
            out.append(describePair(decision));
        }
        return out.toString();
    }

    private static boolean isBoundaryPair(Couple<TrackNode> pair, Couple<TrackNodeLocation> boundaryEdge) {
        if (pair == null || boundaryEdge == null)
            return false;
        TrackNodeLocation from = pair.getFirst().getLocation();
        TrackNodeLocation to = pair.getSecond().getLocation();
        TrackNodeLocation a = boundaryEdge.getFirst();
        TrackNodeLocation b = boundaryEdge.getSecond();
        return (from.equals(a) && to.equals(b)) || (from.equals(b) && to.equals(a));
    }

    /**
     * Returns true when path[index] is a selected continuation at a junction where
     * the approach permits at least two different onward edges. When consecutive
     * path entries are contiguous we use the known previous edge as the approach.
     * For Create's compressed paths we infer a compatible approach from the graph.
     */
    private static boolean isRouteDefiningBranch(TrackGraph graph, List<Couple<TrackNode>> path, int index) {
        if (graph == null || path == null || index < 0 || index >= path.size())
            return false;

        Couple<TrackNode> selectedPair = path.get(index);
        TrackNode junction = graph.locateNode(selectedPair.getFirst().getLocation());
        TrackNode selectedTarget = graph.locateNode(selectedPair.getSecond().getLocation());
        if (junction == null || selectedTarget == null)
            return false;

        Map<TrackNode, TrackEdge> atJunction = graph.getConnectionsFrom(junction);
        TrackEdge selectedEdge = findEdgeTo(atJunction, selectedTarget);
        if (atJunction == null || selectedEdge == null)
            return false;

        TrackNode knownIncoming = contiguousIncomingNode(graph, path, index, junction);
        if (knownIncoming != null)
            return travelableContinuationCount(graph, junction, knownIncoming, selectedEdge) > 1;

        // Compressed-path fallback: try each neighbour as the approach. We only
        // accept an approach if the selected path edge is travelable from it and
        // at least one alternative continuation is also travelable.
        for (TrackNode possibleIncoming : atJunction.keySet()) {
            if (sameNode(possibleIncoming, selectedTarget))
                continue;
            if (travelableContinuationCount(graph, junction, possibleIncoming, selectedEdge) > 1)
                return true;
        }
        return false;
    }

    private static TrackNode contiguousIncomingNode(
            TrackGraph graph, List<Couple<TrackNode>> path, int index, TrackNode junction) {
        if (index <= 0)
            return null;
        Couple<TrackNode> previous = path.get(index - 1);
        if (!previous.getSecond().getLocation().equals(junction.getLocation()))
            return null;
        return graph.locateNode(previous.getFirst().getLocation());
    }

    private static int travelableContinuationCount(
            TrackGraph graph, TrackNode junction, TrackNode incomingNode, TrackEdge selectedEdge) {
        if (graph == null || junction == null || incomingNode == null || selectedEdge == null)
            return 0;
        Map<TrackNode, TrackEdge> incomingConnections = graph.getConnectionsFrom(incomingNode);
        Map<TrackNode, TrackEdge> atJunction = graph.getConnectionsFrom(junction);
        TrackEdge incomingEdge = findEdgeTo(incomingConnections, junction);
        if (incomingEdge == null || atJunction == null || !incomingEdge.canTravelTo(selectedEdge))
            return 0;

        int count = 0;
        for (Map.Entry<TrackNode, TrackEdge> entry : atJunction.entrySet()) {
            if (sameNode(entry.getKey(), incomingNode))
                continue;
            if (incomingEdge.canTravelTo(entry.getValue()))
                count++;
        }
        return count;
    }

    private static String classifyPathBranch(TrackGraph graph, List<Couple<TrackNode>> path, int index) {
        if (graph == null || path == null || index < 0 || index >= path.size())
            return "UNKNOWN";
        Couple<TrackNode> selectedPair = path.get(index);
        TrackNode junction = graph.locateNode(selectedPair.getFirst().getLocation());
        TrackNode selectedTarget = graph.locateNode(selectedPair.getSecond().getLocation());
        if (junction == null || selectedTarget == null)
            return "UNKNOWN";

        TrackNode incoming = contiguousIncomingNode(graph, path, index, junction);
        if (incoming == null)
            return "UNKNOWN";
        return classifyAtJunction(graph, incoming, junction, selectedTarget);
    }


    private static void logA7TargetPathIfChanged(
            Train train, String destinationName, SignalBoundary boundary, BlockPos linkedSignalPos,
            String source, List<Couple<TrackNode>> path, SharedMatch match) {
        if (!isA7TargetDestination(destinationName))
            return;

        String trainName = train.name == null ? train.id.toString() : train.name.getString();
        StringBuilder signature = new StringBuilder();
        signature.append("matches=").append(match.matches)
                .append(";firstIndex=").append(match.firstIndex)
                .append(";shared=").append(match.sharedNode == null ? "NONE" : describe(match.sharedNode))
                .append(";path=");
        if (path == null) {
            signature.append("NULL");
        } else {
            for (int i = 0; i < path.size(); i++)
                signature.append(i).append(':').append(describePair(path.get(i))).append('|');
        }

        String stateKey = boundary.getId() + "|" + train.id + "|" + destinationName + "|" + source;
        String state = signature.toString();
        if (state.equals(a7LoggedPathStates.put(stateKey, state)))
            return;

        if (CommonConfig.signalDebug()) CreateSignallingAdditions.LOGGER.info(
                "Route probe A7 PATH: signal={} boundary={} train='{}' destination='{}' source={} pathSize={} shared={} firstIndex={} sharedBoundaryNode={}",
                linkedSignalPos, boundary.getId(), trainName, destinationName, source,
                sizeOf(path), match.matches, match.firstIndex,
                match.sharedNode == null ? "NONE" : describe(match.sharedNode));

        if (path == null) {
            if (CommonConfig.signalDebug()) CreateSignallingAdditions.LOGGER.info(
                    "Route probe A7 PATH: train='{}' destination='{}' source={} path=NULL",
                    trainName, destinationName, source);
            return;
        }

        if (path.isEmpty()) {
            if (CommonConfig.signalDebug()) CreateSignallingAdditions.LOGGER.info(
                    "Route probe A7 PATH: train='{}' destination='{}' source={} path=EMPTY",
                    trainName, destinationName, source);
            return;
        }

        for (int i = 0; i < path.size(); i++) {
            if (CommonConfig.signalDebug()) CreateSignallingAdditions.LOGGER.info(
                    "Route probe A7 PATH: train='{}' destination='{}' source={} edge[{}]={}",
                    trainName, destinationName, source, i, describePair(path.get(i)));
        }
    }

    private static boolean isA7TargetDestination(String destinationName) {
        return "DaM P2S 2C".equals(destinationName) || "DaM P3 2C".equals(destinationName);
    }

    private static void logPathGeometry(String trainName, String label, List<Couple<TrackNode>> path) {
        if (path == null) {
            debug(
                    "Route probe A7 geometry: train='{}' {} path=NULL", trainName, label);
            return;
        }

        if (path.isEmpty()) {
            debug(
                    "Route probe A7 geometry: train='{}' {} path=EMPTY", trainName, label);
            return;
        }

        debug(
                "Route probe A7 geometry: train='{}' {} edges={} first={} last={}",
                trainName, label, path.size(), describePair(path.get(0)), describePair(path.get(path.size() - 1)));

        for (int i = 0; i < path.size(); i++) {
            Couple<TrackNode> pair = path.get(i);
            debug(
                    "Route probe A7 geometry: train='{}' {} edge[{}]={}",
                    trainName, label, i, describePair(pair));
        }
    }

    private static int sizeOf(List<?> list) {
        return list == null ? -1 : list.size();
    }

    /**
     * Predict Create's manual A/D junction selection at the linked signal.
     *
     * We only accept trains physically near either end of the boundary edge so a
     * manually-driven train elsewhere on the same TrackGraph cannot operate this RI.
     * The actual candidate scoring mirrors TravellingPoint.steer(): LEFT targets a
     * cross-product dot matching Create's observed A/D orientation at the approached junction.
     */
    private static Result resolveManualSteer(
            Train train, TrackGraph graph, SignalBoundary boundary, boolean signalSide, String destinationName) {
        Couple<TrackNodeLocation> boundaryEdge = boundary.edgeLocation;
        TrackNode a = graph.locateNode(boundaryEdge.getFirst());
        TrackNode b = graph.locateNode(boundaryEdge.getSecond());
        if (a == null || b == null)
            return null;

        double distanceA = nearestTrainPointDistance(train, graph, boundaryEdge.getFirst().getLocation());
        double distanceB = nearestTrainPointDistance(train, graph, boundaryEdge.getSecond().getLocation());
        if (Math.min(distanceA, distanceB) > 128.0)
            return null;

        ManualChoice choiceA = chooseManualOutgoing(graph, b, a, train.manualSteer);
        ManualChoice choiceB = chooseManualOutgoing(graph, a, b, train.manualSteer);

        // A physical signal protects one travel direction. Never choose its
        // opposite junction just because the rear carriage happens to be nearer.
        if ((!boundary.isPrimary(b)) != signalSide) choiceA = null;
        if ((!boundary.isPrimary(a)) != signalSide) choiceB = null;

        boolean reversed = com.benjamintaskis.createsignalextensions.content.speed.TrainTravelDirection.reversed(train);
        if (train.carriages.isEmpty()) return null;
        Carriage leading = reversed ? train.carriages.getLast() : train.carriages.getFirst();
        TravellingPoint point = reversed ? leading.getTrailingPoint() : leading.getLeadingPoint();
        if (point == null || point.edge == null) return null;
        Vec3 motion = point.getPositionWithOffset(graph, reversed ? -.25 : .25, false).subtract(point.getPosition(graph));
        Vec3 protectedDirection = (choiceA != null ? a.getLocation().getLocation().subtract(b.getLocation().getLocation())
                : b.getLocation().getLocation().subtract(a.getLocation().getLocation()));
        if (motion.dot(protectedDirection) <= 1.0e-6) return null;

        // Prefer a true junction (multiple valid routes) over a plain continuation.
        ManualChoice choice = null;
        if (choiceA != null && choiceA.candidateCount > 1)
            choice = choiceA;
        if (choiceB != null && choiceB.candidateCount > 1
                && (choice == null || distanceB < distanceA))
            choice = choiceB;
        if (choice == null)
            return null;

        String trainName = train.name == null ? train.id.toString() : train.name.getString();
        Couple<TrackNode> routeEdge = Couple.create(choice.junction, choice.target);
        debug(
                "Route probe A6a MANUAL: train='{}' steer={} nearby={}.{} candidates={} route={}",
                trainName, train.manualSteer,
                String.format("%.1f", Math.min(distanceA, distanceB)), "m",
                choice.candidateCount, describePair(routeEdge));
        return resolved(train, routeEdge, "MANUAL_" + train.manualSteer.name(), destinationName, classifyRoute(graph, boundaryEdge, routeEdge));
    }

    private record ManualChoice(TrackNode junction, TrackNode target, int candidateCount) {}

    private static ManualChoice chooseManualOutgoing(
            TrackGraph graph, TrackNode incomingNode, TrackNode junction, TravellingPoint.SteerDirection steer) {
        TrackEdge incomingEdge = graph.getConnectionsFrom(incomingNode).get(junction);
        if (incomingEdge == null)
            return null;

        Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(junction);
        if (connections == null || connections.isEmpty())
            return null;

        Vec3 trajectory = incomingEdge.getDirection(false);
        Vec3 normal = trajectory.cross(junction.getNormal());
        double targetDot = steer == TravellingPoint.SteerDirection.LEFT ? -1.0 : 1.0;
        double closest = Double.MAX_VALUE;
        TrackNode best = null;
        int candidates = 0;

        for (Map.Entry<TrackNode, TrackEdge> entry : connections.entrySet()) {
            TrackNode target = entry.getKey();
            TrackEdge outgoing = entry.getValue();
            if (target == incomingNode)
                continue;
            if (!incomingEdge.canTravelTo(outgoing))
                continue;

            candidates++;
            Vec3 entryTrajectory = outgoing.getDirection(true);
            double dot = normal.dot(entryTrajectory);
            double diff = Math.abs(targetDot - dot);
            if (diff > closest)
                continue;
            closest = diff;
            best = target;
        }

        return best == null ? null : new ManualChoice(junction, best, candidates);
    }

    private static Vec3 boundaryMidpoint(Couple<TrackNodeLocation> edge) {
        Vec3 a = edge.getFirst().getLocation();
        Vec3 b = edge.getSecond().getLocation();
        return a.add(b).scale(0.5);
    }

    private static double nearestTrainPointDistance(Train train, TrackGraph graph, Vec3 target) {
        double nearest = Double.MAX_VALUE;
        for (Carriage carriage : train.carriages) {
            nearest = Math.min(nearest, travellingPointDistance(carriage.getLeadingPoint(), graph, target));
            nearest = Math.min(nearest, travellingPointDistance(carriage.getTrailingPoint(), graph, target));
        }
        return nearest;
    }

    private static double travellingPointDistance(TravellingPoint point, TrackGraph graph, Vec3 target) {
        if (point == null || point.edge == null)
            return Double.MAX_VALUE;
        double length = point.edge.getLength();
        double t = length == 0 ? 0 : point.position / length;
        return point.edge.getPosition(graph, t).distanceTo(target);
    }

    private static TrackGraph findOwningGraph(SignalBoundary boundary) {
        for (TrackGraph graph : Create.RAILWAYS.trackNetworks.values()) {
            SignalBoundary found = graph.getPoint(EdgePointType.SIGNAL, boundary.getId());
            if (found != null)
                return graph;
        }
        return null;
    }

    private static Boolean getSignalSide(SignalBoundary boundary, BlockPos signalPos) {
        if (signalPos == null)
            return null;
        if (boundary.blockEntities.getFirst().containsKey(signalPos))
            return true;
        if (boundary.blockEntities.getSecond().containsKey(signalPos))
            return false;
        return null;
    }

    /**
     * Classify the selected outgoing edge relative to the edge by which the train
     * approaches its junction node. The valid continuation with the smallest
     * lateral steering component is treated as STRAIGHT; the remaining routes
     * use the same sign convention as Create's manual steering (LEFT=-, RIGHT=+).
     */
    private static String classifyRoute(TrackGraph graph, Couple<TrackNodeLocation> boundaryEdge, Couple<TrackNode> routeEdge) {
        if (graph == null || boundaryEdge == null || routeEdge == null)
            return "UNKNOWN";

        // Alpha 11b: resolve the boundary exactly the same way as the proven
        // manual-steering code. A11 tried to reconstruct the incoming node from
        // the selected route edge, which was too fragile and left valid turnouts
        // classified as UNKNOWN.
        TrackNode boundaryA = graph.locateNode(boundaryEdge.getFirst());
        TrackNode boundaryB = graph.locateNode(boundaryEdge.getSecond());
        if (boundaryA == null || boundaryB == null)
            return "UNKNOWN";

        TrackNode routeJunction = routeEdge.getFirst();
        TrackNode selectedTarget = routeEdge.getSecond();

        TrackNode junction;
        TrackNode incomingNode;
        if (sameNode(routeJunction, boundaryA)) {
            junction = boundaryA;
            incomingNode = boundaryB;
        } else if (sameNode(routeJunction, boundaryB)) {
            junction = boundaryB;
            incomingNode = boundaryA;
        } else {
            return "UNKNOWN";
        }

        return classifyAtJunction(graph, incomingNode, junction, selectedTarget);
    }

    private static String classifyAtJunction(
            TrackGraph graph, TrackNode incomingNode, TrackNode junction, TrackNode selectedTarget) {
        Map<TrackNode, TrackEdge> incomingConnections = graph.getConnectionsFrom(incomingNode);
        Map<TrackNode, TrackEdge> atJunction = graph.getConnectionsFrom(junction);
        if (incomingConnections == null || atJunction == null || atJunction.isEmpty())
            return "UNKNOWN";

        TrackEdge incomingEdge = findEdgeTo(incomingConnections, junction);
        TrackEdge selectedEdge = findEdgeTo(atJunction, selectedTarget);
        if (incomingEdge == null || selectedEdge == null || !incomingEdge.canTravelTo(selectedEdge))
            return "UNKNOWN";

        Vec3 trajectory = incomingEdge.getDirection(false);
        Vec3 normal = trajectory.cross(junction.getNormal());
        double selectedDot = normal.dot(selectedEdge.getDirection(true));

        double straightAbsDot = Double.MAX_VALUE;
        int candidates = 0;
        for (Map.Entry<TrackNode, TrackEdge> entry : atJunction.entrySet()) {
            TrackNode target = entry.getKey();
            if (sameNode(target, incomingNode))
                continue;

            TrackEdge outgoing = entry.getValue();
            if (!incomingEdge.canTravelTo(outgoing))
                continue;

            candidates++;
            double dot = normal.dot(outgoing.getDirection(true));
            straightAbsDot = Math.min(straightAbsDot, Math.abs(dot));
        }

        if (candidates == 0 || straightAbsDot == Double.MAX_VALUE)
            return "UNKNOWN";
        if (Math.abs(selectedDot) <= straightAbsDot + 1.0e-4)
            return "STRAIGHT";
        return selectedDot < 0 ? "DIVERGING LEFT" : "DIVERGING RIGHT";
    }

    private static boolean sameNode(TrackNode a, TrackNode b) {
        return a == b || (a != null && b != null && a.getLocation().equals(b.getLocation()));
    }

    private static TrackEdge findEdgeTo(Map<TrackNode, TrackEdge> connections, TrackNode target) {
        if (connections == null || target == null)
            return null;

        TrackEdge direct = connections.get(target);
        if (direct != null)
            return direct;

        // TrackNode identity is not guaranteed to be the same object instance
        // as a node supplied by a discovered path, so fall back to location.
        for (Map.Entry<TrackNode, TrackEdge> entry : connections.entrySet()) {
            if (sameNode(entry.getKey(), target))
                return entry.getValue();
        }
        return null;
    }

    private static Result resolved(Train train, Couple<TrackNode> routeEdge, String mode, String destinationName, String routeType) {
        Vec3 from = routeEdge.getFirst().getLocation().getLocation();
        Vec3 to = routeEdge.getSecond().getLocation().getLocation();
        return resolvedWithKey(train, describePair(routeEdge), mode, destinationName, routeType,
                List.of(new PathPoint(from.x, from.y, from.z), new PathPoint(to.x, to.y, to.z)));
    }

    private static Result resolvedWithKey(Train train, String routeKey, String mode, String destinationName, String routeType) {
        return resolvedWithKey(train, routeKey, mode, destinationName, routeType, List.of());
    }

    private static Result resolvedWithKey(Train train, String routeKey, String mode, String destinationName, String routeType, List<PathPoint> visualPath) {
        String trainName = train.name == null ? train.id.toString() : train.name.getString();
        return new Result(true, "FOUND " + mode + " | " + trainName + " | " + routeKey + " | " + routeType, trainName, routeKey, destinationName, routeType, visualPath);
    }

    private static String describePair(Couple<TrackNode> pair) {
        return describe(pair.getFirst().getLocation()) + " -> " + describe(pair.getSecond().getLocation());
    }

    private static String describeEdge(Couple<TrackNodeLocation> edge) {
        return describe(edge.getFirst()) + " <-> " + describe(edge.getSecond());
    }

    private static String describe(TrackNodeLocation location) {
        Vec3 p = location.getLocation();
        return String.format("%.1f,%.1f,%.1f", p.x, p.y, p.z);
    }
    /** Alpha 5c: nearest active train whose selected path reaches this Create signal. */
    public static Train findApproachingTrain(SignalBoundary boundary, BlockPos linkedSignalPos) {
        if (boundary == null || boundary.edgeLocation == null) return null;
        TrackGraph graph = findOwningGraph(boundary);
        if (graph == null) return null;
        Boolean side = getSignalSide(boundary, linkedSignalPos);
        if (side == null) return null;
        Vec3 midpoint = boundaryMidpoint(boundary.edgeLocation);
        Train best = null; double bestDistance = Double.MAX_VALUE;
        for (Train train : Create.RAILWAYS.trains.values()) {
            if (train == null || train.graph != graph || train.navigation == null || !train.navigation.isActive()) continue;
            List<Couple<TrackNode>> path = getCurrentPath(train.navigation);
            if (!pathMatchesSide(boundary, side, path)) continue;
            boolean reaches = pathContainsBoundaryEdge(path, boundary.edgeLocation) || findOutgoingSharedNodeMatch(path, boundary.edgeLocation).matches > 0;
            if (!reaches) continue;
            double distance = nearestTrainPointDistance(train, graph, midpoint);
            if (distance < bestDistance) { bestDistance = distance; best = train; }
        }
        return best;
    }

    private static void debug(String message, Object... args) {
        if (CommonConfig.signalDebug())
            CreateSignallingAdditions.LOGGER.debug(message, args);
    }

}
