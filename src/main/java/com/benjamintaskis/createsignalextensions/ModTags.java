package com.benjamintaskis.createsignalextensions;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    public static final TagKey<Block> HEIGHTVARIABLE_BLOCKS = TagKey.create(Registries.BLOCK, CreateSignallingAdditions.id("heightvariable"));
    public static final TagKey<Item> HEIGHTVARIABLE_ITEMS = TagKey.create(Registries.ITEM, CreateSignallingAdditions.id("heightvariable"));
    private ModTags() {}
}
