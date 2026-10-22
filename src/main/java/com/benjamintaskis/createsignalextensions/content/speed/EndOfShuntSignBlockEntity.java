package com.benjamintaskis.createsignalextensions.content.speed;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class EndOfShuntSignBlockEntity extends TrackLinkedBlockEntity {
    public EndOfShuntSignBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
}
