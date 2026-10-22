package com.benjamintaskis.createsignalextensions.content.speed;

import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Alpha 5c persistent train movement state. Running is the default. */
public final class TrainMovementModeManager extends SavedData {
    private static final String DATA_NAME = "create_signalling_additions_train_movement_modes";
    private static final Factory<TrainMovementModeManager> FACTORY = new Factory<>(TrainMovementModeManager::new, TrainMovementModeManager::load, null);
    private final Set<UUID> shunting = new HashSet<>();
    private final Set<UUID> pendingShunt = new HashSet<>();
    // Alpha 6 player-initiated shunt state is deliberately transient. train -> controlling player.
    private final Map<UUID, UUID> manualShunting = new HashMap<>();
    public TrainMovementModeManager() {}
    public static TrainMovementModeManager get(ServerLevel level) { return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME); }

    /** Start Shunt arms the train; the next shunt-capable Create signal activates it. */
    public static void requestStartShunt(Level level, Train train) {
        if (!(level instanceof ServerLevel sl) || train == null || train.id == null) return;
        TrainMovementModeManager d=get(sl);
        if (!d.shunting.contains(train.id)) d.pendingShunt.add(train.id);
        d.setDirty();
    }
    /** End Shunt schedule control returns the train to running mode. */
    public static void endShunt(Level level, Train train) {
        if (!(level instanceof ServerLevel sl) || train == null || train.id == null) return;
        TrainMovementModeManager d=get(sl); d.pendingShunt.remove(train.id); d.shunting.remove(train.id); d.setDirty();
    }
    /** Called when a pending train's next Create signal is known to support shunt. */
    public static boolean activateIfPending(Level level, Train train) {
        if (!(level instanceof ServerLevel sl) || train == null || train.id == null) return false;
        TrainMovementModeManager d=get(sl);
        if (!d.pendingShunt.remove(train.id)) return d.shunting.contains(train.id);
        d.shunting.add(train.id); d.setDirty(); return true;
    }
    public static boolean isPending(Level level, Train train) {
        return level instanceof ServerLevel sl && train != null && train.id != null && get(sl).pendingShunt.contains(train.id);
    }
    public static boolean isShunting(Level level, Train train) {
        return level instanceof ServerLevel sl && train != null && train.id != null && (get(sl).shunting.contains(train.id) || get(sl).manualShunting.containsKey(train.id));
    }

    public static boolean isManualShunting(Level level, Train train) { return level instanceof ServerLevel sl && train != null && train.id != null && get(sl).manualShunting.containsKey(train.id); }

    public static boolean toggleManualShunt(ServerLevel level, Train train, UUID playerId) {
        if (train == null || train.id == null || playerId == null) return false;
        TrainMovementModeManager d=get(level);
        if (playerId.equals(d.manualShunting.get(train.id))) { d.manualShunting.remove(train.id); return false; }
        d.manualShunting.put(train.id, playerId); return true;
    }
    public static void validateManualShunts(ServerLevel level) {
        TrainMovementModeManager d=get(level);
        d.manualShunting.entrySet().removeIf(e -> {
            Train train = com.simibubi.create.Create.RAILWAYS.trains.get(e.getKey());
            if (train == null || train.carriages == null) return true;
            for (var carriage : train.carriages) {
                var entity=carriage.anyAvailableEntity();
                if (entity != null && entity.getControllingPlayer().map(e.getValue()::equals).orElse(false)) return false;
            }
            return true;
        });
    }

    /** Compatibility helper retained for older callers. */
    public static void setShunting(Level level, Train train, boolean value) { if (value) requestStartShunt(level, train); else endShunt(level, train); }

    private static TrainMovementModeManager load(CompoundTag tag, HolderLookup.Provider r) {
        TrainMovementModeManager d=new TrainMovementModeManager();
        loadSet(tag, "Shunting", d.shunting); loadSet(tag, "PendingShunt", d.pendingShunt); return d;
    }
    private static void loadSet(CompoundTag tag,String key,Set<UUID> out){ ListTag l=tag.getList(key, Tag.TAG_COMPOUND); for(int i=0;i<l.size();i++){CompoundTag e=l.getCompound(i);if(e.hasUUID("Train"))out.add(e.getUUID("Train"));}}
    private static ListTag saveSet(Set<UUID> set){ListTag l=new ListTag();for(UUID id:set){CompoundTag e=new CompoundTag();e.putUUID("Train",id);l.add(e);}return l;}
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider r) { tag.put("Shunting",saveSet(shunting));tag.put("PendingShunt",saveSet(pendingShunt));return tag; }
}
