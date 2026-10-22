package com.benjamintaskis.createsignalextensions.content.route;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import com.benjamintaskis.createsignalextensions.config.CommonConfig;
import com.benjamintaskis.createsignalextensions.content.signal.SignalAspectLinkManager;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.content.trains.signal.SignalBlock;
import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import com.benjamintaskis.createsignalextensions.network.OpenRouteConfigPayload;
import java.util.concurrent.atomic.AtomicLong;

public class RouteInterfaceBlockEntity extends SmartBlockEntity {
    /** Beta 2a: routine Route Interface telemetry is opt-in production debug. */
    private static void debugInfo(String message, Object... args) {
        if (CommonConfig.signalDebug())
            CreateSignallingAdditions.LOGGER.info(message, args);
    }

    private static final AtomicLong CONFIG_SESSION_IDS = new AtomicLong();
    private BlockPos linkedSignal;
    // Beta 6a: per-interface automatic route look-ahead ceiling, in blocks of track.
    private int automaticRouteRange = 128;
    // Beta 7: when false, unknown paths are matched to the closest learned route instead of creating one.
    private boolean allowNewRoutes = true;

    // Alpha 7: cache the values that Display Links actually consume. The route
    // pathfinder is therefore run by this block entity on a short cadence, not
    // every time every attached Display Link asks for text.
    private RouteProbe.Result cachedRoute = RouteProbe.Result.unresolved("NO ROUTE YET");
    private String cachedAspect = "UNLINKED";
    private String cachedDestination = "";
    private boolean snapshotInitialized;

    // Alpha 8: raw Create route edge -> player-facing label.
    private final Map<String, String> routeLabels = new HashMap<>();
    // Alpha 7: route-specific indication for large theatre boxes. Empty string is an intentional blank.
    private final Map<String, String> theatreIndications = new HashMap<>();
    // Alpha 8: six fixed small-theatre cells per route: top-left, top-right,
    // middle-left, middle-right, bottom-left, bottom-right.
    private final Map<String, List<String>> smallTheatreIndications = new HashMap<>();
    // Routes observed by this interface, including routes which have not yet been labelled.
    private final Set<String> knownRoutes = new HashSet<>();
    // Alpha 11: learned geometry classification for each observed raw route.
    private final Map<String, String> routeTypes = new HashMap<>();
    // Alpha 6: route-specific special indication, independent of geometry.
    private final Map<String, String> routeIndications = new HashMap<>();
    // Beta 6: continuous downstream geometry used only by the graphical Route Map.
    // This is deliberately separate from the route signature so intermediate track
    // geometry cannot create extra learned routes.
    private final Map<String, List<RouteProbe.PathPoint>> routeVisualPaths = new HashMap<>();
    // Beta 3a: wildcard destination name -> large theatre indication.
    private final Map<String, String> destinationRules = new LinkedHashMap<>();
    // Beta 4: optional six-cell small-theatre output for each destination rule.
    private final Map<String, List<String>> destinationSmallTheatreRules = new LinkedHashMap<>();

    public RouteInterfaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        // Use the common signalling cadence (default 10 ticks = 2 Hz).
        setLazyTickRate(CommonConfig.signalRefreshTicks());
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level == null || level.isClientSide)
            return;
        // Alpha 1i: publish the exact Route Interface -> Create signal association
        // for aspect heads. This avoids guessing by physical proximity and lets the
        // aspect resolver use the same learned/configured route metadata as this RI.
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel && linkedSignal != null)
            SignalAspectLinkManager.get(serverLevel).registerRouteInterface(linkedSignal, worldPosition);
        refreshSnapshot(true);
    }

    public BlockPos getLinkedSignalPos() {
        return linkedSignal;
    }

    public boolean isAllowNewRoutes() { return allowNewRoutes; }

    public void setAllowNewRoutes(boolean allow) {
        if (allowNewRoutes == allow) return;
        allowNewRoutes = allow;
        snapshotInitialized = false;
        setChanged();
        notifyUpdate();
        refreshSnapshot(true);
    }

    public int getAutomaticRouteRange() {
        return automaticRouteRange;
    }

    public void setAutomaticRouteRange(int blocks) {
        int clamped = Math.max(16, Math.min(256, ((blocks + 8) / 16) * 16));
        if (automaticRouteRange == clamped)
            return;
        automaticRouteRange = clamped;
        snapshotInitialized = false;
        setChanged();
        notifyUpdate();
        refreshSnapshot(true);
    }

    public void setLinkedSignal(BlockPos pos) {
        linkedSignal = pos.immutable();
        snapshotInitialized = false;
        debugInfo("Route Interface at {} linked to Create signal at {}", worldPosition, linkedSignal);
        setChanged();
        notifyUpdate();
        refreshSnapshot(true);
    }

    public void clearLinkedSignal() {
        linkedSignal = null;
        snapshotInitialized = false;
        cachedRoute = RouteProbe.Result.unresolved("UNLINKED");
        cachedAspect = "UNLINKED";
        cachedDestination = "";
        setChanged();
        notifyUpdate();
        refreshAttachedDisplayLinks();
    }

    public SignalBlockEntity getLinkedSignal() {
        if (level == null || linkedSignal == null)
            return null;

        BlockEntity be = level.getBlockEntity(linkedSignal);
        if (be instanceof SignalBlockEntity signal)
            return signal;

        BlockPos replacement = findNearestCreateSignal(16);
        if (replacement != null) {
            CreateSignallingAdditions.LOGGER.warn(
                    "Route Interface at {} could not resolve saved signal at {} ({}); relinking to {}",
                    worldPosition, linkedSignal, describeBlockAt(linkedSignal), replacement);
            linkedSignal = replacement.immutable();
            setChanged();
            notifyUpdate();
            BlockEntity replacementBe = level.getBlockEntity(replacement);
            if (replacementBe instanceof SignalBlockEntity replacementSignal)
                return replacementSignal;
        }

        CreateSignallingAdditions.LOGGER.warn(
                "Route Interface at {} could not resolve saved signal at {} ({}) and found no replacement within 16 blocks",
                worldPosition, linkedSignal, describeBlockAt(linkedSignal));
        return null;
    }

    public String describeBlockAt(BlockPos pos) {
        if (level == null || pos == null)
            return "level/position unavailable";
        if (!level.hasChunkAt(pos))
            return "chunk not loaded";

        String blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
        BlockEntity be = level.getBlockEntity(pos);
        String beName = be == null ? "none" : be.getClass().getName();
        return "block=" + blockId + ", blockEntity=" + beName;
    }

    public BlockPos findNearestCreateSignal(int radius) {
        if (level == null)
            return null;

        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos candidate = new BlockPos(
                            worldPosition.getX() + dx,
                            worldPosition.getY() + dy,
                            worldPosition.getZ() + dz);

                    if (!(level.getBlockEntity(candidate) instanceof SignalBlockEntity))
                        continue;

                    double distance = candidate.distSqr(worldPosition);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = candidate.immutable();
                    }
                }
            }
        }

        if (nearest != null) {
            debugInfo(
                    "Route Interface at {} found Create signal at {} (delta {}, {}, {})",
                    worldPosition, nearest,
                    nearest.getX() - worldPosition.getX(),
                    nearest.getY() - worldPosition.getY(),
                    nearest.getZ() - worldPosition.getZ());
        }

        return nearest;
    }

    /**
     * Re-evaluate the linked signal and selected route. When the public-facing
     * data changes, attached Create Display Links are pushed immediately.
     */
    private void refreshSnapshot(boolean refreshDisplaysOnChange) {
        String newAspect = "UNLINKED";
        RouteProbe.Result newRoute = RouteProbe.Result.unresolved("UNLINKED");
        String newDestination = "";

        SignalBlockEntity signal = getLinkedSignal();
        if (signal != null) {
            newAspect = signal.getState().name();
            SignalBoundary boundary = signal.getSignal();
            newRoute = RouteProbe.probe(boundary, linkedSignal, automaticRouteRange);
            newDestination = newRoute.destinationName();
        }

        // Beta 2: normalise a locked interface's raw physical path BEFORE comparing it
        // with the cached public snapshot. Previously the comparison/log happened first,
        // so a longer live path was reported as a "change" every refresh and was then
        // collapsed back to the same learned route. That produced the ~2 Hz INFO spam
        // seen on the live server even though the displayed route never actually changed.
        if (!allowNewRoutes && newRoute.resolved() && !knownRoutes.contains(newRoute.routeKey()) && !knownRoutes.isEmpty()) {
            String matched = closestKnownRoute(newRoute.routeKey());
            if (matched != null)
                newRoute = new RouteProbe.Result(true, newRoute.summary() + " | MATCHED " + matched, newRoute.trainName(), matched,
                        newRoute.destinationName(), routeTypes.getOrDefault(matched, newRoute.routeType()),
                        routeVisualPaths.getOrDefault(matched, List.of()));
        }

        boolean changed = !snapshotInitialized
                || !Objects.equals(cachedAspect, newAspect)
                || !Objects.equals(cachedRoute.routeKey(), newRoute.routeKey())
                || cachedRoute.resolved() != newRoute.resolved()
                || !Objects.equals(cachedDestination, newDestination);

        if (changed) {
            debugInfo(
                    "Route Interface {} state changed: aspect {} -> {}, route '{}' -> '{}', destination '{}' -> '{}'",
                    worldPosition, cachedAspect, newAspect, cachedRoute.routeKey(), newRoute.routeKey(),
                    cachedDestination, newDestination);
        }

        cachedAspect = newAspect;
        cachedRoute = newRoute;
        cachedDestination = newDestination;
        if (allowNewRoutes && newRoute.resolved() && newRoute.routeKey() != null && !newRoute.routeKey().isBlank()) {
            boolean newlyLearned = knownRoutes.add(newRoute.routeKey());
            boolean learnedChanged = newlyLearned;
            if (newRoute.visualPath() != null && !newRoute.visualPath().isEmpty()) {
                List<RouteProbe.PathPoint> oldPath = routeVisualPaths.put(newRoute.routeKey(), List.copyOf(newRoute.visualPath()));
                learnedChanged |= !Objects.equals(oldPath, newRoute.visualPath());
            }
            // Keep a usable classification attached to a newly observed route immediately.
            // Player edits in the Route Map can still override this later.
            if (newlyLearned && newRoute.routeType() != null && !newRoute.routeType().isBlank())
                routeTypes.putIfAbsent(newRoute.routeKey(), newRoute.routeType());
            if (learnedChanged) {
                setChanged();
                notifyUpdate();
                if (newlyLearned)
                    debugInfo("Route Interface {} learned new route '{}' (type={}, range={} blocks)",
                            worldPosition, newRoute.routeKey(), newRoute.routeType(), automaticRouteRange);
            }
        }
        snapshotInitialized = true;

        if (changed && refreshDisplaysOnChange)
            refreshAttachedDisplayLinks();
    }

    private String closestKnownRoute(String candidate) {
        String[] incoming = candidate.split("\\s*\\|\\s*");
        String best = null;
        int bestPrefix = -1;
        int bestCommon = -1;
        for (String known : knownRoutes) {
            String[] stored = known.split("\\s*\\|\\s*");
            int prefix = 0;
            while (prefix < incoming.length && prefix < stored.length && incoming[prefix].equals(stored[prefix])) prefix++;
            int common = 0;
            for (String a : incoming) for (String b : stored) if (a.equals(b)) { common++; break; }
            if (prefix > bestPrefix || (prefix == bestPrefix && common > bestCommon) ||
                    (prefix == bestPrefix && common == bestCommon && (best == null || known.compareTo(best) < 0))) {
                best = known; bestPrefix = prefix; bestCommon = common;
            }
        }
        return best;
    }

    /**
     * A Create Display Link's source is the block it faces. Scan the immediate
     * neighbourhood and only refresh links whose source position is exactly
     * this Route Interface. This is change-triggered rather than continuous.
     */
    private void refreshAttachedDisplayLinks() {
        if (level == null || level.isClientSide)
            return;

        int refreshed = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos candidate = worldPosition.offset(dx, dy, dz);
                    BlockEntity be = level.getBlockEntity(candidate);
                    if (!(be instanceof DisplayLinkBlockEntity link))
                        continue;
                    if (!worldPosition.equals(link.getSourcePosition()))
                        continue;

                    link.refreshTicks = 0;
                    link.updateGatheredData();
                    refreshed++;
                }
            }
        }

        if (refreshed > 0)
            if (CommonConfig.signalDebug())
                CreateSignallingAdditions.LOGGER.debug("Route Interface {} refreshed {} attached Display Link(s)", worldPosition, refreshed);
    }

    public String getCachedAspect() {
        if (!snapshotInitialized && level != null && !level.isClientSide)
            refreshSnapshot(false);
        return cachedAspect;
    }

    public String getCachedDestination() {
        if (!snapshotInitialized && level != null && !level.isClientSide)
            refreshSnapshot(false);
        return cachedDestination;
    }

    public String getSelectedRouteDisplayValue() {
        RouteProbe.Result result = getRouteProbeResult();
        if (!result.resolved())
            return "NO ROUTE";
        return routeLabels.getOrDefault(result.routeKey(), "UNMAPPED");
    }

    public String getRouteLabel(String routeKey) {
        return routeLabels.get(routeKey);
    }

    public String getSelectedTheatreIndication() {
        if (!snapshotInitialized && level != null && !level.isClientSide)
            refreshSnapshot(false);
        String destinationOverride = getDestinationRuleIndication(cachedDestination);
        if (!destinationOverride.isBlank())
            return destinationOverride;
        RouteProbe.Result result = cachedRoute;
        if (!result.resolved() || result.routeKey() == null || result.routeKey().isBlank())
            return "";
        return theatreIndications.getOrDefault(result.routeKey(), "");
    }

    public String getTheatreIndication(String routeKey) {
        return theatreIndications.getOrDefault(routeKey, "");
    }

    public List<String> getSelectedSmallTheatreIndications() {
        if (!snapshotInitialized && level != null && !level.isClientSide)
            refreshSnapshot(false);
        List<String> destinationOverride = getDestinationRuleSmallTheatreIndications(cachedDestination);
        if (hasAnySmallTheatreIndication(destinationOverride))
            return destinationOverride;
        RouteProbe.Result result = cachedRoute;
        if (!result.resolved() || result.routeKey() == null || result.routeKey().isBlank())
            return blankSmallTheatreIndications();
        return getSmallTheatreIndications(result.routeKey());
    }

    public List<String> getSmallTheatreIndications(String routeKey) {
        List<String> stored = smallTheatreIndications.get(routeKey);
        if (stored == null)
            return blankSmallTheatreIndications();
        return List.copyOf(stored);
    }

    public void setSmallTheatreIndications(String routeKey, List<String> indications) {
        if (routeKey == null || routeKey.isBlank())
            return;
        List<String> normalized = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) {
            String value = indications != null && i < indications.size() ? indications.get(i) : "";
            normalized.add(normalizeSmallTheatreIndication(value));
        }
        smallTheatreIndications.put(routeKey, normalized);
        debugInfo(
                "Route Interface {} mapped route '{}' to small theatre cells {}",
                worldPosition, routeKey, normalized);
        setChanged();
        notifyUpdate();
    }

    private static List<String> blankSmallTheatreIndications() {
        return List.of("", "", "", "", "", "");
    }

    private static String normalizeSmallTheatreIndication(String indication) {
        if (indication == null || indication.isBlank())
            return "";
        String upper = indication.trim().toUpperCase(java.util.Locale.ROOT);
        StringBuilder out = new StringBuilder(2);
        for (int i = 0; i < upper.length() && out.length() < 2; i++) {
            char c = upper.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '/' || c == '\\')
                out.append(c);
        }
        return out.toString();
    }

    public void setTheatreIndication(String routeKey, String indication) {
        if (routeKey == null || routeKey.isBlank())
            return;
        String normalized = normalizeTheatreIndication(indication);
        theatreIndications.put(routeKey, normalized);
        debugInfo(
                "Route Interface {} mapped route '{}' to theatre indication '{}'",
                worldPosition, routeKey, normalized.isBlank() ? "<blank>" : normalized);
        setChanged();
        notifyUpdate();
    }

    private static String normalizeTheatreIndication(String indication) {
        if (indication == null || indication.isBlank())
            return "";
        int cp = indication.codePointAt(0);
        if ((cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z') ||
                (cp >= '0' && cp <= '9') || cp == '/' || cp == '\\') {
            return new String(Character.toChars(Character.toUpperCase(cp)));
        }
        return "";
    }


    public void setDestinationRule(String oldPattern, String pattern, String indication, List<String> smallIndications) {
        String normalizedPattern = pattern == null ? "" : pattern.trim();
        if (normalizedPattern.length() > 128)
            normalizedPattern = normalizedPattern.substring(0, 128);
        String old = oldPattern == null ? "" : oldPattern.trim();
        if (!old.isBlank() && !old.equals(normalizedPattern)) {
            destinationRules.remove(old);
            destinationSmallTheatreRules.remove(old);
        }
        if (normalizedPattern.isBlank())
            return;
        destinationRules.put(normalizedPattern, normalizeTheatreIndication(indication));
        List<String> normalizedSmall = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) {
            String value = smallIndications != null && i < smallIndications.size() ? smallIndications.get(i) : "";
            normalizedSmall.add(normalizeSmallTheatreIndication(value));
        }
        destinationSmallTheatreRules.put(normalizedPattern, normalizedSmall);
        debugInfo("Route Interface {} destination rule '{}' -> '{}', small {}",
                worldPosition, normalizedPattern, destinationRules.get(normalizedPattern), normalizedSmall);
        setChanged();
        notifyUpdate();
        refreshAttachedDisplayLinks();
    }

    public void removeDestinationRule(String pattern) {
        if (pattern == null || pattern.isBlank())
            return;
        String normalized = pattern.trim();
        destinationSmallTheatreRules.remove(normalized);
        if (destinationRules.remove(normalized) != null) {
            setChanged();
            notifyUpdate();
            refreshAttachedDisplayLinks();
        }
    }

    private String getDestinationRuleIndication(String destination) {
        if (destination == null || destination.isBlank())
            return "";
        for (Map.Entry<String, String> rule : destinationRules.entrySet()) {
            if (globMatches(rule.getKey(), destination))
                return rule.getValue();
        }
        return "";
    }


    private List<String> getDestinationRuleSmallTheatreIndications(String destination) {
        if (destination == null || destination.isBlank())
            return blankSmallTheatreIndications();
        for (Map.Entry<String, String> rule : destinationRules.entrySet()) {
            if (globMatches(rule.getKey(), destination))
                return List.copyOf(destinationSmallTheatreRules.getOrDefault(rule.getKey(), blankSmallTheatreIndications()));
        }
        return blankSmallTheatreIndications();
    }

    private static boolean hasAnySmallTheatreIndication(List<String> values) {
        if (values == null) return false;
        for (String value : values)
            if (value != null && !value.isBlank()) return true;
        return false;
    }

    private static boolean globMatches(String pattern, String value) {
        if (pattern == null || value == null)
            return false;
        String p = pattern.toLowerCase(java.util.Locale.ROOT);
        String v = value.toLowerCase(java.util.Locale.ROOT);
        int pi = 0, vi = 0, star = -1, starMatch = 0;
        while (vi < v.length()) {
            if (pi < p.length() && (p.charAt(pi) == '?' || p.charAt(pi) == v.charAt(vi))) {
                pi++; vi++;
            } else if (pi < p.length() && p.charAt(pi) == '*') {
                star = pi++;
                starMatch = vi;
            } else if (star >= 0) {
                pi = star + 1;
                vi = ++starMatch;
            } else {
                return false;
            }
        }
        while (pi < p.length() && p.charAt(pi) == '*')
            pi++;
        return pi == p.length();
    }

    public void setRouteType(String routeKey, String routeType) {
        if (routeKey == null || routeKey.isBlank())
            return;
        String normalized = switch (routeType == null ? "" : routeType.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "DIVERGING LEFT" -> "DIVERGING LEFT";
            case "DIVERGING RIGHT" -> "DIVERGING RIGHT";
            default -> "STRAIGHT";
        };
        routeTypes.put(routeKey, normalized);
        debugInfo(
                "Route Interface {} mapped route '{}' to route type '{}'", worldPosition, routeKey, normalized);
        setChanged();
        notifyUpdate();
    }


    public void setRouteIndication(String routeKey, String indication) {
        if (routeKey == null || routeKey.isBlank()) return;
        String normalized = switch (indication == null ? "" : indication.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "SHUNT" -> "SHUNT";
            case "DEAD END", "DEAD_END" -> "DEAD_END";
            default -> "NORMAL";
        };
        routeIndications.put(routeKey, normalized);
        setChanged(); notifyUpdate(); refreshAttachedDisplayLinks();
    }

    public String getSelectedRouteIndication() {
        RouteProbe.Result result = getRouteProbeResult();
        if (!result.resolved() || result.routeKey() == null || result.routeKey().isBlank()) return "NORMAL";
        return routeIndications.getOrDefault(result.routeKey(), "NORMAL");
    }

    public void setRouteLabel(String routeKey, String label) {
        if (routeKey == null || routeKey.isBlank() || label == null || label.isBlank())
            return;
        routeLabels.put(routeKey, label);
        debugInfo(
                "Route Interface {} mapped route '{}' to display label '{}'", worldPosition, routeKey, label);
        setChanged();
        notifyUpdate();
        refreshAttachedDisplayLinks();
    }

    public void clearLoadedRoutes() {
        int count = knownRoutes.size();
        knownRoutes.clear();
        routeLabels.clear();
        theatreIndications.clear();
        smallTheatreIndications.clear();
        routeTypes.clear();
        routeIndications.clear();
        routeVisualPaths.clear();
        debugInfo(
                "Route Interface {} cleared {} loaded route(s) and route-specific mappings",
                worldPosition, count);
        setChanged();
        notifyUpdate();
        refreshAttachedDisplayLinks();
    }

    public boolean deleteLoadedRoute(String routeKey) {
        if (routeKey == null || routeKey.isBlank())
            return false;

        boolean removed = knownRoutes.remove(routeKey);
        removed |= routeLabels.remove(routeKey) != null;
        removed |= theatreIndications.remove(routeKey) != null;
        removed |= smallTheatreIndications.remove(routeKey) != null;
        removed |= routeTypes.remove(routeKey) != null;
        removed |= routeIndications.remove(routeKey) != null;
        removed |= routeVisualPaths.remove(routeKey) != null;
        if (!removed)
            return false;

        debugInfo(
                "Route Interface {} deleted loaded route '{}' and its route-specific mappings",
                worldPosition, routeKey);
        setChanged();
        notifyUpdate();
        refreshAttachedDisplayLinks();
        return true;
    }

    public boolean clearRouteLabel(String routeKey) {
        if (routeKey == null || routeKey.isBlank())
            return false;
        String removed = routeLabels.remove(routeKey);
        if (removed == null)
            return false;
        debugInfo(
                "Route Interface {} removed mapping for route '{}' (was '{}')", worldPosition, routeKey, removed);
        setChanged();
        notifyUpdate();
        refreshAttachedDisplayLinks();
        return true;
    }
    public void openConfigurationScreen(ServerPlayer player) {
        openConfigurationScreen(player, false, CONFIG_SESSION_IDS.incrementAndGet());
    }

    public void openConfigurationScreen(ServerPlayer player, boolean refreshResponse, long sessionId) {
        refreshSnapshot(false);

        String signalText = linkedSignal == null
                ? "Unlinked"
                : linkedSignal.getX() + ", " + linkedSignal.getY() + ", " + linkedSignal.getZ();

        List<String> keys = new ArrayList<>(knownRoutes);
        keys.sort(String::compareTo);

        List<OpenRouteConfigPayload.RouteEntry> routes = new ArrayList<>();
        for (String key : keys)
            routes.add(new OpenRouteConfigPayload.RouteEntry(
                    key,
                    routeLabels.getOrDefault(key, ""),
                    theatreIndications.getOrDefault(key, ""),
                    getSmallTheatreIndications(key),
                    routeTypes.getOrDefault(key, "STRAIGHT"),
                    routeIndications.getOrDefault(key, "NORMAL"),
                    routeVisualPaths.getOrDefault(key, List.of()).stream()
                            .map(point -> new OpenRouteConfigPayload.PathPoint(point.x(), point.y(), point.z()))
                            .toList()));

        List<OpenRouteConfigPayload.DestinationRule> rules = new ArrayList<>();
        destinationRules.forEach((pattern, indication) ->
                rules.add(new OpenRouteConfigPayload.DestinationRule(pattern, indication,
                        destinationSmallTheatreRules.getOrDefault(pattern, blankSmallTheatreIndications()))));

        PacketDistributor.sendToPlayer(player, new OpenRouteConfigPayload(
                worldPosition,
                signalText,
                cachedAspect,
                cachedDestination,
                cachedRoute.resolved() ? cachedRoute.routeKey() : "",
                routes,
                rules,
                automaticRouteRange,
                allowNewRoutes,
                refreshResponse,
                sessionId
        ));
    }

    public Component getStatusMessage() {
        if (linkedSignal == null)
            return Component.translatable("message.create_signal_extensions.unlinked");

        refreshSnapshot(false);

        BlockPos requestedPos = linkedSignal;
        SignalBlockEntity signal = getLinkedSignal();
        if (signal == null)
            return Component.translatable("message.create_signal_extensions.missing_debug",
                    requestedPos.getX(), requestedPos.getY(), requestedPos.getZ(), describeBlockAt(requestedPos));

        SignalBoundary boundary = signal.getSignal();
        String type = signal.getBlockState().getOptionalValue(SignalBlock.TYPE)
                .map(t -> t.getSerializedName())
                .orElse("unknown");

        return Component.translatable("message.create_signal_extensions.status_route",
                linkedSignal.getX(), linkedSignal.getY(), linkedSignal.getZ(),
                cachedAspect, type, boundary != null ? "OK" : "MISSING", cachedRoute.summary());
    }

    public RouteProbe.Result getRouteProbeResult() {
        if (!snapshotInitialized && level != null && !level.isClientSide)
            refreshSnapshot(false);
        return cachedRoute;
    }

    /**
     * Alpha 1i: effective route type for the route currently selected through this
     * interface. A player's explicit Route Map classification wins over the raw
     * RouteProbe classifier, because the RI is the authoritative route-metadata
     * provider for turnout-capable signals.
     */
    public String getSelectedRouteType() {
        RouteProbe.Result result = getRouteProbeResult();
        if (!result.resolved() || result.routeKey() == null || result.routeKey().isBlank())
            return "UNKNOWN";
        return routeTypes.getOrDefault(result.routeKey(), result.routeType());
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (linkedSignal != null)
            tag.putLong("LinkedSignal", linkedSignal.asLong());
        tag.putInt("AutomaticRouteRange", automaticRouteRange);
        tag.putBoolean("AllowNewRoutes", allowNewRoutes);

        CompoundTag labels = new CompoundTag();
        routeLabels.forEach(labels::putString);
        tag.put("RouteLabels", labels);

        CompoundTag theatres = new CompoundTag();
        theatreIndications.forEach(theatres::putString);
        tag.put("TheatreIndications", theatres);

        CompoundTag smallTheatres = new CompoundTag();
        smallTheatreIndications.forEach((routeKey, values) -> {
            CompoundTag cells = new CompoundTag();
            for (int i = 0; i < Math.min(values.size(), 6); i++)
                cells.putString(Integer.toString(i), values.get(i));
            smallTheatres.put(routeKey, cells);
        });
        tag.put("SmallTheatreIndications", smallTheatres);

        CompoundTag routes = new CompoundTag();
        int routeIndex = 0;
        for (String route : knownRoutes)
            routes.putString(Integer.toString(routeIndex++), route);
        tag.put("KnownRoutes", routes);

        CompoundTag types = new CompoundTag();
        routeTypes.forEach(types::putString);
        tag.put("RouteTypes", types);

        CompoundTag indications = new CompoundTag();
        routeIndications.forEach(indications::putString);
        tag.put("RouteIndications", indications);

        CompoundTag visualPaths = new CompoundTag();
        routeVisualPaths.forEach((routeKey, points) -> {
            CompoundTag pathTag = new CompoundTag();
            for (int i = 0; i < Math.min(points.size(), 256); i++) {
                RouteProbe.PathPoint point = points.get(i);
                CompoundTag pointTag = new CompoundTag();
                pointTag.putDouble("X", point.x());
                pointTag.putDouble("Y", point.y());
                pointTag.putDouble("Z", point.z());
                pathTag.put(Integer.toString(i), pointTag);
            }
            visualPaths.put(routeKey, pathTag);
        });
        tag.put("RouteVisualPaths", visualPaths);

        CompoundTag destinationRuleTag = new CompoundTag();
        int destinationRuleIndex = 0;
        for (Map.Entry<String, String> entry : destinationRules.entrySet()) {
            CompoundTag rule = new CompoundTag();
            rule.putString("Pattern", entry.getKey());
            rule.putString("Theatre", entry.getValue());
            List<String> cells = destinationSmallTheatreRules.getOrDefault(entry.getKey(), blankSmallTheatreIndications());
            CompoundTag small = new CompoundTag();
            for (int i = 0; i < 6; i++)
                small.putString(Integer.toString(i), i < cells.size() ? cells.get(i) : "");
            rule.put("SmallTheatre", small);
            destinationRuleTag.put(Integer.toString(destinationRuleIndex++), rule);
        }
        tag.put("DestinationRules", destinationRuleTag);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        linkedSignal = tag.contains("LinkedSignal") ? BlockPos.of(tag.getLong("LinkedSignal")) : null;
        automaticRouteRange = tag.contains("AutomaticRouteRange") ? Math.max(16, Math.min(256, tag.getInt("AutomaticRouteRange"))) : 128;
        allowNewRoutes = !tag.contains("AllowNewRoutes") || tag.getBoolean("AllowNewRoutes");

        routeLabels.clear();
        if (tag.contains("RouteLabels", Tag.TAG_COMPOUND)) {
            CompoundTag labels = tag.getCompound("RouteLabels");
            for (String key : labels.getAllKeys())
                routeLabels.put(key, labels.getString(key));
        }

        theatreIndications.clear();
        if (tag.contains("TheatreIndications", Tag.TAG_COMPOUND)) {
            CompoundTag theatres = tag.getCompound("TheatreIndications");
            for (String key : theatres.getAllKeys())
                theatreIndications.put(key, normalizeTheatreIndication(theatres.getString(key)));
        }

        smallTheatreIndications.clear();
        if (tag.contains("SmallTheatreIndications", Tag.TAG_COMPOUND)) {
            CompoundTag smallTheatres = tag.getCompound("SmallTheatreIndications");
            for (String routeKey : smallTheatres.getAllKeys()) {
                CompoundTag cells = smallTheatres.getCompound(routeKey);
                List<String> values = new ArrayList<>(6);
                for (int i = 0; i < 6; i++)
                    values.add(normalizeSmallTheatreIndication(cells.getString(Integer.toString(i))));
                smallTheatreIndications.put(routeKey, values);
            }
        }

        // Migration from pre-A7 worlds: preserve the old first-character theatre behaviour once.
        for (Map.Entry<String, String> entry : routeLabels.entrySet()) {
            theatreIndications.computeIfAbsent(entry.getKey(), key -> normalizeTheatreIndication(entry.getValue()));
        }

        knownRoutes.clear();
        if (tag.contains("KnownRoutes", Tag.TAG_COMPOUND)) {
            CompoundTag routes = tag.getCompound("KnownRoutes");
            for (String key : routes.getAllKeys())
                knownRoutes.add(routes.getString(key));
        }
        knownRoutes.addAll(routeLabels.keySet());
        knownRoutes.addAll(theatreIndications.keySet());
        knownRoutes.addAll(smallTheatreIndications.keySet());

        routeTypes.clear();
        routeIndications.clear();
        if (tag.contains("RouteTypes", Tag.TAG_COMPOUND)) {
            CompoundTag types = tag.getCompound("RouteTypes");
            for (String key : types.getAllKeys())
                routeTypes.put(key, types.getString(key));
        }
        if (tag.contains("RouteIndications", Tag.TAG_COMPOUND)) {
            CompoundTag indications = tag.getCompound("RouteIndications");
            for (String key : indications.getAllKeys())
                routeIndications.put(key, indications.getString(key));
        }

        routeVisualPaths.clear();
        if (tag.contains("RouteVisualPaths", Tag.TAG_COMPOUND)) {
            CompoundTag visualPaths = tag.getCompound("RouteVisualPaths");
            for (String routeKey : visualPaths.getAllKeys()) {
                CompoundTag pathTag = visualPaths.getCompound(routeKey);
                List<String> pointKeys = new ArrayList<>(pathTag.getAllKeys());
                pointKeys.sort((a, b) -> Integer.compare(parseRuleIndex(a), parseRuleIndex(b)));
                List<RouteProbe.PathPoint> points = new ArrayList<>();
                for (String pointKey : pointKeys) {
                    if (points.size() >= 256) break;
                    CompoundTag pointTag = pathTag.getCompound(pointKey);
                    points.add(new RouteProbe.PathPoint(pointTag.getDouble("X"), pointTag.getDouble("Y"), pointTag.getDouble("Z")));
                }
                if (!points.isEmpty())
                    routeVisualPaths.put(routeKey, List.copyOf(points));
            }
        }
        knownRoutes.addAll(routeVisualPaths.keySet());

        destinationRules.clear();
        destinationSmallTheatreRules.clear();
        if (tag.contains("DestinationRules", Tag.TAG_COMPOUND)) {
            CompoundTag ruleTags = tag.getCompound("DestinationRules");
            List<String> keys = new ArrayList<>(ruleTags.getAllKeys());
            keys.sort((a, b) -> Integer.compare(parseRuleIndex(a), parseRuleIndex(b)));
            for (String key : keys) {
                CompoundTag rule = ruleTags.getCompound(key);
                String pattern = rule.getString("Pattern").trim();
                if (!pattern.isBlank()) {
                    destinationRules.put(pattern, normalizeTheatreIndication(rule.getString("Theatre")));
                    List<String> smallValues = new ArrayList<>(6);
                    CompoundTag small = rule.contains("SmallTheatre", Tag.TAG_COMPOUND) ? rule.getCompound("SmallTheatre") : new CompoundTag();
                    for (int i = 0; i < 6; i++)
                        smallValues.add(normalizeSmallTheatreIndication(small.getString(Integer.toString(i))));
                    destinationSmallTheatreRules.put(pattern, smallValues);
                }
            }
        }

        snapshotInitialized = false;
    }

    private static int parseRuleIndex(String key) {
        try { return Integer.parseInt(key); } catch (NumberFormatException ignored) { return Integer.MAX_VALUE; }
    }
}
