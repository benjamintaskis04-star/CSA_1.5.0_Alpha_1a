package com.benjamintaskis.createsignalextensions.content.signal;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Beta 2 overhead-mounted counterpart to the ordinary post shunt signal. */
public class HangingShuntSignalBlock extends GroundShuntSignalBlock {
    public static final MapCodec<HangingShuntSignalBlock> CODEC = simpleCodec(HangingShuntSignalBlock::new);
    public HangingShuntSignalBlock(Properties p) { super(p); }
    @Override protected MapCodec<? extends GroundShuntSignalBlock> codec() { return CODEC; }
    @Override protected String signalDescription() { return "Hanging shunt signal"; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape south = Shapes.or(Block.box(4, 6, 4, 12, 14, 13), Block.box(6, 14, 6, 10, 16, 10));
        return rotateSouthShape(south, state.getValue(FACING));
    }
}
