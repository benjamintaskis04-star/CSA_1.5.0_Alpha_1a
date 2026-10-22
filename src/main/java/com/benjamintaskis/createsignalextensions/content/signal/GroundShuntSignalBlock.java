package com.benjamintaskis.createsignalextensions.content.signal;

import com.benjamintaskis.createsignalextensions.content.route.SignalLinkingToolItem;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 0.1.4 Alpha 1 ground-mounted NSW shunt signal.
 *
 * Physical aspects:
 *  - STOP: both red lamps illuminated.
 *  - SHUNT PROCEED: yellow lamp illuminated.
 *
 * SHUNT PROCEED is intentionally a restrictive authority. Alpha 1 exposes that
 * semantic through ShuntDisplayedAspect; actual train-speed enforcement is left
 * for the shared 0.1.4 speed-restriction system.
 */
public class GroundShuntSignalBlock extends HorizontalDirectionalBlock implements IWrenchable {
    public static final MapCodec<GroundShuntSignalBlock> CODEC = simpleCodec(GroundShuntSignalBlock::new);
    public static final EnumProperty<ShuntDisplayedAspect> ASPECT =
            EnumProperty.create("aspect", ShuntDisplayedAspect.class);

    public GroundShuntSignalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ASPECT, ShuntDisplayedAspect.STOP));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(ASPECT, ShuntDisplayedAspect.STOP);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ASPECT);
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level instanceof ServerLevel serverLevel) {
            SignalAspectLinkManager manager = SignalAspectLinkManager.get(serverLevel);
            BlockPos linked = manager.linkedSignal(pos);
            player.displayClientMessage(Component.literal(
                    signalDescription() + ": "
                            + state.getValue(ASPECT).getSerializedName().replace('_', ' ').toUpperCase()
                            + (linked == null ? " | unlinked" : " | Create signal " + linked.toShortString())
                            + (state.getValue(ASPECT).isRestrictiveProceed() ? " | RESTRICTIVE" : "")), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Closely follows the supplied Blockbench geometry footprint.
        VoxelShape south = Shapes.or(
                Block.box(1, 0, 5, 5, 17, 10),
                Block.box(2, 0, 5, 4, 11, 7)
        );
        return rotateSouthShape(south, state.getValue(FACING));
    }

    protected String signalDescription() {
        return "Ground shunt signal";
    }

    protected static VoxelShape rotateSouthShape(VoxelShape shape, Direction facing) {
        int turns = switch (facing) {
            case SOUTH -> 0;
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> 0;
        };
        VoxelShape out = shape;
        for (int i = 0; i < turns; i++) {
            VoxelShape next = Shapes.empty();
            for (var box : out.toAabbs())
                next = Shapes.or(next, Block.box((1.0 - box.maxZ) * 16.0, box.minY * 16.0, box.minX * 16.0,
                        (1.0 - box.minZ) * 16.0, box.maxY * 16.0, box.maxX * 16.0));
            out = next;
        }
        return out;
    }
}
