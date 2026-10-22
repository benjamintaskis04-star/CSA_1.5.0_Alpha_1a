package com.benjamintaskis.createsignalextensions.content.signal;

/** Two-aspect single-colour-light signal: red stop or green proceed. */
public class TwoAspectSingleColourLightSignalBlock extends SingleColourLightSignalBlock {
    public TwoAspectSingleColourLightSignalBlock(Properties properties) {
        super(properties, TurnoutCapability.NONE, PhysicalType.TWO_ASPECT);
    }
}
