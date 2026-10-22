package com.benjamintaskis.createsignalextensions.content.speed;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import com.benjamintaskis.createsignalextensions.config.CommonConfig;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import net.createmod.catnip.data.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.List;

/** CSA schedule instruction: Set Speed Category. Train name is never consulted. */
public class SetTrainCategoryInstruction extends ScheduleInstruction {
    public static final String CATEGORY = "Category";

    public SetTrainCategoryInstruction() {
        data.putInt(CATEGORY, TrainSpeedCategory.DEFAULT.ordinal());
    }

    public TrainSpeedCategory getCategory() {
        return TrainSpeedCategory.fromOrdinal(intData(CATEGORY));
    }

    @Override
    public ResourceLocation getId() {
        return CreateSignallingAdditions.id("set_speed_category");
    }

    @Override
    public boolean supportsConditions() {
        return false;
    }

    @Override
    public Pair<ItemStack, Component> getSummary() {
        return Pair.of(icon(), Component.literal("Set Speed Category: " + getCategory().displayName()));
    }

    @Override
    public ItemStack getSecondLineIcon() {
        return icon();
    }

    @Override
    public List<Component> getTitleAs(String type) {
        return List.of(Component.literal("Set Speed Category: " + getCategory().displayName()));
    }

    @Override
    public void initConfigurationWidgets(ModularGuiLineBuilder builder) {
        builder.addSelectionScrollInput(0, 95, (input, label) -> input
                .forOptions(List.of(
                        Component.literal(TrainSpeedCategory.DEFAULT.displayName()),
                        Component.literal(TrainSpeedCategory.MULTIPLE_UNIT.displayName()),
                        Component.literal(TrainSpeedCategory.HIGH_SPEED.displayName())))
                .titled(Component.literal("Speed Category")), CATEGORY);
    }

    @Override
    public DiscoveredPath start(ScheduleRuntime runtime, Level level) {
        TrainSpeedCategory category = getCategory();
        TrainCategoryManager.setStoredCategory(level, runtime.train, category);
        if (CommonConfig.signalDebug())
            CreateSignallingAdditions.LOGGER.info("Train '{}' speed category set to {} by schedule",
                runtime.train.name == null ? runtime.train.id : runtime.train.name.getString(), category.displayName());

        // Match Create's own instantaneous instructions (e.g. Change Throttle):
        // apply the change, advance to the next schedule entry, and return no path.
        runtime.state = ScheduleRuntime.State.PRE_TRANSIT;
        runtime.currentEntry++;
        return null;
    }

    private ItemStack icon() {
        return new ItemStack(Items.MINECART);
    }
}
