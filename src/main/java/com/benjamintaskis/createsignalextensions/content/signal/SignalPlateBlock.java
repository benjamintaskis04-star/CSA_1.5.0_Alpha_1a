package com.benjamintaskis.createsignalextensions.content.signal;

import com.benjamintaskis.createsignalextensions.ModBlockEntityTypes;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import com.benjamintaskis.createsignalextensions.content.HeightVariable;

/** Alpha 7 temporary 16x8 signal-identification plate used to tune text scale in-game. */
public class SignalPlateBlock extends HorizontalDirectionalBlock implements IBE<SignalPlateBlockEntity>, IWrenchable {
    public enum PlateMode implements StringRepresentable {
        NORMAL("normal"), CO_ACT("co_act"), REPEATER("repeater"), DIST("dist");
        private final String name; PlateMode(String name){this.name=name;}
        @Override public String getSerializedName(){return name;}
        public PlateMode next(){return switch(this){case NORMAL->CO_ACT; case CO_ACT->REPEATER; case REPEATER->DIST; case DIST->NORMAL;};}
    }
    public static final MapCodec<SignalPlateBlock> CODEC = simpleCodec(SignalPlateBlock::new);
    public static final IntegerProperty VERTICAL_OFFSET = HeightVariable.VERTICAL_OFFSET;
    public static final EnumProperty<PlateMode> MODE = EnumProperty.create("mode", PlateMode.class);
    public SignalPlateBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(MODE,PlateMode.NORMAL).setValue(VERTICAL_OFFSET,16));}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
    @Override public RenderShape getRenderShape(BlockState state){ return RenderShape.INVISIBLE; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite()).setValue(MODE,PlateMode.NORMAL).setValue(VERTICAL_OFFSET,16);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,MODE,VERTICAL_OFFSET);}
    @Override public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c){
        return switch(s.getValue(FACING)){
            case NORTH -> Block.box(0,0,15,16,8,16); case SOUTH -> Block.box(0,0,0,16,8,1);
            case EAST -> Block.box(0,0,0,1,8,16); case WEST -> Block.box(15,0,0,16,8,16);
            default -> Block.box(0,0,15,16,8,16);
        };}
    @Override public void onRemove(BlockState s, Level l, BlockPos p, BlockState n, boolean m){IBE.onRemove(s,l,p,n);}
    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level=context.getLevel();
        if(!level.isClientSide) level.setBlock(context.getClickedPos(), state.setValue(MODE,state.getValue(MODE).next()), 3);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public Class<SignalPlateBlockEntity> getBlockEntityClass(){return SignalPlateBlockEntity.class;}
    @Override public BlockEntityType<? extends SignalPlateBlockEntity> getBlockEntityType(){return ModBlockEntityTypes.SIGNAL_PLATE.get();}
}
