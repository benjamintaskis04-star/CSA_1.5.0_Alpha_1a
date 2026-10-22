package com.benjamintaskis.createsignalextensions.content.signal;

import net.minecraft.world.level.block.state.BlockBehaviour;

public class ConvergingDoubleColourLightSignalBlock extends DoubleColourLightSignalBlock {
    public ConvergingDoubleColourLightSignalBlock(BlockBehaviour.Properties properties) {
        super(properties, PhysicalType.CONVERGING);
    }
}
