package com.benjamintaskis.createsignalextensions.content.speed;

import com.benjamintaskis.createsignalextensions.content.HeightVariable;
import com.benjamintaskis.createsignalextensions.ModBlockEntityTypes;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** First auxiliary sign. Passing it releases only the active shunt-authority speed restriction. */
public class EndOfShuntSignBlock extends HorizontalDirectionalBlock implements IBE<EndOfShuntSignBlockEntity>, IWrenchable {
    public static final MapCodec<EndOfShuntSignBlock> CODEC = simpleCodec(EndOfShuntSignBlock::new);
    public EndOfShuntSignBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HeightVariable.VERTICAL_OFFSET, HeightVariable.DEFAULT)); }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(HeightVariable.VERTICAL_OFFSET, HeightVariable.DEFAULT); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, HeightVariable.VERTICAL_OFFSET); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> Block.box(0, 0, 0, 16, 16, 2);
            case NORTH -> Block.box(0, 0, 14, 16, 16, 16);
            case WEST -> Block.box(14, 0, 0, 16, 16, 16);
            case EAST -> Block.box(0, 0, 0, 2, 16, 16);
            default -> Block.box(0, 0, 14, 16, 16, 16);
        };
    }
    @Override public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState newState, boolean moving) { IBE.onRemove(state, level, pos, newState); }
    @Override public Class<EndOfShuntSignBlockEntity> getBlockEntityClass() { return EndOfShuntSignBlockEntity.class; }
    @Override public BlockEntityType<? extends EndOfShuntSignBlockEntity> getBlockEntityType() { return ModBlockEntityTypes.END_OF_SHUNT_SIGN.get(); }

    @Override public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) { return net.minecraft.world.level.block.RenderShape.MODEL; }
}
