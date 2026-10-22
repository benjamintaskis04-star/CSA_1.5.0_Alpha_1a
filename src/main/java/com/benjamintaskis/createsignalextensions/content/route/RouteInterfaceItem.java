package com.benjamintaskis.createsignalextensions.content.route;

import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

public class RouteInterfaceItem extends BlockItem {
    private static final String KEY_X = "BoundSignalX";
    private static final String KEY_Y = "BoundSignalY";
    private static final String KEY_Z = "BoundSignalZ";
    private static final String KEY_DIM = "BoundSignalDimension";

    public RouteInterfaceItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var be = level.getBlockEntity(pos);

        // Right-clicking a Create signal stores it on the item instead of placing the block.
        if (be instanceof SignalBlockEntity) {
            if (!level.isClientSide) {
                ItemStack stack = context.getItemInHand();
                bindToSignal(stack, level, pos);

                if (context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(
                            Component.translatable(
                                    "message.create_signal_extensions.item_bound_signal",
                                    pos.getX(), pos.getY(), pos.getZ()),
                            true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return super.useOn(context);
    }

    public static void bindToSignal(ItemStack stack, net.minecraft.world.level.Level level, BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt(KEY_X, pos.getX());
            tag.putInt(KEY_Y, pos.getY());
            tag.putInt(KEY_Z, pos.getZ());
            tag.putString(KEY_DIM, level.dimension().location().toString());
        });
    }

    public static BlockPos getBoundSignal(ItemStack stack, net.minecraft.world.level.Level level) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();

        if (!tag.contains(KEY_X) || !tag.contains(KEY_Y) || !tag.contains(KEY_Z))
            return null;

        if (tag.contains(KEY_DIM)) {
            String dim = tag.getString(KEY_DIM);
            if (!dim.equals(level.dimension().location().toString()))
                return null;
        }

        return new BlockPos(tag.getInt(KEY_X), tag.getInt(KEY_Y), tag.getInt(KEY_Z));
    }
}
