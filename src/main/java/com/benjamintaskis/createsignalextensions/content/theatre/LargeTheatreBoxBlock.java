package com.benjamintaskis.createsignalextensions.content.theatre;

import com.benjamintaskis.createsignalextensions.content.HeightVariable;
import com.benjamintaskis.createsignalextensions.ModBlockEntityTypes;
import com.benjamintaskis.createsignalextensions.content.route.SignalLinkingToolItem;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.phys.BlockHitResult;

public class LargeTheatreBoxBlock extends HorizontalDirectionalBlock implements IBE<LargeTheatreBoxBlockEntity>, IWrenchable {
    public static final MapCodec<LargeTheatreBoxBlock> CODEC = simpleCodec(LargeTheatreBoxBlock::new);

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public LargeTheatreBoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HeightVariable.VERTICAL_OFFSET, HeightVariable.DEFAULT));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(HeightVariable.VERTICAL_OFFSET, HeightVariable.DEFAULT);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, HeightVariable.VERTICAL_OFFSET);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof SignalLinkingToolItem tool) {
            tool.useOn(new UseOnContext(player, hand, hit));
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide)
            return InteractionResult.SUCCESS;

        return onBlockEntityUse(level, pos, be -> {
            BlockPos linkedRoute = be.getLinkedRouteInterfacePos();
            BlockPos linkedSignal = be.getLinkedSignalPos();
            String shown = be.getIndication();
            if (linkedRoute != null) {
                player.displayClientMessage(Component.literal(
                        "Large Theatre Box -> Route Interface " + linkedRoute.getX() + ", " + linkedRoute.getY() + ", " + linkedRoute.getZ()
                                + (shown.isBlank() ? " (blank)" : " (showing " + shown + ")")), true);
            } else if (linkedSignal != null) {
                player.displayClientMessage(Component.literal(
                        "Large Theatre Box -> CSA signal " + linkedSignal.getX() + ", " + linkedSignal.getY() + ", " + linkedSignal.getZ()
                                + (shown.isBlank() ? " (blank)" : " (showing turnout " + shown + ")")), true);
            } else {
                player.displayClientMessage(Component.literal("Large Theatre Box: unlinked"), true);
            }
            return InteractionResult.SUCCESS;
        });
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<LargeTheatreBoxBlockEntity> getBlockEntityClass() {
        return LargeTheatreBoxBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LargeTheatreBoxBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.LARGE_THEATRE_BOX.get();
    }

    @Override public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) { return net.minecraft.world.level.block.RenderShape.MODEL; }
}
