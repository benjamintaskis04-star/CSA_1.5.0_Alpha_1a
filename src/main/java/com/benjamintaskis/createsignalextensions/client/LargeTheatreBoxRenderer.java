package com.benjamintaskis.createsignalextensions.client;

import com.benjamintaskis.createsignalextensions.content.HeightVariable;
import com.benjamintaskis.createsignalextensions.content.theatre.LargeTheatreBoxBlock;
import com.benjamintaskis.createsignalextensions.content.theatre.LargeTheatreBoxBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

public class LargeTheatreBoxRenderer implements BlockEntityRenderer<LargeTheatreBoxBlockEntity> {
    private static final int FULL_BRIGHT = 0x00F000F0;
    private final Font font;

    public LargeTheatreBoxRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(LargeTheatreBoxBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        String text = blockEntity.getIndication();
        if (text == null || text.isBlank())
            return;

        Direction facing = blockEntity.getBlockState().getValue(LargeTheatreBoxBlock.FACING);

        poseStack.pushPose();
        poseStack.translate(0.0, HeightVariable.blocks(blockEntity.getBlockState()), 0.0);
        poseStack.translate(0.5, 0.5, 0.5);

        // Place the indication directly on the physical front face for each facing.
        // This avoids the east/west inversion caused by rotating a north-face offset.
        switch (facing) {
            case NORTH -> {
                poseStack.translate(0.0, 0.05, -0.506);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
            }
            case SOUTH -> {
                poseStack.translate(0.0, 0.05, 0.506);
            }
            case EAST -> {
                poseStack.translate(0.506, 0.05, 0.0);
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
            }
            case WEST -> {
                poseStack.translate(-0.506, 0.05, 0.0);
                poseStack.mulPose(Axis.YP.rotationDegrees(-90.0f));
            }
            default -> {
                poseStack.translate(0.0, 0.05, -0.506);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
            }
        }

        float scale = 0.055f;
        poseStack.scale(scale, -scale, scale);

        float x = -font.width(text) / 2.0f;
        float y = -font.lineHeight / 2.0f;
        font.drawInBatch(text, x, y, 0xFFFFFFFF, false, poseStack.last().pose(), bufferSource,
                Font.DisplayMode.POLYGON_OFFSET, 0, FULL_BRIGHT);

        poseStack.popPose();
    }
}
