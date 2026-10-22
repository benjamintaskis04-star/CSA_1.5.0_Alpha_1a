package com.benjamintaskis.createsignalextensions.content.signal;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 1.4.0 Alpha 5 post-mounted NSW shunt signal. Behaviour is shared with the ground shunt. */
public class PostShuntSignalBlock extends GroundShuntSignalBlock {
    public static final MapCodec<PostShuntSignalBlock> CODEC = simpleCodec(PostShuntSignalBlock::new);

    public PostShuntSignalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GroundShuntSignalBlock> codec() {
        return CODEC;
    }

    @Override
    protected String signalDescription() {
        return "Post-mounted shunt signal";
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape south = Shapes.or(
                Block.box(4, 0, 4, 12, 9, 13),
                Block.box(4, 0, 4, 12, 2, 12)
        );
        return rotateSouthShape(south, state.getValue(FACING));
    }
}
