package com.benjamintaskis.createsignalextensions.content.signal;

import net.minecraft.world.level.block.state.BlockBehaviour;

public class DistantSingleColourLightSignalBlock extends SingleColourLightSignalBlock {
    public DistantSingleColourLightSignalBlock(BlockBehaviour.Properties properties) {
        super(properties, TurnoutCapability.NONE, PhysicalType.DISTANT);
    }
}
