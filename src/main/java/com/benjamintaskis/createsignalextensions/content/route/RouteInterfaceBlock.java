package com.benjamintaskis.createsignalextensions.content.route;

import com.benjamintaskis.createsignalextensions.content.HeightVariable;
import com.benjamintaskis.createsignalextensions.ModBlockEntityTypes;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class RouteInterfaceBlock extends Block implements IBE<RouteInterfaceBlockEntity>, IWrenchable {
    public RouteInterfaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HeightVariable.VERTICAL_OFFSET, HeightVariable.DEFAULT));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HeightVariable.VERTICAL_OFFSET);
    }


    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        // The Route Interface normally falls through to its empty-hand interaction,
        // which opens the configuration screen. The Signal Linking Tool needs to
        // take priority instead, otherwise selecting/linking a Route Interface is
        // swallowed by the GUI interaction.
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
            if (player.isShiftKeyDown()) {
                BlockPos found = be.findNearestCreateSignal(16);
                if (found == null) {
                    be.clearLinkedSignal();
                    player.displayClientMessage(Component.translatable("message.create_signal_extensions.no_signal"), true);
                } else {
                    be.setLinkedSignal(found);
                    player.displayClientMessage(Component.translatable(
                            "message.create_signal_extensions.linked",
                            found.getX(), found.getY(), found.getZ()), true);
                }
                return InteractionResult.SUCCESS;
            }

            if (player instanceof ServerPlayer serverPlayer)
                be.openConfigurationScreen(serverPlayer);
            return InteractionResult.SUCCESS;
        });
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (level.isClientSide)
            return;

        BlockPos boundSignal = RouteInterfaceItem.getBoundSignal(stack, level);
        if (boundSignal == null)
            return;

        onBlockEntityUse(level, pos, be -> {
            be.setLinkedSignal(boundSignal);
            return InteractionResult.SUCCESS;
        });
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<RouteInterfaceBlockEntity> getBlockEntityClass() {
        return RouteInterfaceBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RouteInterfaceBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.ROUTE_INTERFACE.get();
    }

    @Override public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) { return net.minecraft.world.level.block.RenderShape.MODEL; }
}
