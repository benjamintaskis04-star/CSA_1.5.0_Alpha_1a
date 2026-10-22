package com.benjamintaskis.createsignalextensions.content.signal;

import com.mojang.serialization.MapCodec;

/** Alpha 5b ground shunt equipped with a green Running Proceed aspect. */
public class GroundRunningShuntSignalBlock extends GroundTheatreShuntSignalBlock {
    public static final MapCodec<GroundRunningShuntSignalBlock> CODEC = simpleCodec(GroundRunningShuntSignalBlock::new);
    public GroundRunningShuntSignalBlock(Properties p) { super(p); }
    @Override protected MapCodec<? extends GroundShuntSignalBlock> codec() { return CODEC; }
    @Override protected String signalDescription() { return "Ground running-equipped shunt signal"; }
}
