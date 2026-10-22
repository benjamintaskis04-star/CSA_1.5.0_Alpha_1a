package com.benjamintaskis.createsignalextensions.content.signal;

import com.benjamintaskis.createsignalextensions.content.HeightVariable;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class EndOfLineMarkerBlock extends HorizontalDirectionalBlock implements IWrenchable {
    public static final MapCodec<EndOfLineMarkerBlock> CODEC = simpleCodec(EndOfLineMarkerBlock::new);
    public EndOfLineMarkerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HeightVariable.VERTICAL_OFFSET, HeightVariable.DEFAULT));
    }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(HeightVariable.VERTICAL_OFFSET, HeightVariable.DEFAULT);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape south=Shapes.or(Block.box(6,0,0,10,23,5),Block.box(3,0,-13,13,6,1));
        int turns=switch(state.getValue(FACING)){case SOUTH->0;case WEST->1;case NORTH->2;case EAST->3;default->0;};
        VoxelShape out=south;
        for(int i=0;i<turns;i++){VoxelShape next=Shapes.empty();for(var b:out.toAabbs())next=Shapes.or(next,Block.box((1-b.maxZ)*16,b.minY*16,b.minX*16,(1-b.minZ)*16,b.maxY*16,b.maxX*16));out=next;}
        return out;
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, HeightVariable.VERTICAL_OFFSET);
    }

    @Override public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) { return net.minecraft.world.level.block.RenderShape.MODEL; }
}
