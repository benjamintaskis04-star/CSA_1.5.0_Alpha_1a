package com.benjamintaskis.createsignalextensions.content.signal;
import com.benjamintaskis.createsignalextensions.content.route.SignalLinkingToolItem;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos; import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand; import net.minecraft.world.ItemInteractionResult; import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack; import net.minecraft.world.item.context.BlockPlaceContext; import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level; import net.minecraft.world.level.block.Block; import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState; import net.minecraft.world.level.block.state.StateDefinition; import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
public class RepeaterSignalBlock extends HorizontalDirectionalBlock implements IWrenchable {
 public static final MapCodec<RepeaterSignalBlock> CODEC=simpleCodec(RepeaterSignalBlock::new); public static final BooleanProperty PROCEED=BooleanProperty.create("proceed");
 public RepeaterSignalBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(PROCEED,false));}
 @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
 @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite()).setValue(PROCEED,false);}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(FACING,PROCEED);}
 @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(stack.getItem() instanceof SignalLinkingToolItem tool){tool.useOn(new UseOnContext(player,hand,hit));return ItemInteractionResult.SUCCESS;}return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
}
