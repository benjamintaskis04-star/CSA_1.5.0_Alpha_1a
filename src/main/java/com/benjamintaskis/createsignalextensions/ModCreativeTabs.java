package com.benjamintaskis.createsignalextensions;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateSignallingAdditions.ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.create_signal_extensions.main"))
                    .icon(() -> new ItemStack(CreateSignallingAdditions.SIGNAL_LINKING_TOOL.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(CreateSignallingAdditions.SIGNAL_LINKING_TOOL.get());
                        output.accept(ModBlocks.ROUTE_INTERFACE.get().asItem());
                        output.accept(ModBlocks.LARGE_THEATRE_BOX.get().asItem());
                        output.accept(ModBlocks.SMALL_THEATRE_BOX.get().asItem());
                        output.accept(ModBlocks.NSW_SINGLE_COLOUR_SIGNAL.get().asItem());
                        output.accept(ModBlocks.NSW_SINGLE_COLOUR_TWO_ASPECT_SIGNAL.get().asItem());
                        output.accept(ModBlocks.NSW_SINGLE_COLOUR_DIVERGING_SIGNAL.get().asItem());
                        output.accept(ModBlocks.NSW_SINGLE_COLOUR_CONVERGING_SIGNAL.get().asItem());
                        output.accept(ModBlocks.NSW_SINGLE_COLOUR_DISTANT_SIGNAL.get().asItem());
                        output.accept(ModBlocks.NSW_SINGLE_COLOUR_FIXED_RED_SIGNAL.get().asItem());
                        output.accept(ModBlocks.NSW_DOUBLE_COLOUR_SIGNAL.get().asItem());
                        output.accept(ModBlocks.NSW_DOUBLE_COLOUR_FIXED_RED_SIGNAL.get().asItem());
                        output.accept(ModBlocks.NSW_DOUBLE_COLOUR_DIVERGING_SIGNAL.get().asItem());
                        output.accept(ModBlocks.NSW_DOUBLE_COLOUR_CONVERGING_SIGNAL.get().asItem());
                        output.accept(ModBlocks.NSW_DOUBLE_COLOUR_UNIVERSAL_SIGNAL.get().asItem());
                        output.accept(ModBlocks.NSW_DOUBLE_COLOUR_UNIVERSAL_TUNNEL_SIGNAL.get().asItem());
                        output.accept(ModBlocks.NSW_END_OF_LINE_MARKER.get().asItem());
                        output.accept(ModBlocks.NSW_GROUND_SHUNT_SIGNAL.get().asItem());
                        output.accept(ModBlocks.NSW_POST_SHUNT_SIGNAL.get().asItem());
                        output.accept(ModBlocks.NSW_GROUND_THEATRE_SHUNT_SIGNAL.get().asItem());
                        output.accept(ModBlocks.NSW_POST_THEATRE_SHUNT_SIGNAL.get().asItem());
                        output.accept(ModBlocks.NSW_GROUND_RUNNING_SHUNT_SIGNAL.get().asItem());
                        output.accept(ModBlocks.NSW_POST_RUNNING_SHUNT_SIGNAL.get().asItem());
                        output.accept(ModBlocks.NSW_HANGING_SHUNT_SIGNAL.get().asItem());
                        output.accept(ModBlocks.NSW_HANGING_THEATRE_SHUNT_SIGNAL.get().asItem());
                        output.accept(ModBlocks.NSW_HANGING_RUNNING_SHUNT_SIGNAL.get().asItem());
                        output.accept(ModBlocks.NSW_MAINLINE_SHUNT_LAMP.get().asItem());
                        output.accept(ModBlocks.NSW_MAINLINE_DEAD_END_LAMP.get().asItem());
                        output.accept(ModBlocks.SIGNAL_PLATE.get().asItem());
                        output.accept(ModBlocks.NSW_REPEATER_SIGNAL.get().asItem());
                        output.accept(ModBlocks.SPEED_BOARD.get().asItem());
                        output.accept(ModBlocks.SPEED_BOARD_DEFAULT_MU.get().asItem());
                        output.accept(ModBlocks.SPEED_BOARD_DEFAULT_HIGH_SPEED.get().asItem());
                        output.accept(ModBlocks.SPEED_BOARD_DEFAULT_MU_HIGH_SPEED.get().asItem());
                        output.accept(ModBlocks.UNRESTRICTED_SPEED_BOARD.get().asItem());
                        output.accept(ModBlocks.END_OF_SHUNT_SIGN.get().asItem());
                    })
                    .build());

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }

    private ModCreativeTabs() {}
}
