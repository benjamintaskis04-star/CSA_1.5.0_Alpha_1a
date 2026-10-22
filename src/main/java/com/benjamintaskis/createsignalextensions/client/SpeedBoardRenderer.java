package com.benjamintaskis.createsignalextensions.client;

import com.benjamintaskis.createsignalextensions.content.HeightVariable;
import com.benjamintaskis.createsignalextensions.content.speed.SpeedBoardBlock;
import com.benjamintaskis.createsignalextensions.content.speed.SpeedBoardBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;

public class SpeedBoardRenderer implements BlockEntityRenderer<SpeedBoardBlockEntity> {
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final double FACE = .438;
    private final Font font;

    public SpeedBoardRenderer(BlockEntityRendererProvider.Context c) { font = c.getFont(); }

    @Override
    public void render(SpeedBoardBlockEntity be, float pt, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!be.isUnrestricted()) {
            if (be.hasMuPanel() && be.hasHighPanel()) {
                draw(be, label(be, be.getDefaultSpeedKmh()), .50, pose, buffers);
                draw(be, label(be, be.getMuSpeedKmh()), 0, pose, buffers);
                draw(be, label(be, be.getHighSpeedKmh()), -.50, pose, buffers);
            } else if (be.hasMuPanel()) {
                draw(be, label(be, be.getDefaultSpeedKmh()), .25, pose, buffers);
                draw(be, label(be, be.getMuSpeedKmh()), -.25, pose, buffers);
            } else if (be.hasHighPanel()) {
                draw(be, label(be, be.getDefaultSpeedKmh()), .25, pose, buffers);
                draw(be, label(be, be.getHighSpeedKmh()), -.25, pose, buffers);
            } else {
                draw(be, label(be, be.getDefaultSpeedKmh()), 0, pose, buffers);
            }
        }
        drawTrackSelectionOverlay(be, pose, buffers);
    }

    private String label(SpeedBoardBlockEntity be, int speed) { return (be.isTurnoutBoard() ? "X" : "") + speed; }

    private void draw(SpeedBoardBlockEntity be, String text, double yo, PoseStack pose, MultiBufferSource buffers) {
        Direction facing = be.getBlockState().getValue(SpeedBoardBlock.FACING);
        pose.pushPose();
        pose.translate(0, HeightVariable.blocks(be.getBlockState()), 0);
        pose.translate(.5, .5 + yo, .5);
        switch (facing) {
            case NORTH -> { pose.translate(0, 0, FACE); pose.mulPose(Axis.YP.rotationDegrees(180)); }
            case SOUTH -> pose.translate(0, 0, -FACE);
            case WEST -> { pose.translate(FACE, 0, 0); pose.mulPose(Axis.YP.rotationDegrees(-90)); }
            case EAST -> { pose.translate(-FACE, 0, 0); pose.mulPose(Axis.YP.rotationDegrees(90)); }
            default -> { }
        }
        float scale = .035f;
        pose.scale(scale, -scale, scale);
        font.drawInBatch(text, -font.width(text) / 2f, -font.lineHeight / 2f, 0xFF000000, false,
                pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, FULL_BRIGHT);
        pose.popPose();
    }

    /**
     * Beta 2 Dev 2: when the player looks directly at a placed speed board,
     * show the persisted linked track point and the direction in which a train
     * must travel for the board to apply. This replaces the always-visible Dev 1
     * marker so the world stays uncluttered during normal play.
     */
    private void drawTrackSelectionOverlay(SpeedBoardBlockEntity be, PoseStack pose, MultiBufferSource buffers) {
        if (be.getLevel() == null || !be.hasTrackTarget()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.hitResult instanceof BlockHitResult hit) || !hit.getBlockPos().equals(be.getBlockPos())) return;

        Vec3 target = be.getTrackTargetPosition(be.getLevel());
        if (target == null) return;

        double ox = target.x - be.getBlockPos().getX();
        double oy = target.y - be.getBlockPos().getY() + 0.08;
        double oz = target.z - be.getBlockPos().getZ();

        Direction facing = be.getBlockState().getValue(SpeedBoardBlock.FACING);
        // Valid movement is into the visible face of the board, i.e. opposite
        // its outward face normal. Keep this identical to SpeedRestrictionManager.
        double dx = -facing.getStepX();
        double dz = -facing.getStepZ();

        pose.pushPose();
        VertexConsumer line = buffers.getBuffer(RenderType.lines());
        var matrix = pose.last();

        // Crosshair centred exactly on the persisted selected track point.
        line.addVertex(matrix.pose(), (float)(ox - 0.22), (float)oy, (float)oz).setColor(255, 255, 255, 255).setNormal(matrix, 1, 0, 0);
        line.addVertex(matrix.pose(), (float)(ox + 0.22), (float)oy, (float)oz).setColor(255, 255, 255, 255).setNormal(matrix, 1, 0, 0);
        line.addVertex(matrix.pose(), (float)ox, (float)oy, (float)(oz - 0.22)).setColor(255, 255, 255, 255).setNormal(matrix, 0, 0, 1);
        line.addVertex(matrix.pose(), (float)ox, (float)oy, (float)(oz + 0.22)).setColor(255, 255, 255, 255).setNormal(matrix, 0, 0, 1);

        // Arrow shaft: arrowhead points in the direction of valid train travel.
        double half = 0.72;
        double sx = ox - dx * half, sz = oz - dz * half;
        double ex = ox + dx * half, ez = oz + dz * half;
        line.addVertex(matrix.pose(), (float)sx, (float)(oy + 0.035), (float)sz).setColor(255, 220, 64, 255).setNormal(matrix, 0, 1, 0);
        line.addVertex(matrix.pose(), (float)ex, (float)(oy + 0.035), (float)ez).setColor(255, 220, 64, 255).setNormal(matrix, 0, 1, 0);

        // Two arrowhead wings, perpendicular to travel.
        double px = -dz, pz = dx;
        double backX = ex - dx * 0.30, backZ = ez - dz * 0.30;
        for (double side : new double[]{-1.0, 1.0}) {
            line.addVertex(matrix.pose(), (float)ex, (float)(oy + 0.035), (float)ez).setColor(255, 220, 64, 255).setNormal(matrix, 0, 1, 0);
            line.addVertex(matrix.pose(), (float)(backX + px * 0.22 * side), (float)(oy + 0.035), (float)(backZ + pz * 0.22 * side))
                    .setColor(255, 220, 64, 255).setNormal(matrix, 0, 1, 0);
        }
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(SpeedBoardBlockEntity be) { return true; }
}
