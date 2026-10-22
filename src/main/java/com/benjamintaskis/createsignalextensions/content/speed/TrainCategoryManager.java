package com.benjamintaskis.createsignalextensions.content.speed;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent CSA train speed categories. Train names are deliberately never
 * consulted. Manual driving evaluates as DEFAULT without destroying the
 * stored scheduled category.
 */
public final class TrainCategoryManager extends SavedData {
    private static final String DATA_NAME = "create_signalling_additions_train_categories";
    private static final Factory<TrainCategoryManager> FACTORY =
            new Factory<>(TrainCategoryManager::new, TrainCategoryManager::load, null);

    private final Map<UUID, TrainSpeedCategory> stored = new HashMap<>();

    public TrainCategoryManager() {}

    public static TrainCategoryManager get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static void setStoredCategory(Level level, Train train, TrainSpeedCategory category) {
        if (!(level instanceof ServerLevel serverLevel) || train == null || train.id == null) return;
        TrainCategoryManager data = get(serverLevel);
        data.stored.put(train.id, category == null ? TrainSpeedCategory.DEFAULT : category);
        data.setDirty();
    }

    public static TrainSpeedCategory getStoredCategory(Level level, Train train) {
        if (!(level instanceof ServerLevel serverLevel) || train == null || train.id == null)
            return TrainSpeedCategory.DEFAULT;
        return get(serverLevel).stored.getOrDefault(train.id, TrainSpeedCategory.DEFAULT);
    }

    public static TrainSpeedCategory getEffectiveCategory(Level level, Train train) {
        return isPlayerControlled(train) ? TrainSpeedCategory.DEFAULT : getStoredCategory(level, train);
    }

    public static boolean isPlayerControlled(Train train) {
        if (train == null || train.carriages == null) return false;
        for (Carriage carriage : train.carriages) {
            var entity = carriage.anyAvailableEntity();
            if (entity != null && entity.getControllingPlayer().isPresent()) return true;
        }
        return false;
    }

    private static TrainCategoryManager load(CompoundTag tag, HolderLookup.Provider registries) {
        TrainCategoryManager data = new TrainCategoryManager();
        ListTag list = tag.getList("Categories", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Train")) continue;
            data.stored.put(entry.getUUID("Train"), TrainSpeedCategory.fromOrdinal(entry.getInt("Category")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, TrainSpeedCategory> e : stored.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Train", e.getKey());
            entry.putInt("Category", e.getValue().ordinal());
            list.add(entry);
        }
        tag.put("Categories", list);
        return tag;
    }
}
