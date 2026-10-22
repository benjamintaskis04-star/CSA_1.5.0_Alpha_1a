package com.benjamintaskis.createsignalextensions.content.speed;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.track.BezierTrackPointLocation;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackTargetingBlockItem;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create-style two-step placement for CSA track-linked signs.
 *
 * Extending Create's TrackTargetingBlockItem is intentional: Create's client-side
 * track outline code recognises this item type, so the chosen track point remains
 * visibly highlighted and curved Bezier track points can be selected using the
 * same interaction path as Create signals/observers.
 *
 * CSA signs remain passive observers; this item never creates an EdgePoint in the
 * railway graph. EdgePointType.SIGNAL is supplied only so Create's curved-track
 * targeting machinery can validate/encode a curve selection.
 */
public class TrackLinkedBlockItem extends TrackTargetingBlockItem {
    private static final double STRAIGHT_MAX_PLACEMENT_DISTANCE = 16.0;

    public TrackLinkedBlockItem(Block block, Item.Properties properties) {
        super(block, properties, EdgePointType.SIGNAL);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        Player player = context.getPlayer();
        if (player == null)
            return InteractionResult.FAIL;

        // Same reset gesture as Create's track-targeting blocks.
        if (player.isShiftKeyDown() && stack.has(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_POS)) {
            if (!level.isClientSide) {
                clearSelection(stack);
                player.displayClientMessage(Component.literal("Track link cleared").withStyle(ChatFormatting.YELLOW), true);
            }
            return InteractionResult.SUCCESS;
        }

        // Straight-track selection. Curved selections are supplied by the inherited
        // TrackTargetingBlockItem#useOnCurve path and Create's own selection packet.
        if (clickedState.getBlock() instanceof ITrackBlock track) {
            if (!level.isClientSide) {
                boolean front = track.getNearestTrackAxis(level, clickedPos, clickedState, player.getLookAngle())
                        .getSecond() == AxisDirection.POSITIVE;
                stack.set(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_POS, clickedPos);
                stack.set(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_DIRECTION, front);
                stack.remove(AllDataComponents.TRACK_TARGETING_ITEM_BEZIER);
                player.displayClientMessage(Component.literal("Track selected - place the sign").withStyle(ChatFormatting.GREEN), true);
            }
            return InteractionResult.SUCCESS;
        }

        if (!stack.has(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_POS)) {
            if (!level.isClientSide)
                player.displayClientMessage(Component.literal("Select a track before placing this sign").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        BlockPos targetTrack = stack.get(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_POS);
        boolean targetDirection = stack.getOrDefault(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_DIRECTION, false);
        BezierTrackPointLocation targetBezier = stack.get(AllDataComponents.TRACK_TARGETING_ITEM_BEZIER);
        BlockPos placedPos = clickedPos.relative(context.getClickedFace(), clickedState.canBeReplaced() ? 0 : 1);

        double maxDistance = targetBezier == null
                ? STRAIGHT_MAX_PLACEMENT_DISTANCE
                : AllConfigs.server().trains.maxTrackPlacementLength.get() + 16.0;
        if (targetTrack == null || !targetTrack.closerThan(placedPos, maxDistance)) {
            if (!level.isClientSide)
                player.displayClientMessage(Component.literal("Selected track is too far away").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        InteractionResult result = superBlockPlacement(context);
        if (!level.isClientSide && result != InteractionResult.FAIL) {
            BlockEntity be = level.getBlockEntity(placedPos);
            if (be instanceof TrackLinkedBlockEntity linked) {
                linked.setTrackTarget(targetTrack, targetDirection, targetBezier);
                player.displayClientMessage(Component.literal("Sign linked to track").withStyle(ChatFormatting.GREEN), true);
            }
            clearSelection(stack);
        }
        return result;
    }

    /**
     * Bypass TrackTargetingBlockItem#useOn here: CSA signs do not create Create
     * edge points. Calling BlockItem's placement logic via super's superclass is
     * not legal Java syntax, so temporarily remove the targeting components and
     * delegate to TrackTargetingBlockItem with placement data restored afterwards.
     * TrackTargetingBlockItem then performs the ordinary BlockItem placement.
     */
    private InteractionResult superBlockPlacement(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        BlockPos pos = stack.get(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_POS);
        Boolean dir = stack.get(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_DIRECTION);
        BezierTrackPointLocation bezier = stack.get(AllDataComponents.TRACK_TARGETING_ITEM_BEZIER);

        // Let Create's implementation carry the selection into the placed BE. Our
        // BE ignores Create's TargetTrack/Bezier tags and is populated immediately
        // after placement by setTrackTarget(), so the data is harmless.
        InteractionResult result = super.useOn(context);

        // super.useOn clears the targeting components; restore them briefly so our
        // caller can read/clear consistently after successful placement.
        if (result != InteractionResult.FAIL && pos != null) {
            stack.set(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_POS, pos);
            if (dir != null)
                stack.set(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_DIRECTION, dir);
            if (bezier != null)
                stack.set(AllDataComponents.TRACK_TARGETING_ITEM_BEZIER, bezier);
        }
        return result;
    }

    private static void clearSelection(ItemStack stack) {
        stack.remove(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_POS);
        stack.remove(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_DIRECTION);
        stack.remove(AllDataComponents.TRACK_TARGETING_ITEM_BEZIER);
    }
}
