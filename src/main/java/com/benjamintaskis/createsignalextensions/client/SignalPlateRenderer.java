package com.benjamintaskis.createsignalextensions.client;

import com.benjamintaskis.createsignalextensions.content.HeightVariable;
import com.benjamintaskis.createsignalextensions.content.signal.SignalPlateBlock;
import com.benjamintaskis.createsignalextensions.content.signal.SignalPlateBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Alpha 7a3: renders the complete placed plate assembly (plate + standard bracket)
 * through the block-entity renderer. This deliberately avoids chunk-model renderers
 * (including Sodium's), so the same vertical translation moves the static model and
 * the dynamic text as one assembly.
 */
public class SignalPlateRenderer implements BlockEntityRenderer<SignalPlateBlockEntity> {
    private final Font font;
    public SignalPlateRenderer(BlockEntityRendererProvider.Context c){ font=c.getFont(); }

    @Override public void render(SignalPlateBlockEntity be,float pt,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var state=be.getBlockState();
        Direction f=state.getValue(SignalPlateBlock.FACING);
        SignalPlateBlock.PlateMode mode=state.getValue(SignalPlateBlock.MODE);
        double yOffset=HeightVariable.blocks(state);

        // Render the complete placed assembly with the same shared vertical translation.
        HeightVariableModelRenderer.render(state, pose, buffers, light, overlay);

        // Keep the established Alpha 7-4 text transform exactly relative to the plate.
        pose.pushPose();
        pose.translate(.5,.25+yOffset,.5);
        switch(f){
            case NORTH->{pose.translate(0,0,.422);pose.mulPose(Axis.YP.rotationDegrees(180));}
            case SOUTH->pose.translate(0,0,-.422);
            case EAST->{pose.translate(-.422,0,0);pose.mulPose(Axis.YP.rotationDegrees(90));}
            case WEST->{pose.translate(.422,0,0);pose.mulPose(Axis.YP.rotationDegrees(-90));}
            default->{}
        }
        float scale=.014f; pose.scale(scale,-scale,scale);
        if(mode==SignalPlateBlock.PlateMode.NORMAL) draw(be.text(),font.lineHeight/2f,pose,buffers);
        else {
            draw(mode==SignalPlateBlock.PlateMode.CO_ACT?"CO-ACT":mode==SignalPlateBlock.PlateMode.REPEATER?"REPTR":"DIST",-1.0f,pose,buffers);
            draw(be.text(),font.lineHeight-1.0f,pose,buffers);
        }
        pose.popPose();
    }
    private void draw(String text,float y,PoseStack pose,MultiBufferSource buffers){
        font.drawInBatch(text,-font.width(text)/2f,y,0xFFFFFFFF,false,pose.last().pose(),buffers,Font.DisplayMode.POLYGON_OFFSET,0,0x00F000F0);
    }
}
