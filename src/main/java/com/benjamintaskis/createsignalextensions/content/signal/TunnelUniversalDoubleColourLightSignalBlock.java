package com.benjamintaskis.createsignalextensions.content.signal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TunnelUniversalDoubleColourLightSignalBlock extends DoubleColourLightSignalBlock {
    public TunnelUniversalDoubleColourLightSignalBlock(BlockBehaviour.Properties properties) { super(properties, PhysicalType.UNIVERSAL); }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Thin wall-mounted body plus the two projecting heads, authored for SOUTH.
        VoxelShape south = Shapes.or(
                Block.box(0, 0, 11, 8, 24, 13),
                Block.box(1, 2, 11, 7, 23, 15)
        );
        return rotateSouthShape(south, state.getValue(FACING));
    }
}
