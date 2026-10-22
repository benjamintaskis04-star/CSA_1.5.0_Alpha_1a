package com.benjamintaskis.createsignalextensions.content.signal;

import com.benjamintaskis.createsignalextensions.content.HeightVariable;
import com.benjamintaskis.createsignalextensions.ModBlocks;
import com.benjamintaskis.createsignalextensions.content.route.SignalLinkingToolItem;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Mainline route-indication lamp, including combined shunt/dead-end heads. */
public class MainlineAddonLampBlock extends HorizontalDirectionalBlock implements IWrenchable {
    public enum Kind { SHUNT, DEAD_END }
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    public static final BooleanProperty COMBINED = BooleanProperty.create("combined");
    public static final BooleanProperty SHUNT_LIT = BooleanProperty.create("shunt_lit");
    public static final BooleanProperty DEAD_END_LIT = BooleanProperty.create("dead_end_lit");
    private final Kind kind;

    public MainlineAddonLampBlock(Properties properties, Kind kind) {
        super(properties); this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HeightVariable.VERTICAL_OFFSET, HeightVariable.DEFAULT).setValue(LIT, false)
                .setValue(RIGHT, false).setValue(COMBINED, false).setValue(SHUNT_LIT, false).setValue(DEAD_END_LIT, false));
    }
    public Kind kind() { return kind; }
    public boolean contains(BlockState state, Kind wanted) { return kind == wanted || state.getValue(COMBINED); }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return simpleCodec(p -> new MainlineAddonLampBlock(p, kind)); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()).setValue(HeightVariable.VERTICAL_OFFSET, HeightVariable.DEFAULT); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, HeightVariable.VERTICAL_OFFSET, LIT, RIGHT, COMBINED, SHUNT_LIT, DEAD_END_LIT); }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof SignalLinkingToolItem tool) { tool.useOn(new UseOnContext(player, hand, hit)); return ItemInteractionResult.SUCCESS; }
        boolean addingOther = (kind == Kind.SHUNT && stack.is(ModBlocks.NSW_MAINLINE_DEAD_END_LAMP.get().asItem()))
                || (kind == Kind.DEAD_END && stack.is(ModBlocks.NSW_MAINLINE_SHUNT_LAMP.get().asItem()));
        if (addingOther && !state.getValue(COMBINED)) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(COMBINED, true), 3);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Alpha 6f: selection shape follows the actual addon geometry rather than a full/oversized block.
        VoxelShape shape;
        if (kind == Kind.SHUNT) {
            shape = Shapes.or(
                    Block.box(5, 9, 0, 11, 15, 2),
                    Block.box(3, 12, -13, 13, 14, -3),
                    Block.box(7, 12, -3, 9, 14, 0));
            if (state.getValue(COMBINED)) shape = Shapes.or(shape, deadEndShape(state.getValue(RIGHT)));
        } else {
            shape = deadEndShape(state.getValue(RIGHT));
            if (state.getValue(COMBINED)) shape = Shapes.or(shape,
                    Block.box(5, 9, 0, 11, 15, 2),
                    Block.box(3, 12, -13, 13, 14, -3),
                    Block.box(7, 12, -3, 9, 14, 0));
        }
        return rotateSouthShape(shape, state.getValue(FACING));
    }

    private static VoxelShape deadEndShape(boolean right) {
        return right
                ? Shapes.or(Block.box(13, 4, 0, 19, 10, 2), Block.box(3, 6, -13, 13, 8, -3), Block.box(13, 6, -7, 17, 8, 0))
                : Shapes.or(Block.box(-3, 4, 0, 3, 10, 2), Block.box(3, 6, -13, 13, 8, -3), Block.box(-1, 6, -7, 3, 8, 0));
    }

    private static VoxelShape rotateSouthShape(VoxelShape shape, Direction facing) {
        int turns = switch (facing) { case SOUTH -> 0; case WEST -> 1; case NORTH -> 2; case EAST -> 3; default -> 0; };
        VoxelShape out = shape;
        for (int i = 0; i < turns; i++) {
            VoxelShape next = Shapes.empty();
            for (var box : out.toAabbs())
                next = Shapes.or(next, Block.box((1.0-box.maxZ)*16.0, box.minY*16.0, box.minX*16.0,
                        (1.0-box.minZ)*16.0, box.maxY*16.0, box.maxX*16.0));
            out = next;
        }
        return out;
    }

    @Override public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (state.getValue(COMBINED)) {
            if (!level.isClientSide) {
                Block.popResource(level, context.getClickedPos(), new ItemStack(ModBlocks.NSW_MAINLINE_SHUNT_LAMP.get().asItem()));
                Block.popResource(level, context.getClickedPos(), new ItemStack(ModBlocks.NSW_MAINLINE_DEAD_END_LAMP.get().asItem()));
                level.removeBlock(context.getClickedPos(), false);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return IWrenchable.super.onSneakWrenched(state, context);
    }

    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (kind == Kind.DEAD_END) {
            if (!level.isClientSide) level.setBlock(context.getClickedPos(), state.cycle(RIGHT), 3);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return IWrenchable.super.onWrenched(state, context);
    }

    @Override public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) { return net.minecraft.world.level.block.RenderShape.MODEL; }
}
