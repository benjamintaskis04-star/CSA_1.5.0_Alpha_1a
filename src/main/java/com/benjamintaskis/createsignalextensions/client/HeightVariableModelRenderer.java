package com.benjamintaskis.createsignalextensions.client;

import com.benjamintaskis.createsignalextensions.content.HeightVariable;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Renders an ordinary baked CSA block model through a BER so vertical translation is reliable. */
public final class HeightVariableModelRenderer {
    private HeightVariableModelRenderer() {}
    public static void render(BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.0, HeightVariable.blocks(state), 0.0);
        var mc = Minecraft.getInstance();
        var model = mc.getBlockRenderer().getBlockModel(state);
        var consumer = buffers.getBuffer(RenderType.cutout());
        mc.getBlockRenderer().getModelRenderer().renderModel(
                pose.last(), consumer, state, model, 1.0f, 1.0f, 1.0f, light, overlay, ModelData.EMPTY, RenderType.cutout());
        pose.popPose();
    }
}
