package com.benjamintaskis.createsignalextensions.content.speed;

import com.benjamintaskis.createsignalextensions.content.HeightVariable;
import com.benjamintaskis.createsignalextensions.ModBlockEntityTypes;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class SpeedBoardBlock extends HorizontalDirectionalBlock implements IBE<SpeedBoardBlockEntity>, IWrenchable {
    public static final BooleanProperty UNRESTRICTED = BooleanProperty.create("unrestricted");
    public static final MapCodec<SpeedBoardBlock> CODEC = simpleCodec(SpeedBoardBlock::new);
    public SpeedBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(UNRESTRICTED, false).setValue(HeightVariable.VERTICAL_OFFSET, HeightVariable.DEFAULT));
    }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(UNRESTRICTED, false).setValue(HeightVariable.VERTICAL_OFFSET, HeightVariable.DEFAULT);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, UNRESTRICTED, HeightVariable.VERTICAL_OFFSET);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof SpeedBoardBlockEntity be && !be.isUnrestricted())
            be.openConfigurationScreen(player);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        return onBlockEntityUse(level, pos, be -> {
            if (player instanceof ServerPlayer sp && !be.isUnrestricted()) be.openConfigurationScreen(sp);
            return InteractionResult.SUCCESS;
        });
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> Block.box(0, 0, 0, 16, 16, 2);
            case NORTH -> Block.box(0, 0, 14, 16, 16, 16);
            case WEST -> Block.box(14, 0, 0, 16, 16, 16);
            case EAST -> Block.box(0, 0, 0, 2, 16, 16);
            default -> Block.box(0, 0, 14, 16, 16, 16);
        };
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (state.getBlock() != newState.getBlock() && level instanceof net.minecraft.server.level.ServerLevel server)
            SpeedZoneBoardData.get(server).remove(pos);
        IBE.onRemove(state, level, pos, newState);
    }
    @Override public Class<SpeedBoardBlockEntity> getBlockEntityClass() { return SpeedBoardBlockEntity.class; }
    @Override public BlockEntityType<? extends SpeedBoardBlockEntity> getBlockEntityType() { return ModBlockEntityTypes.SPEED_BOARD.get(); }

    @Override public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) { return net.minecraft.world.level.block.RenderShape.MODEL; }
}
