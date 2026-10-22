package com.benjamintaskis.createsignalextensions.content.signal;

/** NSW single-colour Fixed Red mainline variant. */
public class FixedRedSingleColourLightSignalBlock extends SingleColourLightSignalBlock {
    public FixedRedSingleColourLightSignalBlock(Properties properties) {
        super(properties, TurnoutCapability.NONE, PhysicalType.FIXED_RED);
    }
}
