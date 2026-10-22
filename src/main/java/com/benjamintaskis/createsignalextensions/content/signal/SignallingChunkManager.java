package com.benjamintaskis.createsignalextensions.content.signal;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import com.benjamintaskis.createsignalextensions.config.CommonConfig;
import com.benjamintaskis.createsignalextensions.content.route.RouteProbe;
import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

import java.util.*;

/**
 * Beta 3 adaptive signalling chunk loader.
 *
 * Player demand has absolute priority: a NSW signal already inside a player's
 * normal server view wakes the next two linked NSW signals. Active Create train
 * paths wake the current NSW signal and up to five following NSW signals. Chunks
 * are deduplicated and retained for a short grace period to avoid load thrashing.
 */
public final class SignallingChunkManager {
    public static final TicketController TICKETS = new TicketController(
            CreateSignallingAdditions.id("adaptive_signalling"),
            (level, helper) -> {
                // Adaptive tickets are deliberately not restored after a restart.
                for (UUID owner : List.copyOf(helper.getEntityTickets().keySet()))
                    helper.removeAllTickets(owner);
            });

    private static final UUID OWNER = UUID.fromString("72e7b31b-61d0-4d30-99ca-4e7f8fd10130");
    private static final int PLAYER_LOOKAHEAD = 2;
    private static final int TRAIN_LOOKAHEAD = 5;
    private static final long GRACE_TICKS = 20L * 30L;
    private static final Map<Long, Long> FORCED = new LinkedHashMap<>(); // chunk -> last required game time
    private static int tickCounter;

    private SignallingChunkManager() {}

    public static void registerTicketController(RegisterTicketControllersEvent event) {
        event.register(TICKETS);
    }

    public static void tick(MinecraftServer server, SignalAspectLinkManager links) {
        if (!CommonConfig.adaptiveChunkLoading()) return;
        if (++tickCounter % 20 != 0) return; // once per second; signalling itself still refreshes normally
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        int limit = CommonConfig.maxSignallingForcedChunks();

        // Lower number = higher priority. LinkedHashMap preserves deterministic ordering.
        LinkedHashMap<Long, Integer> wanted = new LinkedHashMap<>();
        collectPlayerDemand(level, links, wanted);
        collectTrainDemand(level, links, wanted, limit);

        List<Map.Entry<Long, Integer>> ordered = new ArrayList<>(wanted.entrySet());
        ordered.sort(Comparator.comparingInt(Map.Entry::getValue));
        LinkedHashSet<Long> admitted = new LinkedHashSet<>();
        for (var e : ordered) {
            if (admitted.size() >= limit) break;
            admitted.add(e.getKey());
        }

        // Acquire/renew admitted chunks. UUID tickets are ticking tickets; false means
        // no forced natural spawning for these remote signalling chunks.
        for (long packed : admitted) {
            ChunkPos cp = new ChunkPos(packed);
            if (!FORCED.containsKey(packed)) {
                if (TICKETS.forceChunk(level, OWNER, cp.x, cp.z, true, false))
                    if (CommonConfig.signalDebug())
                        CreateSignallingAdditions.LOGGER.debug("Adaptive signalling loaded chunk {} {}", cp.x, cp.z);
            }
            FORCED.put(packed, now);
        }

        // Player demand can pre-empt train/grace demand simply by not admitting it.
        // Non-required chunks receive a 30-second grace period unless the cap is full.
        boolean pressure = admitted.size() >= limit;
        Iterator<Map.Entry<Long, Long>> it = FORCED.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (admitted.contains(e.getKey())) continue;
            if (!pressure && now - e.getValue() < GRACE_TICKS) continue;
            ChunkPos cp = new ChunkPos(e.getKey());
            TICKETS.forceChunk(level, OWNER, cp.x, cp.z, false, false);
            it.remove();
            if (CommonConfig.signalDebug())
                CreateSignallingAdditions.LOGGER.debug("Adaptive signalling released chunk {} {}", cp.x, cp.z);
        }
    }

    private static void collectPlayerDemand(ServerLevel level, SignalAspectLinkManager data,
                                            Map<Long, Integer> wanted) {
        int view = level.getServer().getPlayerList().getViewDistance();
        for (BlockPos source : data.linkedDisplays()) {
            ChunkPos sourceChunk = new ChunkPos(source);
            boolean inView = level.players().stream().anyMatch(player -> {
                ChunkPos pc = player.chunkPosition();
                return Math.abs(pc.x - sourceChunk.x) <= view && Math.abs(pc.z - sourceChunk.z) <= view;
            });
            if (!inView) continue;
            collectAllBranches(data, source, PLAYER_LOOKAHEAD, 0, wanted, new HashSet<>());
        }
    }

    private static void collectAllBranches(SignalAspectLinkManager data, BlockPos source, int remaining,
                                           int depth, Map<Long, Integer> wanted, Set<BlockPos> visited) {
        if (remaining <= 0 || !visited.add(source)) return;
        for (BlockPos next : data.followingDisplaysRaw(source)) {
            addEquipment(data, next, depth, wanted); // PLAYER priorities 0 (+1), 1 (+2)
            collectAllBranches(data, next, remaining - 1, depth + 1, wanted, visited);
        }
    }

    private static void collectTrainDemand(ServerLevel level, SignalAspectLinkManager data,
                                           Map<Long, Integer> wanted, int limit) {
        for (BlockPos sourceDisplay : data.linkedDisplays()) {
            if (!level.hasChunkAt(sourceDisplay)) continue;
            BlockPos sourceSignal = data.linkedSignal(sourceDisplay);
            if (sourceSignal == null || !level.hasChunkAt(sourceSignal)) continue;
            if (!(level.getBlockEntity(sourceSignal) instanceof SignalBlockEntity current)) continue;

            RouteProbe.Result active = RouteProbe.probe(current.getSignal(), sourceSignal);
            if (!active.resolved()) continue;

            addEquipment(data, sourceDisplay, 10, wanted); // train current, below every player priority
            BlockPos display = sourceDisplay;
            Set<BlockPos> visited = new HashSet<>();
            visited.add(display);
            for (int depth = 1; depth <= TRAIN_LOOKAHEAD; depth++) {
                BlockPos next = chooseTrainFollowing(level, data, display, wanted, limit, depth);
                if (next == null || !visited.add(next)) break;
                addEquipment(data, next, 10 + depth, wanted);
                display = next;
            }
        }
    }

    private static BlockPos chooseTrainFollowing(ServerLevel level, SignalAspectLinkManager data,
                                                  BlockPos display, Map<Long, Integer> wanted,
                                                  int limit, int depth) {
        List<BlockPos> following = data.followingDisplaysRaw(display);
        if (following.isEmpty()) return null;
        if (following.size() == 1) return following.get(0);

        BlockPos signalPos = data.linkedSignal(display);
        if (signalPos == null || !(level.getBlockEntity(signalPos) instanceof SignalBlockEntity current)) return null;

        List<BlockPos> candidateSignals = new ArrayList<>();
        for (BlockPos targetDisplay : following) {
            BlockPos targetSignal = data.linkedSignal(targetDisplay);
            if (targetSignal == null) continue; // EOL or an unlinked target ends train propagation
            // Route selection needs Create's target SignalBlockEntity. Wake only the candidate
            // Create-signal chunks needed to resolve this junction; the final admitted set is
            // still governed by the global cap and player-first priorities.
            addChunk(targetSignal, 10 + depth, wanted);
            long playerReserved = wanted.entrySet().stream().filter(e -> e.getValue() < 10).map(Map.Entry::getKey).distinct().count();
            int trainCapacity = Math.max(0, limit - (int) Math.min(playerReserved, limit));
            if (FORCED.size() < trainCapacity) {
                ChunkPos cp = new ChunkPos(targetSignal);
                TICKETS.forceChunk(level, OWNER, cp.x, cp.z, true, false);
                FORCED.putIfAbsent(cp.toLong(), level.getGameTime());
            }
            candidateSignals.add(targetSignal);
        }
        if (candidateSignals.isEmpty()) return null;

        BlockPos selectedSignal = RouteProbe.findNextLinkedSignal(current.getSignal(), signalPos, candidateSignals, level);
        if (selectedSignal == null) return null;
        for (BlockPos targetDisplay : following)
            if (selectedSignal.equals(data.linkedSignal(targetDisplay))) return targetDisplay;
        return null;
    }

    private static void addEquipment(SignalAspectLinkManager data, BlockPos display, int priority,
                                     Map<Long, Integer> wanted) {
        addChunk(display, priority, wanted);
        BlockPos createSignal = data.linkedSignal(display);
        if (createSignal != null) addChunk(createSignal, priority, wanted);
        BlockPos ri = data.routeInterfacePosition(createSignal);
        if (ri != null) addChunk(ri, priority, wanted);
    }

    private static void addChunk(BlockPos pos, int priority, Map<Long, Integer> wanted) {
        long key = new ChunkPos(pos).toLong();
        wanted.merge(key, priority, Math::min);
    }
}
