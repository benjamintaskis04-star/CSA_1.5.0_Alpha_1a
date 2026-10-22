package com.benjamintaskis.createsignalextensions.content.signal;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import com.benjamintaskis.createsignalextensions.network.OpenConvergingSignalConfigPayload;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import com.benjamintaskis.createsignalextensions.content.HeightVariable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.benjamintaskis.createsignalextensions.content.route.SignalLinkingToolItem;
import com.benjamintaskis.createsignalextensions.config.CommonConfig;

/**
 * First proper NSW single-colour-light signal head.
 *
 * Alpha 3 currently provides two physical variants of the same item:
 * no turnout indicator, or a both-directions turnout indicator.
 */
public class SingleColourLightSignalBlock extends HorizontalDirectionalBlock implements IWrenchable {
    public enum PhysicalType { NORMAL, DIVERGING, CONVERGING, DISTANT, FIXED_RED, TWO_ASPECT }
    private final TurnoutCapability fixedCapability;
    private final PhysicalType physicalType;
    public static final MapCodec<SingleColourLightSignalBlock> CODEC = simpleCodec(SingleColourLightSignalBlock::new);
    public static final IntegerProperty VERTICAL_OFFSET = HeightVariable.VERTICAL_OFFSET;
    public static final EnumProperty<TurnoutCapability> TURNOUT_CAPABILITY =
            EnumProperty.create("turnout", TurnoutCapability.class);
    public static final EnumProperty<SingleColourDisplayedAspect> ASPECT =
            EnumProperty.create("aspect", SingleColourDisplayedAspect.class);

    public SingleColourLightSignalBlock(Properties properties) { this(properties, TurnoutCapability.NONE, PhysicalType.NORMAL); }

    protected SingleColourLightSignalBlock(Properties properties, TurnoutCapability capability, PhysicalType physicalType) {
        super(properties);
        this.fixedCapability = capability;
        this.physicalType = physicalType;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(VERTICAL_OFFSET, 16)
                .setValue(TURNOUT_CAPABILITY, fixedCapability)
                .setValue(ASPECT, physicalType == PhysicalType.DISTANT ? SingleColourDisplayedAspect.UNLINKED : SingleColourDisplayedAspect.STOP));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(VERTICAL_OFFSET, 16)
                .setValue(TURNOUT_CAPABILITY, fixedCapability)
                .setValue(ASPECT, physicalType == PhysicalType.DISTANT ? SingleColourDisplayedAspect.UNLINKED : SingleColourDisplayedAspect.STOP);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, TURNOUT_CAPABILITY, ASPECT, VERTICAL_OFFSET);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        // Give the Signal Linking Tool first refusal, exactly like the Route Interface
        // and theatre boxes. Without this, the block interaction swallows the tool click.
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

        if (level instanceof ServerLevel serverLevel) {
            SignalAspectLinkManager manager = SignalAspectLinkManager.get(serverLevel);
            boolean hasInputLink = physicalType == PhysicalType.DISTANT
                    ? !manager.followingDisplaysRaw(pos).isEmpty()
                    : manager.linkedSignal(pos) != null;
            if (player.isShiftKeyDown() && !hasInputLink) {
                boolean enabled = manager.toggleDisplayMode(pos);
                player.displayClientMessage(Component.literal(
                        "Signal Display Mode " + (enabled ? "enabled" : "disabled")), true);
                if (enabled) SignalDisplayMode.apply(serverLevel, pos);
                return InteractionResult.SUCCESS;
            }

            BlockPos linked = manager.linkedSignal(pos);
            if (player.isShiftKeyDown() && linked != null && physicalType != PhysicalType.DISTANT) {
                String mode = manager.cycleLookAheadMode(pos);
                BlockPos previewTarget = manager.showLookAheadPreview(serverLevel, pos);
                player.displayClientMessage(Component.literal("Look-ahead: " + mode
                        + (previewTarget == null ? "" : " | highlighting " + previewTarget.toShortString())), true);
                return InteractionResult.SUCCESS;
            }

            if (physicalType == PhysicalType.CONVERGING && player instanceof ServerPlayer serverPlayer) {
                TurnoutDirection direction = manager.convergingDirection(pos);
                PacketDistributor.sendToPlayer(serverPlayer,
                        new OpenConvergingSignalConfigPayload(pos, direction.name()));
                return InteractionResult.SUCCESS;
            }

            player.displayClientMessage(Component.literal(
                    "Single-colour signal: " + (state.getValue(TURNOUT_CAPABILITY) == TurnoutCapability.NONE ? "no turnout" : state.getValue(TURNOUT_CAPABILITY).getSerializedName() + " turnout")
                            + " | showing " + state.getValue(ASPECT).getSerializedName().replace('_', ' ').toUpperCase()
                            + (linked == null ? " | unlinked" : " | Create signal " + linked.toShortString())), true);
        }
        return InteractionResult.SUCCESS;
    }


    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel)
            SignalAspectLinkManager.get(serverLevel).disableDisplayMode(pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        SignalAspectLinkManager manager = SignalAspectLinkManager.get(level);
        // Beta 3 Dev 2b: a Fixed Red is an absolute Stop head. Its Create link is
        // retained for route/following-signal awareness, but its main aspect never clears.
        if (physicalType == PhysicalType.FIXED_RED) {
            if (state.getValue(ASPECT) != SingleColourDisplayedAspect.STOP)
                level.setBlock(pos, state.setValue(ASPECT, SingleColourDisplayedAspect.STOP), 3);
            level.scheduleTick(pos, this, 4);
            return;
        }
        // Distant signals deliberately do not link to a Create signal. Their valid
        // input is the direct following-NSW-signal relationship instead. Treating
        // linkedSignal(pos) == null as "unlinked" caused the scheduled block tick
        // to fight the aspect manager every four ticks, producing the visible
        // linked/unlinked alternation.
        boolean hasInputLink = physicalType == PhysicalType.DISTANT
                ? !manager.followingDisplaysRaw(pos).isEmpty()
                : manager.linkedSignal(pos) != null;
        if (manager.isDisplayMode(pos)) {
            if (!hasInputLink) {
                SignalDisplayMode.apply(level, pos);
                level.scheduleTick(pos, this, 4);
                return;
            }
            manager.disableDisplayMode(pos);
        }
        if (!hasInputLink && physicalType == PhysicalType.TWO_ASPECT) {
            // Two-aspect heads must never flash or show an unlit third indication.
            if (state.getValue(ASPECT) != SingleColourDisplayedAspect.STOP)
                level.setBlock(pos, state.setValue(ASPECT, SingleColourDisplayedAspect.STOP), 3);
            level.scheduleTick(pos, this, 4);
            return;
        }
        if (!hasInputLink) {
            boolean x = CommonConfig.outOfUseX();
            if (physicalType == PhysicalType.DISTANT && !x) {
                if (state.getValue(ASPECT) != SingleColourDisplayedAspect.UNLINKED)
                    level.setBlock(pos, state.setValue(ASPECT, SingleColourDisplayedAspect.UNLINKED), 3);
            } else {
                boolean flashOn = ((level.getGameTime() / 20L) & 1L) == 0L;
                SingleColourDisplayedAspect wanted = x ? SingleColourDisplayedAspect.OUT_OF_USE
                        : (flashOn ? SingleColourDisplayedAspect.STOP : SingleColourDisplayedAspect.DARK);
                if (state.getValue(ASPECT) != wanted)
                    level.setBlock(pos, state.setValue(ASPECT, wanted), 3);
            }
        }
        level.scheduleTick(pos, this, 4);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape south = Shapes.or(Block.box(4, 0, 0, 12, 30, 7), Block.box(3, 0, -13, 13, 6, 1));
        return rotateSouthShape(south, state.getValue(FACING));
    }

    private static VoxelShape rotateSouthShape(VoxelShape shape, Direction facing) {
        int turns = switch (facing) { case SOUTH -> 0; case WEST -> 1; case NORTH -> 2; case EAST -> 3; default -> 0; };
        VoxelShape out = shape;
        for (int i=0;i<turns;i++) {
            VoxelShape next=Shapes.empty();
            for (var box:out.toAabbs()) next=Shapes.or(next, Block.box((1.0-box.maxZ)*16.0,box.minY*16.0,box.minX*16.0,(1.0-box.minZ)*16.0,box.maxY*16.0,box.maxX*16.0));
            out=next;
        }
        return out;
    }

    public PhysicalType physicalType() { return physicalType; }
    public TurnoutCapability fixedCapability() { return fixedCapability; }

    @Override public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) { return net.minecraft.world.level.block.RenderShape.MODEL; }
}
