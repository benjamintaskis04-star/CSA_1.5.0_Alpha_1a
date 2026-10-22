package com.benjamintaskis.createsignalextensions.content.signal;

import net.minecraft.world.level.block.state.BlockBehaviour;

public class DivergingSingleColourLightSignalBlock extends SingleColourLightSignalBlock {
    public DivergingSingleColourLightSignalBlock(BlockBehaviour.Properties properties) {
        super(properties, TurnoutCapability.BOTH, PhysicalType.DIVERGING);
    }
}
