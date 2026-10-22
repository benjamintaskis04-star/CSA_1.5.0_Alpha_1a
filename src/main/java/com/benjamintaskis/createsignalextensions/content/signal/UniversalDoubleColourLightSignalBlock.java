package com.benjamintaskis.createsignalextensions.content.signal;

import net.minecraft.world.level.block.state.BlockBehaviour;

public class UniversalDoubleColourLightSignalBlock extends DoubleColourLightSignalBlock {
    public UniversalDoubleColourLightSignalBlock(BlockBehaviour.Properties properties) {
        super(properties, PhysicalType.UNIVERSAL);
    }
}
