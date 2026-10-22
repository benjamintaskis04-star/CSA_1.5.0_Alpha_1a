package com.benjamintaskis.createsignalextensions.content.signal;

import net.minecraft.world.level.block.state.BlockBehaviour;

public class ConvergingSingleColourLightSignalBlock extends SingleColourLightSignalBlock {
    public ConvergingSingleColourLightSignalBlock(BlockBehaviour.Properties properties) {
        super(properties, TurnoutCapability.BOTH, PhysicalType.CONVERGING);
    }
}
