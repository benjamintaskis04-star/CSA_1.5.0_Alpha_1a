package com.benjamintaskis.createsignalextensions.content.speed;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.observer.TrackObserver;
import com.simibubi.create.content.trains.observer.TrackObserverBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Passive CSA readout for Create Track Observers. Detection remains entirely
 * Create-owned: CSA only samples Train.occupiedObservers and resolves a clicked
 * observer through TrackObserverBlockEntity.getObserver().getId().
 */
public final class TrainObserverSpeedMonitor {
    private static final Map<UUID, Observation> LAST = new HashMap<>();
    private static long tick;

    private TrainObserverSpeedMonitor() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        tick++;
        for (Train train : Create.RAILWAYS.trains.values()) {
            if (train == null || train.occupiedObservers == null || train.occupiedObservers.isEmpty())
                continue;

            ServerLevel level = findTrainLevel(train);
            TrainSpeedCategory stored = level == null
                    ? TrainSpeedCategory.DEFAULT
                    : TrainCategoryManager.getStoredCategory(level, train);
            boolean manual = TrainCategoryManager.isPlayerControlled(train);
            double speedKmh = Math.abs(train.speed) * 72.0;
            String trainName = train.name == null ? "Unnamed train" : train.name.getString();

            for (UUID observerId : train.occupiedObservers)
                LAST.put(observerId, new Observation(observerId, train.id, trainName, speedKmh, stored, manual, tick));
        }

        LAST.values().removeIf(o -> tick - o.tick > 20 * 60 * 10);
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof TrackObserverBlockEntity be)) return;
        if (!event.getEntity().getMainHandItem().isEmpty()) return;

        TrackObserver observer = be.getObserver();
        UUID observerId = observer == null ? null : observer.getId();
        Observation latest = observerId == null ? null : LAST.get(observerId);

        if (latest == null || tick - latest.tick > 20 * 30) {
            event.getEntity().displayClientMessage(Component.literal("CSA: This observer has not detected a train recently"), true);
            return;
        }

        String category = latest.category.displayName();
        if (latest.manual)
            category += " (Manual: Default applies)";

        event.getEntity().displayClientMessage(Component.literal(String.format(
                "CSA Observer: %s - %.0f km/h - Category: %s",
                latest.trainName, latest.speedKmh, category)), true);
    }

    private static ServerLevel findTrainLevel(Train train) {
        if (train == null || train.carriages == null) return null;
        for (var carriage : train.carriages) {
            var entity = carriage.anyAvailableEntity();
            if (entity != null && entity.level() instanceof ServerLevel serverLevel)
                return serverLevel;
        }
        return null;
    }

    private record Observation(UUID observerId, UUID trainId, String trainName, double speedKmh,
                               TrainSpeedCategory category, boolean manual, long tick) {}
}
