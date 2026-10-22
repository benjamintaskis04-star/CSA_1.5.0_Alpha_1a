package com.benjamintaskis.createsignalextensions.mixin;

import com.benjamintaskis.createsignalextensions.content.HeightVariable;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Moves the baked model only. Dynamic block-entity content applies the same offset in its renderer. */
@Mixin(BlockRenderDispatcher.class)
public class BlockRenderDispatcherMixin {
    @Inject(method = "renderBatched", at = @At("HEAD"))
    private void csa$pushVerticalOffset(BlockState state, BlockPos pos, BlockAndTintGetter level,
                                        PoseStack poseStack, VertexConsumer consumer, boolean checkSides,
                                        RandomSource random, CallbackInfo ci) {
        if (!HeightVariable.supports(state)) return;
        poseStack.pushPose();
        poseStack.translate(0.0, HeightVariable.blocks(state), 0.0);
    }

    @Inject(method = "renderBatched", at = @At("RETURN"))
    private void csa$popVerticalOffset(BlockState state, BlockPos pos, BlockAndTintGetter level,
                                       PoseStack poseStack, VertexConsumer consumer, boolean checkSides,
                                       RandomSource random, CallbackInfo ci) {
        if (HeightVariable.supports(state)) poseStack.popPose();
    }
}
