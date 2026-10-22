package com.benjamintaskis.createsignalextensions.content;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class GuidedBlockItem extends BlockItem {
    private final String tooltipKey;

    public GuidedBlockItem(Block block, Item.Properties properties, String tooltipKey) {
        super(block, properties);
        this.tooltipKey = tooltipKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (Screen.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.create_signal_extensions." + tooltipKey + ".summary").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.create_signal_extensions." + tooltipKey + ".usage1").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.create_signal_extensions." + tooltipKey + ".usage2").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.create_signal_extensions.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
