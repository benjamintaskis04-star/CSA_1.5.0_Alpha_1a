package com.benjamintaskis.createsignalextensions.client;

import com.benjamintaskis.createsignalextensions.content.signal.PostTheatreShuntSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.PostRunningShuntSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.GroundRunningShuntSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.HangingTheatreShuntSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.HangingRunningShuntSignalBlock;
import com.benjamintaskis.createsignalextensions.content.theatre.ShuntTheatreBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

/** Integral small-theatre renderer for the Alpha 5a shunt family. */
public class ShuntTheatreRenderer implements BlockEntityRenderer<ShuntTheatreBlockEntity> {
    private static final int FULL = 0x00F000F0;

    // =====================================================================
    // MANUAL N/S THEATRE TEXT POSITION TUNING
    // =====================================================================
    // These are the values to edit if NORTH/SOUTH theatre text needs moving.
    // 1 Minecraft model pixel = 1/16 block = 0.0625.
    //
    // X correction is applied AFTER the normal facing transform, so it moves
    // the already-oriented text sideways across the theatre face without
    // changing its rotation, height, depth, recline, or scale.
    //
    // Positive = local text-right; negative = local text-left.
    // If a test moves the text the wrong way, change only the sign.
    // Try increments of 0.0625 (one model pixel) at a time.
    //
    // Ground values affect BOTH Ground Theatre Shunt and Ground Running Shunt.
    // Post values affect BOTH Post Theatre Shunt and Post Running Shunt.
    private static final double NS_GROUND_X_CORRECTION = 0.0; // 7a4-6 attempt: +1 px
    private static final double NS_POST_X_CORRECTION   = 0.0; // 7a4-6 attempt: +1 px

    // Leave these at zero unless vertical/depth tuning is specifically needed.
    private static final double NS_GROUND_Y_CORRECTION = 0.0625;
    private static final double NS_GROUND_Z_CORRECTION = -0.75;
    private static final double NS_POST_Y_CORRECTION   = 0.0;
    private static final double NS_POST_Z_CORRECTION   = 0.50625;

    private final Font font;

    public ShuntTheatreRenderer(BlockEntityRendererProvider.Context context) {
        font = context.getFont();
    }

    @Override
    public void render(ShuntTheatreBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        var indications = be.getIndications();
        Direction facing = be.getBlockState().getValue(com.benjamintaskis.createsignalextensions.content.signal.GroundTheatreShuntSignalBlock.FACING);

        // The two models deliberately use independent transforms.  The post theatre
        // is a miniature upright 2x1 small-theatre display; the ground theatre is
        // physically reclined 22.5 degrees and its lettering follows that face.
        // Alpha 5b running-equipped shunts only have one theatre aperture: the second position is occupied by the green lamp.
        if (be.getBlockState().getBlock() instanceof PostRunningShuntSignalBlock
                || be.getBlockState().getBlock() instanceof HangingRunningShuntSignalBlock) {
            drawPost(indications.get(0), facing, isNorthSouth(facing) ? -0.125 : -0.1, pose, buffers);
            return;
        }
        if (be.getBlockState().getBlock() instanceof GroundRunningShuntSignalBlock) {
            drawGround(indications.get(0), isNorthSouth(facing) ? 1.515 : 1.5775, isNorthSouth(facing) ? .315 : .43625, facing, pose, buffers);
            return;
        }

        if (be.getBlockState().getBlock() instanceof PostTheatreShuntSignalBlock
                || be.getBlockState().getBlock() instanceof HangingTheatreShuntSignalBlock) {
            // ===== POST THEATRE SHUNT POSITION TESTING =====
            // These are the two drawPost calls to edit while positioning the post theatre text.
            // get(0) = Route Interface top-left cell; get(1) = top-right cell.
            // The third argument is lateral position: positive/negative separates the two cells.
            drawPost(indications.get(0), facing, isNorthSouth(facing) ? -0.125 : -0.1, pose, buffers);
            drawPost(indications.get(1), facing, isNorthSouth(facing) ?  0.125 : 0.12, pose, buffers);
            return;
        }

        // ===== GROUND THEATRE SHUNT POSITION TESTING =====
        // These are the two drawGround calls to edit while positioning the ground theatre text.
        // get(0) = Route Interface top-left -> upper aperture.
        // get(1) = Route Interface top-right -> lower aperture.
        // After get(...): first number = vertical position; second number = forward/back depth.
        // Edit these two calls to position LT and RT independently.
	drawGround(indications.get(0), isNorthSouth(facing) ? 1.515 : 1.5775, isNorthSouth(facing) ? .315 : .43625, facing, pose, buffers); // LT
	drawGround(indications.get(1), isNorthSouth(facing) ? 1.265 : 1.3275, isNorthSouth(facing) ? .415 : .33125, facing, pose, buffers);
    }

    // ===== DRAW POST =====
    // Post theatre translation/scale is adjusted here. "lateral" separates the left/right cells.
    private void drawPost(String text, Direction facing, double lateral,
                          PoseStack pose, MultiBufferSource buffers) {
        if (text == null || text.isBlank()) return;
        pose.pushPose();
        pose.translate(.5, .5, .5);

        // Upright miniature 2x1 small-theatre face on the post-mounted model.
        // South face of the theatre box is at z=13/16; render 3.9 model pixels outward from it.
        orientFace(facing, lateral, .300, -0.25, pose);
        if (isNorthSouth(facing)) {
            // MANUAL TUNING: edit NS_POST_* constants at the top of this file.
            pose.translate(NS_POST_X_CORRECTION, NS_POST_Y_CORRECTION, NS_POST_Z_CORRECTION);
        }
        drawText(text, isNorthSouth(facing) ? .010f : .01425f, pose, buffers); // N/S uses corrected Alpha 5a2 tuning; E/W retains current placement
        pose.popPose();
    }

    // ===== DRAW GROUND =====
    // Ground theatre translation, 22.5-degree recline and scale are adjusted here.
    private void drawGround(String text, double y, double depth, Direction facing,
                            PoseStack pose, MultiBufferSource buffers) {
        if (text == null || text.isBlank()) return;
        pose.pushPose();
        pose.translate(.5, .5, .5);

        // Ground model aperture centre is left of block centre.  First orient to the
        // signal face, then recline the lettering by the same 22.5 degrees as the
        // Blockbench signal body so the glyphs lie on the sloping theatre face.
        orientFace(facing, -0.3125, y - .5, depth, pose); // independent LT/RT depth
        if (isNorthSouth(facing)) {
            // MANUAL TUNING: edit NS_GROUND_* constants at the top of this file.
            pose.translate(NS_GROUND_X_CORRECTION, NS_GROUND_Y_CORRECTION, NS_GROUND_Z_CORRECTION);
        }
        pose.mulPose(Axis.XP.rotationDegrees(-22.5f));
        drawText(text, .018f, pose, buffers);
        pose.popPose();
    }

    private void orientFace(Direction facing, double lateral, double vertical, double depth, PoseStack pose) {
        switch (facing) {
            case SOUTH -> pose.translate(lateral, vertical, depth);
            case NORTH -> {
                pose.translate(-lateral, vertical, -depth);
                pose.mulPose(Axis.YP.rotationDegrees(180));
            }
            case WEST -> {
                pose.translate(depth, vertical, lateral);
                pose.mulPose(Axis.YP.rotationDegrees(-90));
            }
            case EAST -> {
                pose.translate(-depth, vertical, -lateral);
                pose.mulPose(Axis.YP.rotationDegrees(90));
            }
            default -> { }
        }
    }

    private static boolean isNorthSouth(Direction facing) {
        return facing == Direction.NORTH || facing == Direction.SOUTH;
    }

    private void drawText(String text, float scale, PoseStack pose, MultiBufferSource buffers) {
        pose.scale(scale, -scale, scale);
        font.drawInBatch(text, -font.width(text) / 2f, font.lineHeight / 2f,
                0xFFFFFFFF, false, pose.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, FULL);
    }
}