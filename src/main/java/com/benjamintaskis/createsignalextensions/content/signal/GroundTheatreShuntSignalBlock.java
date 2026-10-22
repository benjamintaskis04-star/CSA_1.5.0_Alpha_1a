package com.benjamintaskis.createsignalextensions.content.signal;

import com.benjamintaskis.createsignalextensions.ModBlockEntityTypes;
import com.benjamintaskis.createsignalextensions.content.theatre.ShuntTheatreBlockEntity;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

/** Alpha 5a ground shunt with two integral small-theatre cells. */
public class GroundTheatreShuntSignalBlock extends GroundShuntSignalBlock implements IBE<ShuntTheatreBlockEntity> {
    public static final MapCodec<GroundTheatreShuntSignalBlock> CODEC=simpleCodec(GroundTheatreShuntSignalBlock::new);
    public GroundTheatreShuntSignalBlock(Properties p){super(p);}
    @Override protected MapCodec<? extends GroundShuntSignalBlock> codec(){return CODEC;}
    @Override protected String signalDescription(){return "Ground theatre shunt signal";}
    @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState n,boolean m){IBE.onRemove(s,l,p,n);}
    @Override public Class<ShuntTheatreBlockEntity> getBlockEntityClass(){return ShuntTheatreBlockEntity.class;}
    @Override public BlockEntityType<? extends ShuntTheatreBlockEntity> getBlockEntityType(){return ModBlockEntityTypes.SHUNT_THEATRE.get();}
}
