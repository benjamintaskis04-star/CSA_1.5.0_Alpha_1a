package com.benjamintaskis.createsignalextensions.content.theatre;

import com.benjamintaskis.createsignalextensions.content.HeightVariable;
import com.benjamintaskis.createsignalextensions.ModBlockEntityTypes;
import com.benjamintaskis.createsignalextensions.content.route.SignalLinkingToolItem;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SmallTheatreBoxBlock extends HorizontalDirectionalBlock implements IBE<SmallTheatreBoxBlockEntity>, IWrenchable {
    public static final IntegerProperty ROWS = IntegerProperty.create("rows", 1, 3);
    public static final MapCodec<SmallTheatreBoxBlock> CODEC = simpleCodec(SmallTheatreBoxBlock::new);
    public SmallTheatreBoxBlock(Properties properties) {
        super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ROWS,1).setValue(HeightVariable.VERTICAL_OFFSET, HeightVariable.DEFAULT));
    }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){
        Direction face=c.getClickedFace();
        Direction facing=face.getAxis().isHorizontal()?face:c.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING,facing).setValue(ROWS,1).setValue(HeightVariable.VERTICAL_OFFSET, HeightVariable.DEFAULT);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block,BlockState> b){b.add(FACING,ROWS,HeightVariable.VERTICAL_OFFSET);}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(stack.getItem() instanceof SignalLinkingToolItem tool){tool.useOn(new UseOnContext(player,hand,hit));return ItemInteractionResult.SUCCESS;}
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(level.isClientSide)return InteractionResult.SUCCESS;
        return onBlockEntityUse(level,pos,be->{if(player instanceof ServerPlayer sp)be.openConfigurationScreen(sp);return InteractionResult.SUCCESS;});
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context){
        int rows=state.getValue(ROWS); double minY=rows==1?10:(rows==2?6:2);
        Direction f=state.getValue(FACING);
        return switch(f){
            case NORTH -> Block.box(0,minY,10,16,16,16);
            case SOUTH -> Block.box(0,minY,0,16,16,6);
            case EAST -> Block.box(0,minY,0,6,16,16);
            case WEST -> Block.box(10,minY,0,16,16,16);
            default -> Block.box(0,minY,10,16,16,16);
        };
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState newState,boolean moving){IBE.onRemove(state,level,pos,newState);}
    @Override public Class<SmallTheatreBoxBlockEntity> getBlockEntityClass(){return SmallTheatreBoxBlockEntity.class;}
    @Override public BlockEntityType<? extends SmallTheatreBoxBlockEntity> getBlockEntityType(){return ModBlockEntityTypes.SMALL_THEATRE_BOX.get();}

    @Override public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) { return net.minecraft.world.level.block.RenderShape.MODEL; }
}
