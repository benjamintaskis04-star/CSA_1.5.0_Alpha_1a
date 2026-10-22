package com.benjamintaskis.createsignalextensions.content;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Shared vertical-offset state for blocks in the create_signal_extensions:heightvariable tag. */
public final class HeightVariable {
    public static final int MIN = 0;
    public static final int DEFAULT = 16;
    public static final int MAX = 48;
    public static final IntegerProperty VERTICAL_OFFSET = IntegerProperty.create("vertical_offset", MIN, MAX);

    private HeightVariable() {}

    public static boolean supports(BlockState state) {
        return state.hasProperty(VERTICAL_OFFSET);
    }

    public static int stored(BlockState state) {
        return supports(state) ? state.getValue(VERTICAL_OFFSET) : DEFAULT;
    }

    public static double blocks(BlockState state) {
        return (stored(state) - DEFAULT) / 16.0;
    }
}
