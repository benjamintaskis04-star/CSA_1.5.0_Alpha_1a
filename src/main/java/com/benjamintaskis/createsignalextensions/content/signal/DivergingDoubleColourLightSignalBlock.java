package com.benjamintaskis.createsignalextensions.content.signal;

import net.minecraft.world.level.block.state.BlockBehaviour;

public class DivergingDoubleColourLightSignalBlock extends DoubleColourLightSignalBlock {
    public DivergingDoubleColourLightSignalBlock(BlockBehaviour.Properties properties) {
        super(properties, PhysicalType.DIVERGING);
    }
}
