package com.benjamintaskis.createsignalextensions.content.signal;

import com.benjamintaskis.createsignalextensions.ModBlockEntityTypes;
import com.benjamintaskis.createsignalextensions.content.theatre.ShuntTheatreBlockEntity;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Alpha 5b post-mounted shunt equipped with a green Running Proceed aspect. */
public class PostRunningShuntSignalBlock extends GroundShuntSignalBlock implements IBE<ShuntTheatreBlockEntity> {
    public static final MapCodec<PostRunningShuntSignalBlock> CODEC = simpleCodec(PostRunningShuntSignalBlock::new);
    public PostRunningShuntSignalBlock(Properties p) { super(p); }
    @Override protected MapCodec<? extends GroundShuntSignalBlock> codec() { return CODEC; }
    @Override protected String signalDescription() { return "Post-mounted running-equipped shunt signal"; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape south = Shapes.or(Block.box(4,0,4,12,14,13), Block.box(4,0,4,12,2,12));
        return rotateSouthShape(south, state.getValue(FACING));
    }
    @Override public void onRemove(BlockState s, Level l, BlockPos p, BlockState n, boolean m) { IBE.onRemove(s,l,p,n); }
    @Override public Class<ShuntTheatreBlockEntity> getBlockEntityClass() { return ShuntTheatreBlockEntity.class; }
    @Override public BlockEntityType<? extends ShuntTheatreBlockEntity> getBlockEntityType() { return ModBlockEntityTypes.SHUNT_THEATRE.get(); }
}
