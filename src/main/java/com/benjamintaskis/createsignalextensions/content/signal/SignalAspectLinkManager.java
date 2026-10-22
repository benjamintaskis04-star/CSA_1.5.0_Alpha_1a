package com.benjamintaskis.createsignalextensions.content.signal;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import com.benjamintaskis.createsignalextensions.config.CommonConfig;
import com.benjamintaskis.createsignalextensions.content.route.RouteProbe;
import com.benjamintaskis.createsignalextensions.content.route.RouteInterfaceBlockEntity;
import com.benjamintaskis.createsignalextensions.content.speed.TrainMovementModeManager;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Persistent links from temporary/future signal displays to Create signals. */
public final class SignalAspectLinkManager extends SavedData {
    private static final String DATA_NAME = "create_signalling_additions_signal_links";
    private static final Factory<SignalAspectLinkManager> FACTORY = new Factory<>(SignalAspectLinkManager::new, SignalAspectLinkManager::load, null);
    private final Map<BlockPos, BlockPos> links = new LinkedHashMap<>(); // display -> Create signal
    private final Map<BlockPos, LinkedHashSet<BlockPos>> followingSignals = new LinkedHashMap<>(); // legacy/direct Create signal -> allowed following signals
    private final Map<BlockPos, LinkedHashSet<BlockPos>> followingDisplays = new LinkedHashMap<>(); // aspect display -> allowed following aspect displays
    // Alpha 4a: converging heads have a user-selected default indication direction.
    // Stored independently of Route Interfaces so converging signals work without one.
    private final Map<BlockPos, TurnoutDirection> convergingDirections = new LinkedHashMap<>();
    // Beta 4: unlinked NSW heads can be put into a persistent display/demo mode.
    // The cycle phase itself is derived from world time, so only membership is saved.
    private final LinkedHashSet<BlockPos> displayModeSignals = new LinkedHashSet<>();
    // Beta 5: optional per-display manual choice for no-active-path look-ahead.
    // Absence means AUTO. Values point to one of the configured following NSW displays.
    private final Map<BlockPos, BlockPos> manualLookAheadDisplays = new LinkedHashMap<>();
    // Beta 5a: transient visual preview of the currently selected/manual or AUTO target.
    // Runtime-only: these markers are deliberately not persisted.
    private final Map<BlockPos, LookAheadPreview> lookAheadPreviews = new LinkedHashMap<>();
    // Alpha 1i: runtime index published by loaded Route Interfaces. Not persisted;
    // RouteInterfaceBlockEntity.lazyTick() repopulates it after every world load.
    private final Map<BlockPos, BlockPos> routeInterfacesBySignal = new LinkedHashMap<>(); // Create signal -> Route Interface
    private int tickCounter;
    private record LookAheadPreview(BlockPos targetDisplay, long expiresAt) {}
    private final Map<BlockPos, String> lastDiagnostics = new LinkedHashMap<>();
    // Alpha 6g1: transient latch for chain signals. Create may return a CROSS signal
    // to RED as soon as its reservation/occupancy bookkeeping changes, before the
    // approaching train has physically crossed the signal. Keep the last permissive
    // NSW indication only while an active train path still reaches this boundary.
    private final Map<BlockPos, SignalReading> lastResolvedReadings = new LinkedHashMap<>();
    // Alpha 6g2: signal-level passage hold shared by every CSA display linked to a
    // Create chain/CROSS signal. This prevents mainline heads, shunts and addon lamps
    // from each making independent replacement decisions while the same train is still
    // approaching the boundary.
    private final Map<BlockPos, Boolean> chainPermissiveSeen = new LinkedHashMap<>();

    public SignalAspectLinkManager() {}

    public static SignalAspectLinkManager get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void link(BlockPos display, BlockPos signal) {
        links.put(display.immutable(), signal.immutable());
        // A real Create-signal input always takes precedence over display mode.
        displayModeSignals.remove(display);
        setDirty();
    }

    public BlockPos linkedSignal(BlockPos display) { return links.get(display); }

    public boolean isDisplayMode(BlockPos display) {
        return displayModeSignals.contains(display);
    }

    /** Toggle Beta 4 display mode. Returns true when enabled afterwards. */
    public boolean toggleDisplayMode(BlockPos display) {
        BlockPos key = display.immutable();
        boolean enabled;
        if (displayModeSignals.remove(key)) {
            enabled = false;
        } else {
            displayModeSignals.add(key);
            enabled = true;
        }
        setDirty();
        return enabled;
    }

    public void disableDisplayMode(BlockPos display) {
        if (displayModeSignals.remove(display)) setDirty();
    }

    /** Beta 3 read-only topology views for the adaptive chunk manager. */
    public Set<BlockPos> linkedDisplays() { return Set.copyOf(links.keySet()); }
    public List<BlockPos> followingDisplaysRaw(BlockPos display) {
        Set<BlockPos> set = followingDisplays.get(display);
        return set == null ? List.of() : List.copyOf(set);
    }
    public BlockPos routeInterfacePosition(BlockPos signal) {
        return signal == null ? null : routeInterfacesBySignal.get(signal);
    }

    /** Register the exact Route Interface providing route metadata for a Create signal. */
    public void registerRouteInterface(BlockPos signal, BlockPos routeInterface) {
        if (signal == null || routeInterface == null) return;
        routeInterfacesBySignal.put(signal.immutable(), routeInterface.immutable());
    }

    private RouteInterfaceBlockEntity routeInterfaceFor(ServerLevel level, BlockPos signalPos) {
        BlockPos riPos = routeInterfacesBySignal.get(signalPos);
        if (riPos == null || !level.hasChunkAt(riPos)) return null;
        if (level.getBlockEntity(riPos) instanceof RouteInterfaceBlockEntity ri
                && signalPos.equals(ri.getLinkedSignalPos()))
            return ri;
        routeInterfacesBySignal.remove(signalPos);
        return null;
    }

    private static TurnoutDirection directionFromRouteType(String routeType) {
        if ("DIVERGING LEFT".equalsIgnoreCase(routeType)) return TurnoutDirection.LEFT;
        if ("DIVERGING RIGHT".equalsIgnoreCase(routeType)) return TurnoutDirection.RIGHT;
        return TurnoutDirection.NONE;
    }

    /** Toggle one specific source -> following-signal relationship. */
    public boolean toggleFollowingSignal(BlockPos source, BlockPos following) {
        BlockPos sourceKey = source.immutable();
        BlockPos followingKey = following.immutable();
        LinkedHashSet<BlockPos> set = followingSignals.computeIfAbsent(sourceKey, ignored -> new LinkedHashSet<>());
        boolean linked;
        if (set.contains(followingKey)) {
            set.remove(followingKey);
            linked = false;
            if (set.isEmpty()) followingSignals.remove(sourceKey);
        } else {
            set.add(followingKey);
            linked = true;
        }
        setDirty();
        return linked;
    }

    public List<BlockPos> followingSignals(BlockPos source) {
        Set<BlockPos> set = followingSignals.get(source);
        return set == null ? List.of() : List.copyOf(set);
    }

    /** Toggle one specific aspect-display -> following aspect-display relationship. */
    public boolean toggleFollowingDisplay(BlockPos sourceDisplay, BlockPos followingDisplay) {
        BlockPos sourceKey = sourceDisplay.immutable();
        BlockPos followingKey = followingDisplay.immutable();
        LinkedHashSet<BlockPos> set = followingDisplays.computeIfAbsent(sourceKey, ignored -> new LinkedHashSet<>());
        boolean linked;
        if (set.contains(followingKey)) {
            set.remove(followingKey);
            linked = false;
            if (set.isEmpty()) followingDisplays.remove(sourceKey);
        } else {
            set.add(followingKey);
            linked = true;
            // A following-display input (notably for distant signals) takes precedence.
            displayModeSignals.remove(sourceKey);
        }
        setDirty();
        return linked;
    }

    public List<BlockPos> followingDisplays(BlockPos sourceDisplay) {
        Set<BlockPos> set = followingDisplays.get(sourceDisplay);
        return set == null ? List.of() : List.copyOf(set);
    }

    public BlockPos manualLookAheadDisplay(BlockPos sourceDisplay) {
        return manualLookAheadDisplays.get(sourceDisplay);
    }

    /**
     * Beta 5 linked-signal configuration. Sneak-right-click cycles
     * AUTO -> each configured following NSW display -> AUTO.
     */
    public String cycleLookAheadMode(BlockPos sourceDisplay) {
        List<BlockPos> options = followingDisplays(sourceDisplay);
        if (options.isEmpty()) {
            manualLookAheadDisplays.remove(sourceDisplay);
            setDirty();
            return "AUTO (no following NSW signal links configured)";
        }

        BlockPos current = manualLookAheadDisplays.get(sourceDisplay);
        if (current == null) {
            manualLookAheadDisplays.put(sourceDisplay.immutable(), options.get(0).immutable());
            setDirty();
            return "MANUAL -> " + options.get(0).toShortString();
        }

        int index = options.indexOf(current);
        if (index < 0 || index + 1 >= options.size()) {
            manualLookAheadDisplays.remove(sourceDisplay);
            setDirty();
            return "AUTO";
        }

        BlockPos next = options.get(index + 1);
        manualLookAheadDisplays.put(sourceDisplay.immutable(), next.immutable());
        setDirty();
        return "MANUAL -> " + next.toShortString();
    }


    /**
     * Beta 5a: show a five-second visual preview of the target selected by the
     * linked-signal look-ahead selector. MANUAL previews the chosen display;
     * AUTO previews the static-topology fallback that would be preferred when
     * no active train path exists.
     */
    public BlockPos showLookAheadPreview(ServerLevel level, BlockPos sourceDisplay) {
        BlockPos target = previewLookAheadDisplay(level, sourceDisplay);
        if (target == null) {
            lookAheadPreviews.remove(sourceDisplay);
            return null;
        }
        lookAheadPreviews.put(sourceDisplay.immutable(),
                new LookAheadPreview(target.immutable(), level.getGameTime() + 100L));
        spawnLookAheadPreview(level, sourceDisplay, target);
        return target;
    }

    private BlockPos previewLookAheadDisplay(ServerLevel level, BlockPos sourceDisplay) {
        List<BlockPos> options = followingDisplays(sourceDisplay);
        if (options.isEmpty()) return null;

        BlockPos manual = manualLookAheadDisplays.get(sourceDisplay);
        if (manual != null && options.contains(manual)) return manual;

        BlockPos sourceSignal = links.get(sourceDisplay);
        if (sourceSignal == null || !level.hasChunkAt(sourceSignal)) return options.get(0);
        if (!(level.getBlockEntity(sourceSignal) instanceof SignalBlockEntity current)) return options.get(0);

        List<BlockPos> candidateSignals = new ArrayList<>();
        for (BlockPos display : options) {
            BlockPos signal = links.get(display);
            if (signal != null && !signal.equals(sourceSignal) && !candidateSignals.contains(signal))
                candidateSignals.add(signal);
        }
        if (candidateSignals.isEmpty()) return options.get(0);
        if (candidateSignals.size() == 1) {
            BlockPos only = candidateSignals.get(0);
            for (BlockPos display : options) if (only.equals(links.get(display))) return display;
            return options.get(0);
        }

        String topologyRouteType = "STRAIGHT";
        RouteInterfaceBlockEntity routeInterface = routeInterfaceFor(level, sourceSignal);
        if (routeInterface != null) {
            String routeType = routeInterface.getSelectedRouteType();
            if ("DIVERGING LEFT".equalsIgnoreCase(routeType) || "DIVERGING RIGHT".equalsIgnoreCase(routeType))
                topologyRouteType = routeType;
        }

        BlockPos selectedSignal = RouteProbe.findNextLinkedSignalByTopology(
                current.getSignal(), sourceSignal, candidateSignals, topologyRouteType, level);
        if (selectedSignal != null) {
            for (BlockPos display : options) if (selectedSignal.equals(links.get(display))) return display;
        }
        return options.get(0);
    }

    private void tickLookAheadPreviews(ServerLevel level) {
        if (lookAheadPreviews.isEmpty()) return;
        long now = level.getGameTime();
        List<BlockPos> expired = new ArrayList<>();
        lookAheadPreviews.forEach((source, preview) -> {
            if (now >= preview.expiresAt() || !level.hasChunkAt(source) || !level.hasChunkAt(preview.targetDisplay())) {
                expired.add(source);
                return;
            }
            spawnLookAheadPreview(level, source, preview.targetDisplay());
        });
        expired.forEach(lookAheadPreviews::remove);
    }

    private static void spawnLookAheadPreview(ServerLevel level, BlockPos source, BlockPos target) {
        // Particle trace from the configuring signal to the selected target.
        double sx = source.getX() + 0.5;
        double sy = source.getY() + 1.0;
        double sz = source.getZ() + 0.5;
        double tx = target.getX() + 0.5;
        double ty = target.getY() + 1.0;
        double tz = target.getZ() + 0.5;
        int steps = 20;
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            level.sendParticles(ParticleTypes.END_ROD,
                    sx + (tx - sx) * t, sy + (ty - sy) * t, sz + (tz - sz) * t,
                    1, 0.0, 0.0, 0.0, 0.0);
        }

        // Stable 0.1.3 Fix: alternating dark/white outline. 5a proved that
        // ELECTRIC_SPARK renders reliably here, so pair it with SMOKE rather
        // than replacing the working preview with custom dust particles.
        int outlineIndex = 0;
        double minX = target.getX() + 0.08, maxX = target.getX() + 0.92;
        double minY = target.getY() + 0.08, maxY = target.getY() + 1.92;
        double minZ = target.getZ() + 0.08, maxZ = target.getZ() + 0.92;
        double[] xs = {minX, minX + (maxX - minX) * 0.25, (minX + maxX) * 0.5, minX + (maxX - minX) * 0.75, maxX};
        double[] ys = {minY, minY + (maxY - minY) * 0.25, (minY + maxY) * 0.5, minY + (maxY - minY) * 0.75, maxY};
        double[] zs = {minZ, minZ + (maxZ - minZ) * 0.25, (minZ + maxZ) * 0.5, minZ + (maxZ - minZ) * 0.75, maxZ};
        for (double x : xs) {
            outlineIndex = spawnAlternatingOutlineParticle(level, outlineIndex, x, minY, minZ);
            outlineIndex = spawnAlternatingOutlineParticle(level, outlineIndex, x, minY, maxZ);
            outlineIndex = spawnAlternatingOutlineParticle(level, outlineIndex, x, maxY, minZ);
            outlineIndex = spawnAlternatingOutlineParticle(level, outlineIndex, x, maxY, maxZ);
        }
        for (double y : ys) {
            outlineIndex = spawnAlternatingOutlineParticle(level, outlineIndex, minX, y, minZ);
            outlineIndex = spawnAlternatingOutlineParticle(level, outlineIndex, minX, y, maxZ);
            outlineIndex = spawnAlternatingOutlineParticle(level, outlineIndex, maxX, y, minZ);
            outlineIndex = spawnAlternatingOutlineParticle(level, outlineIndex, maxX, y, maxZ);
        }
        for (double z : zs) {
            outlineIndex = spawnAlternatingOutlineParticle(level, outlineIndex, minX, minY, z);
            outlineIndex = spawnAlternatingOutlineParticle(level, outlineIndex, minX, maxY, z);
            outlineIndex = spawnAlternatingOutlineParticle(level, outlineIndex, maxX, minY, z);
            outlineIndex = spawnAlternatingOutlineParticle(level, outlineIndex, maxX, maxY, z);
        }
    }

    private static int spawnAlternatingOutlineParticle(ServerLevel level, int index, double x, double y, double z) {
        level.sendParticles((index & 1) == 0 ? ParticleTypes.SMOKE : ParticleTypes.ELECTRIC_SPARK,
                x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        return index + 1;
    }

    public TurnoutDirection convergingDirection(BlockPos display) {
        return convergingDirections.getOrDefault(display, TurnoutDirection.LEFT);
    }

    public void setConvergingDirection(BlockPos display, TurnoutDirection direction) {
        if (display == null || direction == null || direction == TurnoutDirection.NONE) return;
        convergingDirections.put(display.immutable(), direction);
        setDirty();
    }

    /** Beta 3: direction currently represented by a double-colour turnout aspect. */
    public TurnoutDirection displayedTurnoutDirection(ServerLevel level, BlockPos display) {
        if (display == null || level == null || !level.hasChunkAt(display)) return TurnoutDirection.NONE;
        BlockState state = level.getBlockState(display);
        if (!(state.getBlock() instanceof DoubleColourLightSignalBlock signal)) return TurnoutDirection.NONE;
        DoubleColourDisplayedAspect shown = state.getValue(DoubleColourLightSignalBlock.ASPECT);
        if (shown != DoubleColourDisplayedAspect.CAUTION_TURNOUT
                && shown != DoubleColourDisplayedAspect.MEDIUM_TURNOUT) return TurnoutDirection.NONE;

        if (signal.physicalType() == DoubleColourLightSignalBlock.PhysicalType.CONVERGING)
            return TurnoutDirection.LEFT;

        BlockPos createSignal = links.get(display);
        RouteInterfaceBlockEntity routeInterface = createSignal == null ? null : routeInterfaceFor(level, createSignal);
        return routeInterface == null ? TurnoutDirection.NONE : directionFromRouteType(routeInterface.getSelectedRouteType());
    }

    /**
     * Alpha 2g: union the following-display links from every aspect display that
     * protects the same Create signal. This makes following-signal topology a
     * property of the protected railway signal instead of an individual model.
     */
    private List<BlockPos> followingDisplaysForSignal(BlockPos sourceSignal) {
        LinkedHashSet<BlockPos> pooled = new LinkedHashSet<>();
        for (Map.Entry<BlockPos, BlockPos> entry : links.entrySet()) {
            if (!sourceSignal.equals(entry.getValue())) continue;
            Set<BlockPos> set = followingDisplays.get(entry.getKey());
            if (set != null) pooled.addAll(set);
        }
        return pooled.isEmpty() ? List.of() : List.copyOf(pooled);
    }

    public static SignalAspectLinkManager load(CompoundTag tag, HolderLookup.Provider registries) {
        SignalAspectLinkManager data = new SignalAspectLinkManager();
        ListTag list = tag.getList("Links", Tag.TAG_COMPOUND);
        for (Tag raw : list) {
            CompoundTag e = (CompoundTag) raw;
            data.links.put(BlockPos.of(e.getLong("Display")), BlockPos.of(e.getLong("Signal")));
        }
        ListTag followingList = tag.getList("FollowingSignals", Tag.TAG_COMPOUND);
        for (Tag raw : followingList) {
            CompoundTag e = (CompoundTag) raw;
            BlockPos source = BlockPos.of(e.getLong("Source"));
            ListTag targets = e.getList("Targets", Tag.TAG_COMPOUND);
            LinkedHashSet<BlockPos> set = new LinkedHashSet<>();
            for (Tag targetRaw : targets) {
                CompoundTag target = (CompoundTag) targetRaw;
                set.add(BlockPos.of(target.getLong("Pos")));
            }
            if (!set.isEmpty()) data.followingSignals.put(source, set);
        }
        ListTag convergingDirectionList = tag.getList("ConvergingDirections", Tag.TAG_COMPOUND);
        for (Tag raw : convergingDirectionList) {
            CompoundTag e = (CompoundTag) raw;
            BlockPos display = BlockPos.of(e.getLong("Display"));
            String rawDirection = e.getString("Direction");
            TurnoutDirection direction = "RIGHT".equalsIgnoreCase(rawDirection) ? TurnoutDirection.RIGHT : TurnoutDirection.LEFT;
            data.convergingDirections.put(display, direction);
        }
        ListTag displayModeList = tag.getList("DisplayModeSignals", Tag.TAG_COMPOUND);
        for (Tag raw : displayModeList) {
            CompoundTag e = (CompoundTag) raw;
            data.displayModeSignals.add(BlockPos.of(e.getLong("Display")));
        }

        ListTag displayFollowingList = tag.getList("FollowingDisplays", Tag.TAG_COMPOUND);
        for (Tag raw : displayFollowingList) {
            CompoundTag e = (CompoundTag) raw;
            BlockPos source = BlockPos.of(e.getLong("SourceDisplay"));
            ListTag targets = e.getList("Targets", Tag.TAG_COMPOUND);
            LinkedHashSet<BlockPos> set = new LinkedHashSet<>();
            for (Tag targetRaw : targets) {
                CompoundTag target = (CompoundTag) targetRaw;
                set.add(BlockPos.of(target.getLong("Pos")));
            }
            if (!set.isEmpty()) data.followingDisplays.put(source, set);
        }
        ListTag manualLookAheadList = tag.getList("ManualLookAheadDisplays", Tag.TAG_COMPOUND);
        for (Tag raw : manualLookAheadList) {
            CompoundTag e = (CompoundTag) raw;
            data.manualLookAheadDisplays.put(
                    BlockPos.of(e.getLong("SourceDisplay")),
                    BlockPos.of(e.getLong("TargetDisplay")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        links.forEach((display, signal) -> {
            CompoundTag e = new CompoundTag();
            e.putLong("Display", display.asLong());
            e.putLong("Signal", signal.asLong());
            list.add(e);
        });
        tag.put("Links", list);

        ListTag followingList = new ListTag();
        followingSignals.forEach((source, targets) -> {
            CompoundTag e = new CompoundTag();
            e.putLong("Source", source.asLong());
            ListTag targetList = new ListTag();
            for (BlockPos target : targets) {
                CompoundTag t = new CompoundTag();
                t.putLong("Pos", target.asLong());
                targetList.add(t);
            }
            e.put("Targets", targetList);
            followingList.add(e);
        });
        tag.put("FollowingSignals", followingList);

        ListTag displayFollowingList = new ListTag();
        followingDisplays.forEach((source, targets) -> {
            CompoundTag e = new CompoundTag();
            e.putLong("SourceDisplay", source.asLong());
            ListTag targetList = new ListTag();
            for (BlockPos target : targets) {
                CompoundTag t = new CompoundTag();
                t.putLong("Pos", target.asLong());
                targetList.add(t);
            }
            e.put("Targets", targetList);
            displayFollowingList.add(e);
        });
        tag.put("FollowingDisplays", displayFollowingList);

        ListTag manualLookAheadList = new ListTag();
        manualLookAheadDisplays.forEach((source, target) -> {
            CompoundTag e = new CompoundTag();
            e.putLong("SourceDisplay", source.asLong());
            e.putLong("TargetDisplay", target.asLong());
            manualLookAheadList.add(e);
        });
        tag.put("ManualLookAheadDisplays", manualLookAheadList);

        ListTag convergingDirectionList = new ListTag();
        convergingDirections.forEach((display, direction) -> {
            CompoundTag e = new CompoundTag();
            e.putLong("Display", display.asLong());
            e.putString("Direction", direction.name());
            convergingDirectionList.add(e);
        });
        tag.put("ConvergingDirections", convergingDirectionList);

        ListTag displayModeList = new ListTag();
        for (BlockPos display : displayModeSignals) {
            CompoundTag e = new CompoundTag();
            e.putLong("Display", display.asLong());
            displayModeList.add(e);
        }
        tag.put("DisplayModeSignals", displayModeList);
        return tag;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        SignalAspectLinkManager data = get(server.overworld());
        SignallingChunkManager.tick(server, data);
        if (++data.tickCounter % CommonConfig.signalRefreshTicks() != 0) return;
        data.tickLookAheadPreviews(server.overworld());
        data.refresh(server);
    }

    private boolean hasLinkedAddon(ServerLevel level, BlockPos signalPos, MainlineAddonLampBlock.Kind kind) {
        for (var e : links.entrySet()) {
            if (!signalPos.equals(e.getValue()) || !level.hasChunkAt(e.getKey())) continue;
            if (level.getBlockState(e.getKey()).getBlock() instanceof MainlineAddonLampBlock lamp && lamp.contains(level.getBlockState(e.getKey()), kind)) return true;
        }
        return false;
    }

    private void refresh(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<BlockPos> stale = new ArrayList<>();
        // Beta 1a: signal-level, refresh-scoped caches. Multiple physical CSA heads can
        // share one Create signal; do not repeat identical route scans for every head.
        Map<BlockPos, BlockPos> activeNextSignalCache = new HashMap<>();
        Set<BlockPos> activeNextSignalCached = new HashSet<>();
        Map<BlockPos, Train> approachingTrainCache = new HashMap<>();
        Set<BlockPos> approachingTrainCached = new HashSet<>();
        // Beta 3 Dev 1: shared-signal refresh caches. followingDisplaysForSignal()
        // scans the complete display->signal link table, so calculate that pooled
        // topology only once for each Create signal during this refresh. Likewise,
        // Dead End addon presence is a signal-level property for the duration of a tick.
        Map<BlockPos, List<BlockPos>> followingDisplayPoolCache = new HashMap<>();
        Map<BlockPos, Boolean> deadEndAddonCache = new HashMap<>();

        for (var entry : links.entrySet()) {
            BlockPos displayPos = entry.getKey();
            BlockPos signalPos = entry.getValue();
            if (!level.hasChunkAt(displayPos)) continue;
            if (!level.hasChunkAt(signalPos)) {
                applySignalOutput(level, displayPos, SignalReading.stop());
                continue;
            }
            if (!isAspectDisplay(level, displayPos)) {
                stale.add(displayPos);
                continue;
            }
            if (!(level.getBlockEntity(signalPos) instanceof SignalBlockEntity current)) {
                applySignalOutput(level, displayPos, SignalReading.stop());
                stale.add(displayPos);
                continue;
            }
            // Beta 3 Dev 1: one block-state lookup per display evaluation.
            BlockState displayState = level.getBlockState(displayPos);

            // Alpha 2g: following relationships belong to the protecting Create signal,
            // not to one particular physical NSW head. Multiple physical heads can be
            // linked to the same Create signal (for example a ground and elevated test
            // head). Pool every following-display relationship from all heads protecting
            // this same Create signal so they all resolve the same downstream candidates.
            List<BlockPos> linkedDisplays = followingDisplayPoolCache.computeIfAbsent(
                    signalPos, this::followingDisplaysForSignal);
            boolean endOfLineFollowing = false;
            List<BlockPos> candidates = new ArrayList<>();
            if (!linkedDisplays.isEmpty()) {
                for (BlockPos targetDisplay : linkedDisplays) {
                    if (level.hasChunkAt(targetDisplay) && level.getBlockState(targetDisplay).getBlock() instanceof EndOfLineMarkerBlock) {
                        endOfLineFollowing = true;
                        continue;
                    }
                    BlockPos targetSignal = links.get(targetDisplay);
                    if (targetSignal != null && !targetSignal.equals(signalPos) && !candidates.contains(targetSignal))
                        candidates.add(targetSignal);
                }
            }
            // Keep Alpha 1e/1f direct-signal relationships working and merge them with
            // display-based links rather than using them only when no display links exist.
            for (BlockPos targetSignal : followingSignals(signalPos)) {
                if (targetSignal != null && !targetSignal.equals(signalPos) && !candidates.contains(targetSignal))
                    candidates.add(targetSignal);
            }
            // Alpha 1i: use the Route Interface attached to THIS protecting signal
            // as the authoritative turnout metadata source. The RI already knows the
            // train's selected route and any LEFT/RIGHT classification configured in
            // the Route Map.
            RouteInterfaceBlockEntity routeInterface = routeInterfaceFor(level, signalPos);
            String routeType = "NONE";
            String routeKey = "";
            TurnoutDirection routeDirection = TurnoutDirection.NONE;
            boolean routeResolved = false;
            if (routeInterface != null) {
                RouteProbe.Result riRoute = routeInterface.getRouteProbeResult();
                routeResolved = riRoute != null && riRoute.resolved();
                routeKey = riRoute == null ? "" : riRoute.routeKey();
                routeType = routeInterface.getSelectedRouteType();
                routeDirection = directionFromRouteType(routeType);
            }

            boolean currentIsChain = SignalAspectResolver.isChain(current);
            // Alpha 6g2: compute chain passage authority once per linked Create signal,
            // then let every display consume the same effective stop state. A permissive
            // Create state arms the boundary; if Create returns it to RED early, retain
            // authority only while an active train path still reaches that boundary.
            if (currentIsChain && !SignalAspectResolver.isStop(current))
                chainPermissiveSeen.put(signalPos.immutable(), true);
            Train sharedChainTrain = null;
            if (currentIsChain && SignalAspectResolver.isStop(current)
                    && Boolean.TRUE.equals(chainPermissiveSeen.get(signalPos))) {
                if (!approachingTrainCached.contains(signalPos)) {
                    approachingTrainCache.put(signalPos.immutable(), RouteProbe.findApproachingTrain(current.getSignal(), signalPos));
                    approachingTrainCached.add(signalPos.immutable());
                }
                sharedChainTrain = approachingTrainCache.get(signalPos);
            }
            boolean sharedChainPassageHold = sharedChainTrain != null;
            if (currentIsChain && SignalAspectResolver.isStop(current) && !sharedChainPassageHold)
                chainPermissiveSeen.remove(signalPos);
            // Dev 2: physical trackside aspects must reflect the linked Create
            // boundary. A train-specific chain passage hold is not public authority.
            // Never allow that hold to turn an occupied boundary permissive.
            boolean effectiveStop = SignalAspectResolver.isStop(current);

            // 1.4.1: The NSW two-aspect head is a boundary-only indicator.
            // Do not resolve following signals, routes, or NSW look-ahead for this
            // display: the linked Create signal alone grants STOP/PROCEED.
            if (displayState.getBlock() instanceof SingleColourLightSignalBlock twoAspectBlock
                    && twoAspectBlock.physicalType() == SingleColourLightSignalBlock.PhysicalType.TWO_ASPECT) {
                SignalReading twoAspectReading = effectiveStop ? SignalReading.stop() : SignalReading.proceed();
                lastResolvedReadings.put(displayPos.immutable(), twoAspectReading);
                applySingleColourOutput(level, displayPos, twoAspectReading);
                continue;
            }

            // Alpha 7: LED repeater has only STOP and PROCEED. It consumes the same
            // effective stop decision as the other displays, including Alpha 6g2 chain passage hold.
            if (displayState.getBlock() instanceof RepeaterSignalBlock) {
                BlockState repeaterState = displayState;
                boolean proceed = !effectiveStop;
                if (repeaterState.getValue(RepeaterSignalBlock.PROCEED) != proceed)
                    level.setBlock(displayPos, repeaterState.setValue(RepeaterSignalBlock.PROCEED, proceed), 3);
                continue;
            }

            // Alpha 6: addon lamps are ordinary Create-signal-linked displays, but their
            // illumination comes from the selected Route Interface route indication.
            if (displayState.getBlock() instanceof MainlineAddonLampBlock lamp) {
                String indication = routeInterface == null ? "NORMAL" : routeInterface.getSelectedRouteIndication();
                boolean authority = !effectiveStop && routeResolved;
                BlockState addonState = displayState;
                // Alpha 6e: the addon follows this mainline signal's selected SHUNT authority only.
                // Do not borrow RUNNING_PROCEED from another physical shunt head linked to the
                // same Create boundary; that produced invalid MEDIUM+SHUNT / normal+SHUNT displays.
                boolean shuntLit = authority && lamp.contains(addonState, MainlineAddonLampBlock.Kind.SHUNT)
                        && "SHUNT".equals(indication);
                boolean deadEndLit = authority && lamp.contains(addonState, MainlineAddonLampBlock.Kind.DEAD_END) && "DEAD_END".equals(indication);
                boolean lit = shuntLit || deadEndLit;
                if (addonState.getValue(MainlineAddonLampBlock.LIT) != lit
                        || addonState.getValue(MainlineAddonLampBlock.SHUNT_LIT) != shuntLit
                        || addonState.getValue(MainlineAddonLampBlock.DEAD_END_LIT) != deadEndLit)
                    level.setBlock(displayPos, addonState.setValue(MainlineAddonLampBlock.LIT, lit)
                            .setValue(MainlineAddonLampBlock.SHUNT_LIT, shuntLit)
                            .setValue(MainlineAddonLampBlock.DEAD_END_LIT, deadEndLit), 3);
                continue;
            }

            BlockPos nextPos = null;
            String nextSelection = "NONE";
            boolean endOfLineSelected = endOfLineFollowing && candidates.isEmpty();
            if (endOfLineSelected) {
                nextSelection = "END_OF_LINE";
            } else if (candidates.size() == 1) {
                nextPos = candidates.get(0);
                nextSelection = "ONLY";
            } else if (candidates.size() > 1) {
                // Beta 5 priority:
                // 1) an active Create train path is authoritative;
                // 2) otherwise honour a valid manual look-ahead selection;
                // 3) otherwise use static topology, preferring the least-deviating
                //    route (STRAIGHT unless the Route Interface positively identifies
                //    a left/right divergence).
                if (!activeNextSignalCached.contains(signalPos)) {
                    activeNextSignalCache.put(signalPos.immutable(),
                            RouteProbe.findNextLinkedSignal(current.getSignal(), signalPos, candidates, level));
                    activeNextSignalCached.add(signalPos.immutable());
                }
                nextPos = activeNextSignalCache.get(signalPos);
                if (nextPos != null) {
                    nextSelection = "ACTIVE_PATH";
                } else {
                    BlockPos manualDisplay = manualLookAheadDisplays.get(displayPos);
                    BlockPos manualSignal = manualDisplay == null ? null : links.get(manualDisplay);
                    if (manualSignal != null && candidates.contains(manualSignal)) {
                        nextPos = manualSignal;
                        nextSelection = "MANUAL";
                    } else {
                        if (manualDisplay != null) {
                            manualLookAheadDisplays.remove(displayPos);
                            setDirty();
                        }
                        String topologyRouteType = routeType != null
                                && (routeType.equalsIgnoreCase("DIVERGING LEFT")
                                || routeType.equalsIgnoreCase("DIVERGING RIGHT"))
                                ? routeType : "STRAIGHT";
                        nextPos = RouteProbe.findNextLinkedSignalByTopology(
                                current.getSignal(), signalPos, candidates, topologyRouteType, level);
                        if (nextPos != null) nextSelection = "AUTO_TOPOLOGY";
                    }
                }
            }
            // 0.1.3a: chain-linked NSW heads need a passive/default route even when
            // no train currently owns a navigation path.  Follow the straight continuation
            // through unlinked Create chain signals until the first NSW-controlled signal
            // or first ordinary (non-chain) Create signal is reached.
            PassiveChainTarget passiveTarget = null;
            boolean activeDivergingRoute = routeResolved
                    && (routeDirection == TurnoutDirection.LEFT || routeDirection == TurnoutDirection.RIGHT);
            boolean activeSelectedPath = "ACTIVE_PATH".equals(nextSelection);
            boolean manualSelectedPath = "MANUAL".equals(nextSelection);
            if (currentIsChain && !activeDivergingRoute && !activeSelectedPath && !manualSelectedPath) {
                passiveTarget = resolvePassiveStraightTarget(level, current);
                if (passiveTarget != null) {
                    nextPos = passiveTarget.signalPos();
                    nextSelection = passiveTarget.nswAspect() != null
                            ? "PASSIVE_STRAIGHT_NSW" : "PASSIVE_STRAIGHT_CREATE";
                }
            }

            SignalBlockEntity next = nextPos != null && level.getBlockEntity(nextPos) instanceof SignalBlockEntity s ? s : null;

            SignalAspect nextNswAspect = passiveTarget != null && passiveTarget.nswAspect() != null
                    ? passiveTarget.nswAspect() : logicalAspectShownForSignal(level, nextPos);
            SignalReading reading;
            // 0.1.4 Alpha 1b: ground shunts use a dedicated two-aspect resolver.
            // The previous Alpha 1a output was being immediately forced back to STOP
            // by GroundShuntSignalBlock.onPlace() when the ASPECT blockstate changed.
            // Placement already defaults to STOP, so the manager can now own every
            // subsequent indication change.
            if (displayState.getBlock() instanceof GroundShuntSignalBlock) {
                boolean shuntStop = effectiveStop;
                reading = shuntStop ? SignalReading.stop() : SignalReading.proceed();

                boolean runningEquipped = displayState.getBlock() instanceof GroundRunningShuntSignalBlock
                        || displayState.getBlock() instanceof PostRunningShuntSignalBlock
                        || displayState.getBlock() instanceof HangingRunningShuntSignalBlock;
                String routeIndication = routeInterface == null ? "NORMAL" : routeInterface.getSelectedRouteIndication();
                boolean routeShuntAuthority = runningEquipped && !shuntStop && routeResolved
                        && "SHUNT".equals(routeIndication);
                // Alpha 6f1: an established Route Interface SHUNT route is explicit shunt
                // authority for a Running Shunt protecting the same Create signal.  This
                // keeps the Running Shunt indication in step with the Mainline Shunt lamp
                // even before an approaching train has entered the shunting movement mode.
                // Outside an established SHUNT route, preserve the existing train-mode
                // resolver unchanged.
                boolean shuntMovement = !runningEquipped || routeShuntAuthority;
                if (runningEquipped && !shuntStop && !routeShuntAuthority) {
                    if (!approachingTrainCached.contains(signalPos)) {
                        approachingTrainCache.put(signalPos.immutable(), RouteProbe.findApproachingTrain(current.getSignal(), signalPos));
                        approachingTrainCached.add(signalPos.immutable());
                    }
                    Train approaching = approachingTrainCache.get(signalPos);
                    if (approaching != null) {
                        if (TrainMovementModeManager.isPending(level, approaching))
                            TrainMovementModeManager.activateIfPending(level, approaching);
                        shuntMovement = TrainMovementModeManager.isShunting(level, approaching);
                    }
                }
                ShuntDisplayedAspect resolved = shuntStop
                        ? ShuntDisplayedAspect.STOP
                        : (runningEquipped && !shuntMovement ? ShuntDisplayedAspect.RUNNING_PROCEED : ShuntDisplayedAspect.SHUNT_PROCEED);
                // Beta 3 Dev 1: diagnostic strings are allocation-heavy; do not build
                // or retain them at all unless signal debugging is actually enabled.
                if (CommonConfig.signalDebug()) {
                    String diagnostic = "linked=true"
                            + ", createState=" + SignalAspectResolver.createState(current)
                            + ", occupied=" + SignalAspectResolver.isProtectedBlockOccupied(current)
                            + ", routeIndication=" + routeIndication
                            + ", routeResolved=" + routeResolved
                            + ", routeShuntAuthority=" + routeShuntAuthority
                            + ", chainPassageHold=" + sharedChainPassageHold
                            + ", chainTrain=" + (sharedChainTrain == null ? "NONE" : (sharedChainTrain.name == null ? sharedChainTrain.id : sharedChainTrain.name.getString()))
                            + ", resolved=" + resolved.getSerializedName();
                    String previous = lastDiagnostics.put(displayPos, diagnostic);
                    if (!diagnostic.equals(previous)) {
                        CreateSignallingAdditions.LOGGER.debug("Ground shunt {} linked to Create signal {}: {}",
                                displayPos, signalPos, diagnostic);
                    }
                }
                if (displayState.getValue(GroundShuntSignalBlock.ASPECT) != resolved)
                    level.setBlock(displayPos, displayState.setValue(GroundShuntSignalBlock.ASPECT, resolved), 3);
                continue;
            }

            String specialIndication = routeInterface == null ? "NORMAL" : routeInterface.getSelectedRouteIndication();
            boolean specialAuthority = routeResolved && !effectiveStop;
            boolean shuntRoute = specialAuthority && "SHUNT".equals(specialIndication);
            boolean deadEndRoute = specialAuthority && "DEAD_END".equals(specialIndication);
            boolean deadEndLampPresent = deadEndRoute && deadEndAddonCache.computeIfAbsent(
                    signalPos, pos -> hasLinkedAddon(level, pos, MainlineAddonLampBlock.Kind.DEAD_END));

            boolean singleConverging = displayState.getBlock() instanceof SingleColourLightSignalBlock single
                    && single.physicalType() == SingleColourLightSignalBlock.PhysicalType.CONVERGING;
            boolean doubleConverging = displayState.getBlock() instanceof DoubleColourLightSignalBlock dual
                    && dual.physicalType() == DoubleColourLightSignalBlock.PhysicalType.CONVERGING;
            boolean converging = singleConverging || doubleConverging;
            if (converging) {
                // Converging heads do not require Route Interface metadata. Their configured
                // default LEFT/RIGHT direction is purely the physical diagonal indication.
                // Signal progression remains normal: own STOP is absolute; otherwise a STOP
                // ahead gives steady Caution Turnout and anything else gives flashing Medium Turnout.
                TurnoutDirection configuredDirection = singleConverging ? convergingDirection(displayPos) : TurnoutDirection.LEFT;
                if (SignalAspectResolver.isStop(current)) {
                    reading = SignalReading.stop();
                } else {
                    boolean nextIsStop = endOfLineSelected || (next != null && SignalAspectResolver.isStop(next));
                    if (nextNswAspect == SignalAspect.STOP) nextIsStop = true;
                    reading = nextIsStop ? SignalReading.cautionTurnout(configuredDirection)
                            : SignalReading.mediumTurnout(configuredDirection);
                }
                routeDirection = configuredDirection;
                routeType = "CONVERGING " + configuredDirection.name();
                routeKey = "";
            } else if (endOfLineSelected) {
                // A linked End of Line Marker is a terminal protected endpoint.
                // STOP at the protecting Create signal remains absolute; otherwise the
                // signal warns of the terminal route with Caution/Caution Turnout.
                if (SignalAspectResolver.isStop(current))
                    reading = SignalReading.stop();
                else if (routeDirection == TurnoutDirection.LEFT || routeDirection == TurnoutDirection.RIGHT)
                    reading = SignalReading.cautionTurnout(routeDirection);
                else
                    reading = SignalReading.caution();
            } else if (currentIsChain && !activeDivergingRoute && !activeSelectedPath
                    && !manualSelectedPath && passiveTarget != null) {
                // A passive Create chain signal is a route-availability indication, not a
                // one-block advance-warning signal.  Mirror the downstream authority:
                // an NSW head wins if present; otherwise the first non-chain Create signal
                // determines STOP versus proceed.  This is what makes a blocked straight
                // route display STOP until a real diverging train path is established.
                if (SignalAspectResolver.isStop(current)) {
                    reading = SignalReading.stop();
                } else if (passiveTarget.nswAspect() != null) {
                    // 0.1.3a2: once passive look-ahead reaches a Create signal that is
                    // controlled by another NSW head, hand back to the normal NSW
                    // following-signal progression.  The passive chain override exists
                    // only for otherwise-uncontrolled Create signals.
                    reading = SignalAspectResolver.resolve(
                            current, passiveTarget.createSignal(), TurnoutDirection.NONE,
                            passiveTarget.nswAspect());
                } else if (passiveTarget.createSignal() != null
                        && SignalAspectResolver.isStop(passiveTarget.createSignal())) {
                    reading = SignalReading.stop();
                } else {
                    reading = SignalReading.proceed();
                }
            } else {
                reading = SignalAspectResolver.resolve(current, next, routeDirection, nextNswAspect);
            }

            if (shuntRoute || deadEndLampPresent) {
                reading = SignalReading.stop();
            } else if (deadEndRoute) {
                reading = routeDirection == TurnoutDirection.NONE ? SignalReading.caution() : SignalReading.cautionTurnout(routeDirection);
            }

            // Dev 2: preserve the chain diagnostics, but do not restore a previous
            // permissive indication when the linked Create signal is now STOP.
            // Route look-ahead can only refine a permitted movement, never override STOP.
            boolean chainPassageHold = sharedChainPassageHold;
            Train chainApproachingTrain = sharedChainTrain;
            if (SignalAspectResolver.isStop(current))
                reading = SignalReading.stop();

            // Beta 3 Dev 1: this was previously constructed for every mainline head
            // on every refresh even with signal_debug=false. Keep identical diagnostics
            // when requested, but remove the normal-runtime string/allocation cost.
            if (CommonConfig.signalDebug()) {
                String diagnostic = "current=" + SignalAspectResolver.createState(current)
                        + ", protectedOccupied=" + SignalAspectResolver.isProtectedBlockOccupied(current)
                        + ", followingLinks=" + candidates.size()
                        + ", next=" + SignalAspectResolver.createState(next)
                        + (nextPos != null ? "@" + nextPos.toShortString() : "")
                        + ", nextSelection=" + nextSelection
                        + ", nextNswAspect=" + (nextNswAspect == null ? "NONE" : nextNswAspect)
                        + ", routeInterface=" + (routeInterface == null ? "NONE" : routeInterface.getBlockPos().toShortString())
                        + ", route='" + (routeKey == null ? "" : routeKey) + "'"
                        + ", routeType=" + routeType
                        + ", routeResolved=" + routeResolved
                        + ", chain=" + currentIsChain
                        + ", chainPassageHold=" + chainPassageHold
                        + ", chainTrain=" + (chainApproachingTrain == null ? "NONE" : (chainApproachingTrain.name == null ? chainApproachingTrain.id : chainApproachingTrain.name.getString()))
                        + ", passiveSkippedChains=" + (passiveTarget == null ? 0 : passiveTarget.skippedChains())
                        + ", direction=" + reading.direction()
                        + ", resolved=" + reading.aspect();
                String previous = lastDiagnostics.put(displayPos, diagnostic);
                if (!diagnostic.equals(previous)) {
                    CreateSignallingAdditions.LOGGER.debug("Aspect signal {} linked to {}: {}",
                            displayPos, signalPos, diagnostic);
                }
            }

            lastResolvedReadings.put(displayPos.immutable(), reading);
            applySignalOutput(level, displayPos, reading);
            applyTemporaryNixieOutput(level, displayPos, reading);
        }
        // Alpha 4: distant signals do not protect (and therefore do not link to)
        // a Create signal. Their sole input is a direct following-NSW-signal link.
        // Read that following head's physical/logical indication and translate it
        // into the distant's C/M/P sequence. No following link, a missing target,
        // or an unlinked target leaves the offset yellow UNLINKED marker illuminated.
        for (Map.Entry<BlockPos, LinkedHashSet<BlockPos>> entry : followingDisplays.entrySet()) {
            BlockPos distantPos = entry.getKey();
            if (links.containsKey(distantPos) || !level.hasChunkAt(distantPos)) continue;
            var distantState = level.getBlockState(distantPos);
            if (!(distantState.getBlock() instanceof SingleColourLightSignalBlock distantBlock)
                    || distantBlock.physicalType() != SingleColourLightSignalBlock.PhysicalType.DISTANT)
                continue;

            BlockPos followingDisplay = entry.getValue().stream().findFirst().orElse(null);
            SignalAspect followingAspect = logicalAspectShownByDisplay(level, followingDisplay);
            if (followingAspect == null) {
                setSingleColourDisplayedAspect(level, distantPos, SingleColourDisplayedAspect.UNLINKED);
                continue;
            }

            SignalAspect distantAspect = switch (followingAspect) {
                case STOP -> SignalAspect.CAUTION;
                case CAUTION, CAUTION_TURNOUT -> SignalAspect.MEDIUM;
                case MEDIUM, PRELIMINARY_MEDIUM, MEDIUM_TURNOUT, PROCEED -> SignalAspect.PROCEED;
            };
            applySingleColourOutput(level, distantPos, new SignalReading(distantAspect, TurnoutDirection.NONE));
        }

        if (!stale.isEmpty()) { stale.forEach(pos -> { links.remove(pos); displayModeSignals.remove(pos); manualLookAheadDisplays.remove(pos); lastDiagnostics.remove(pos); }); setDirty(); }
    }

    private static void setSingleColourDisplayedAspect(ServerLevel level, BlockPos pos, SingleColourDisplayedAspect aspect) {
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SingleColourLightSignalBlock)) return;
        if (state.getValue(SingleColourLightSignalBlock.ASPECT) != aspect)
            level.setBlock(pos, state.setValue(SingleColourLightSignalBlock.ASPECT, aspect), 3);
    }

    private static SignalAspect logicalAspectShownByDisplay(ServerLevel level, BlockPos display) {
        if (display == null || !level.hasChunkAt(display)) return null;
        var state = level.getBlockState(display);
        if (state.getBlock() instanceof SingleColourLightSignalBlock) {
            return switch (state.getValue(SingleColourLightSignalBlock.ASPECT)) {
                case UNLINKED, DARK, OUT_OF_USE -> null;
                case STOP -> SignalAspect.STOP;
                case CAUTION -> SignalAspect.CAUTION;
                case MEDIUM_ON, MEDIUM_OFF -> SignalAspect.MEDIUM;
                case TURNOUT_LEFT, TURNOUT_RIGHT -> SignalAspect.CAUTION_TURNOUT;
                case MEDIUM_TURNOUT_LEFT_ON, MEDIUM_TURNOUT_LEFT_OFF,
                     MEDIUM_TURNOUT_RIGHT_ON, MEDIUM_TURNOUT_RIGHT_OFF -> SignalAspect.MEDIUM_TURNOUT;
                case PROCEED -> SignalAspect.PROCEED;
            };
        }
        if (state.getBlock() instanceof DoubleColourLightSignalBlock) {
            return switch (state.getValue(DoubleColourLightSignalBlock.ASPECT)) {
                case DARK, OUT_OF_USE -> null;
                case STOP -> SignalAspect.STOP;
                case CAUTION -> SignalAspect.CAUTION;
                case MEDIUM -> SignalAspect.MEDIUM;
                case PRELIMINARY_MEDIUM_ON, PRELIMINARY_MEDIUM_OFF -> SignalAspect.PRELIMINARY_MEDIUM;
                case CAUTION_TURNOUT -> SignalAspect.CAUTION_TURNOUT;
                case MEDIUM_TURNOUT -> SignalAspect.MEDIUM_TURNOUT;
                case PROCEED -> SignalAspect.PROCEED;
            };
        }
        if (state.getBlock() instanceof GroundShuntSignalBlock) {
            // Alpha 1: SHUNT PROCEED is a distinct physical indication, but for
            // signal-to-signal progression it is deliberately restrictive and
            // therefore behaves like CAUTION until the speed system is added.
            return state.getValue(GroundShuntSignalBlock.ASPECT) == ShuntDisplayedAspect.STOP
                    ? SignalAspect.STOP : SignalAspect.CAUTION;
        }
        return null;
    }

    private record PassiveChainTarget(BlockPos signalPos, SignalBlockEntity createSignal,
                                      SignalAspect nswAspect, int skippedChains) {}

    /** Resolve the default straight path through any number of unlinked Create chain signals. */
    private PassiveChainTarget resolvePassiveStraightTarget(ServerLevel level, SignalBlockEntity startingSignal) {
        if (startingSignal == null || startingSignal.getSignal() == null) return null;
        var boundary = startingSignal.getSignal();
        BlockPos signalPos = startingSignal.getBlockPos();
        Set<java.util.UUID> visited = new java.util.HashSet<>();
        int skipped = 0;

        for (int guard = 0; guard < 32; guard++) {
            if (!visited.add(boundary.getId())) return null;
            RouteProbe.StraightSignalTarget target = RouteProbe.findNextStraightSignal(boundary, signalPos, level);
            if (target == null) return null;

            // A boundary side can have more than one physical Create signal block.
            // If any of them is controlled by an NSW head, that NSW indication is
            // the signalling authority and the chain walk stops immediately.
            SignalAspect linkedNsw = null;
            BlockPos selectedPos = target.signalPos();
            for (BlockPos candidatePos : target.boundary().blockEntities.get(target.side()).keySet()) {
                SignalAspect candidateAspect = logicalAspectShownForSignal(level, candidatePos);
                if (candidateAspect != null) {
                    linkedNsw = candidateAspect;
                    selectedPos = candidatePos.immutable();
                    break;
                }
            }

            SignalBlockEntity createSignal = level.hasChunkAt(selectedPos)
                    && level.getBlockEntity(selectedPos) instanceof SignalBlockEntity s ? s : null;
            if (linkedNsw != null)
                return new PassiveChainTarget(selectedPos, createSignal, linkedNsw, skipped);

            if (!target.chain()) {
                // 0.1.3a3: an ordinary Create signal is only a terminal passive
                // authority when it is actually at STOP.  A clear ordinary signal
                // is merely a checkpoint; keep walking the straight/default route
                // until we either encounter a red ordinary Create signal or a
                // Create signal controlled by another NSW head.
                if (createSignal != null
                        && (SignalAspectResolver.isStop(createSignal)
                        || SignalAspectResolver.isProtectedBlockOccupied(createSignal)))
                    return new PassiveChainTarget(selectedPos, createSignal, null, skipped);

                boundary = target.boundary();
                signalPos = target.signalPos();
                continue;
            }

            // No NSW head controls this intermediate chain boundary; look through it.
            skipped++;
            boundary = target.boundary();
            signalPos = target.signalPos();
        }
        return null;
    }

    private static SignalReading mirrorChainAspect(SignalAspect aspect) {
        if (aspect == null) return SignalReading.stop();
        return switch (aspect) {
            case STOP -> SignalReading.stop();
            case CAUTION, CAUTION_TURNOUT -> SignalReading.caution();
            case MEDIUM, MEDIUM_TURNOUT -> SignalReading.medium();
            case PRELIMINARY_MEDIUM -> SignalReading.preliminaryMedium();
            case PROCEED -> SignalReading.proceed();
        };
    }

    private static boolean isAspectDisplay(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof SingleColourLightSignalBlock) return true;
        if (level.getBlockState(pos).getBlock() instanceof DoubleColourLightSignalBlock) return true;
        if (level.getBlockState(pos).getBlock() instanceof GroundShuntSignalBlock) return true;
        if (level.getBlockState(pos).getBlock() instanceof MainlineAddonLampBlock) return true;
        if (level.getBlockState(pos).getBlock() instanceof RepeaterSignalBlock) return true;
        String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
        return id.startsWith("create:") && id.endsWith("nixie_tube");
    }

    /** Read the logical aspect currently displayed by any NSW head protecting a Create signal. */
    private SignalAspect logicalAspectShownForSignal(ServerLevel level, BlockPos signalPos) {
        if (signalPos == null) return null;
        for (var e : links.entrySet()) {
            if (!signalPos.equals(e.getValue())) continue;
            BlockPos display = e.getKey();
            SignalAspect aspect = logicalAspectShownByDisplay(level, display);
            if (aspect != null) return aspect;
        }
        return null;
    }

    private static void applySignalOutput(ServerLevel level, BlockPos pos, SignalReading reading) {
        applySingleColourOutput(level, pos, reading);
        applyDoubleColourOutput(level, pos, reading);
        applyGroundShuntOutput(level, pos, reading);
    }

    /**
     * Alpha 1 shunt mapping. A ground shunt protects only its own Create block:
     * STOP remains absolute; every permissive logical reading becomes the single
     * restrictive SHUNT PROCEED indication.
     */
    private static void applyGroundShuntOutput(ServerLevel level, BlockPos pos, SignalReading reading) {
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof GroundShuntSignalBlock)) return;
        boolean runningEquipped = state.getBlock() instanceof GroundRunningShuntSignalBlock
                || state.getBlock() instanceof PostRunningShuntSignalBlock
                || state.getBlock() instanceof HangingRunningShuntSignalBlock;
        ShuntDisplayedAspect displayed = reading.aspect() == SignalAspect.STOP
                ? ShuntDisplayedAspect.STOP
                : (runningEquipped ? ShuntDisplayedAspect.RUNNING_PROCEED : ShuntDisplayedAspect.SHUNT_PROCEED);
        if (state.getValue(GroundShuntSignalBlock.ASPECT) != displayed)
            level.setBlock(pos, state.setValue(GroundShuntSignalBlock.ASPECT, displayed), 3);
    }

    /** Apply a logical reading to the Alpha 2 NSW single-colour-light physical signal. */
    private static void applySingleColourOutput(ServerLevel level, BlockPos pos, SignalReading reading) {
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SingleColourLightSignalBlock)) return;

        SingleColourLightSignalBlock signalBlock = (SingleColourLightSignalBlock) state.getBlock();
        // Fixed Red remains physically at Stop even when Shunt Proceed authorises
        // movement through the route; the subsidiary authority does not clear the head.
        if (signalBlock.physicalType() == SingleColourLightSignalBlock.PhysicalType.FIXED_RED) {
            if (state.getValue(SingleColourLightSignalBlock.ASPECT) != SingleColourDisplayedAspect.STOP)
                level.setBlock(pos, state.setValue(SingleColourLightSignalBlock.ASPECT, SingleColourDisplayedAspect.STOP), 3);
            return;
        }
        // Two-aspect signals display red only for STOP; all other valid
        // movement aspects (including caution, medium and turnout) show green.
        // The linked Create signal remains authoritative for movement safety.
        if (signalBlock.physicalType() == SingleColourLightSignalBlock.PhysicalType.TWO_ASPECT) {
            SingleColourDisplayedAspect twoAspect = reading.aspect() != SignalAspect.STOP
                    ? SingleColourDisplayedAspect.PROCEED : SingleColourDisplayedAspect.STOP;
            if (state.getValue(SingleColourLightSignalBlock.ASPECT) != twoAspect)
                level.setBlock(pos, state.setValue(SingleColourLightSignalBlock.ASPECT, twoAspect), 3);
            return;
        }
        TurnoutCapability capability = state.getValue(SingleColourLightSignalBlock.TURNOUT_CAPABILITY);
        // World-time phase keeps every flashing NSW signal synchronised: 20 ticks ON, 20 ticks OFF.
        boolean flashOn = ((level.getGameTime() / 20L) & 1L) == 0L;
        // Distant signals have no red lamp. STOP is therefore represented by the most
        // restrictive aspect they can physically show: CAUTION. They use UNLINKED only
        // before a Create-signal link has been established.
        SignalReading physicalReading = reading;
        if (signalBlock.physicalType() == SingleColourLightSignalBlock.PhysicalType.DISTANT
                && reading.aspect() == SignalAspect.STOP)
            physicalReading = new SignalReading(SignalAspect.CAUTION, TurnoutDirection.NONE);

        final SignalReading shownReading = physicalReading;
        SingleColourDisplayedAspect displayed = switch (shownReading.aspect()) {
            case STOP -> SingleColourDisplayedAspect.STOP;
            case CAUTION -> SingleColourDisplayedAspect.CAUTION;
            case MEDIUM -> flashOn ? SingleColourDisplayedAspect.MEDIUM_ON : SingleColourDisplayedAspect.MEDIUM_OFF;
            case PRELIMINARY_MEDIUM -> SingleColourDisplayedAspect.PROCEED;
            case PROCEED -> SingleColourDisplayedAspect.PROCEED;
            case CAUTION_TURNOUT -> {
                if (!capability.supports(shownReading.direction())) yield SingleColourDisplayedAspect.STOP;
                yield shownReading.direction() == TurnoutDirection.RIGHT
                        ? SingleColourDisplayedAspect.TURNOUT_RIGHT
                        : shownReading.direction() == TurnoutDirection.LEFT
                        ? SingleColourDisplayedAspect.TURNOUT_LEFT : SingleColourDisplayedAspect.STOP;
            }
            case MEDIUM_TURNOUT -> {
                if (!capability.supports(shownReading.direction())) yield SingleColourDisplayedAspect.STOP;
                if (shownReading.direction() == TurnoutDirection.RIGHT)
                    yield flashOn ? SingleColourDisplayedAspect.MEDIUM_TURNOUT_RIGHT_ON
                            : SingleColourDisplayedAspect.MEDIUM_TURNOUT_RIGHT_OFF;
                if (shownReading.direction() == TurnoutDirection.LEFT)
                    yield flashOn ? SingleColourDisplayedAspect.MEDIUM_TURNOUT_LEFT_ON
                            : SingleColourDisplayedAspect.MEDIUM_TURNOUT_LEFT_OFF;
                yield SingleColourDisplayedAspect.STOP;
            }
        };

        if (state.getValue(SingleColourLightSignalBlock.ASPECT) != displayed)
            level.setBlock(pos, state.setValue(SingleColourLightSignalBlock.ASPECT, displayed), 3);
    }

    /** Apply a shared logical NSW reading to a double-colour-light head. */
    private static void applyDoubleColourOutput(ServerLevel level, BlockPos pos, SignalReading reading) {
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DoubleColourLightSignalBlock signalBlock)) return;

        if (signalBlock.physicalType() == DoubleColourLightSignalBlock.PhysicalType.FIXED_RED) {
            if (state.getValue(DoubleColourLightSignalBlock.ASPECT) != DoubleColourDisplayedAspect.STOP)
                level.setBlock(pos, state.setValue(DoubleColourLightSignalBlock.ASPECT, DoubleColourDisplayedAspect.STOP), 3);
            return;
        }
        boolean flashOn = ((level.getGameTime() / 20L) & 1L) == 0L;
        SignalAspect aspect = reading.aspect();

        // Normal double-colour heads cannot display turnout aspects; like other restricted
        // physical heads they skip to the next usable non-turnout warning, MEDIUM.
        if (signalBlock.physicalType() == DoubleColourLightSignalBlock.PhysicalType.NORMAL
                && (aspect == SignalAspect.CAUTION_TURNOUT || aspect == SignalAspect.MEDIUM_TURNOUT))
            aspect = SignalAspect.MEDIUM;

        // Converging double-colour heads physically provide only R/R, Y/R and Y/Y.
        if (signalBlock.physicalType() == DoubleColourLightSignalBlock.PhysicalType.CONVERGING) {
            if (aspect != SignalAspect.STOP && aspect != SignalAspect.CAUTION_TURNOUT && aspect != SignalAspect.MEDIUM_TURNOUT)
                aspect = SignalAspect.MEDIUM_TURNOUT;
        }

        DoubleColourDisplayedAspect displayed = switch (aspect) {
            case STOP -> DoubleColourDisplayedAspect.STOP;
            case CAUTION -> DoubleColourDisplayedAspect.CAUTION;
            case MEDIUM -> DoubleColourDisplayedAspect.MEDIUM;
            case PRELIMINARY_MEDIUM -> flashOn
                    ? DoubleColourDisplayedAspect.PRELIMINARY_MEDIUM_ON
                    : DoubleColourDisplayedAspect.PRELIMINARY_MEDIUM_OFF;
            case PROCEED -> DoubleColourDisplayedAspect.PROCEED;
            case CAUTION_TURNOUT -> DoubleColourDisplayedAspect.CAUTION_TURNOUT;
            case MEDIUM_TURNOUT -> DoubleColourDisplayedAspect.MEDIUM_TURNOUT;
        };
        if (state.getValue(DoubleColourLightSignalBlock.ASPECT) != displayed)
            level.setBlock(pos, state.setValue(DoubleColourLightSignalBlock.ASPECT, displayed), 3);
    }

    /** Temporary Alpha 1e diagnostic output using Create's own Nixie Tube API. */
    private static void applyTemporaryNixieOutput(ServerLevel level, BlockPos pos, SignalReading reading) {
        String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
        if (!id.startsWith("create:") || !id.endsWith("nixie_tube")) return;
        if (!(level.getBlockEntity(pos) instanceof NixieTubeBlockEntity nixie)) {
            CreateSignallingAdditions.LOGGER.warn("Linked Create Nixie Tube at {} has unexpected block entity {}",
                    pos, level.getBlockEntity(pos) == null ? "null" : level.getBlockEntity(pos).getClass().getName());
            return;
        }

        String glyph = switch (reading.aspect()) {
            case STOP -> "S";
            case CAUTION -> "C";
            case MEDIUM -> "M";
            case PRELIMINARY_MEDIUM -> "p";
            case CAUTION_TURNOUT -> "T";
            case MEDIUM_TURNOUT -> "M";
            case PROCEED -> "P";
        };

        // The physical Nixie variant controls its glow colour, so use unambiguous letters
        // instead of relying on Component RGB: S=Stop, C=Caution, T=Caution Turnout, P=Proceed.
        String json = "{\"text\":\"" + glyph + "\"}";
        nixie.displayCustomText(json, 0);
        nixie.setChanged();
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
    }

}
