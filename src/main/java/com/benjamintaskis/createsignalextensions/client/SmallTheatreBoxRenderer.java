package com.benjamintaskis.createsignalextensions.client;

import com.benjamintaskis.createsignalextensions.content.HeightVariable;
import com.benjamintaskis.createsignalextensions.content.theatre.SmallTheatreBoxBlock;
import com.benjamintaskis.createsignalextensions.content.theatre.SmallTheatreBoxBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import java.util.List;

public class SmallTheatreBoxRenderer implements BlockEntityRenderer<SmallTheatreBoxBlockEntity> {
    private static final int FULL_BRIGHT=0x00F000F0; private final Font font;
    public SmallTheatreBoxRenderer(BlockEntityRendererProvider.Context c){font=c.getFont();}
    @Override public void render(SmallTheatreBoxBlockEntity be,float pt,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        List<String> values=be.getIndications(); int rows=be.getBlockState().getValue(SmallTheatreBoxBlock.ROWS); Direction facing=be.getBlockState().getValue(SmallTheatreBoxBlock.FACING);
        for(int row=0;row<rows;row++)for(int col=0;col<2;col++){int index=row*2+col;if(index>=values.size())continue;String text=values.get(index);if(text==null||text.isBlank())continue;renderCell(be,text,row,col,facing,pose,buffers);}
    }
    private void renderCell(SmallTheatreBoxBlockEntity be,String text,int row,int col,Direction facing,PoseStack pose,MultiBufferSource buffers){
        double y=0.5625-row*0.375; double lateral=col==0?0.25:-0.25;
        pose.pushPose();pose.translate(0.0,HeightVariable.blocks(be.getBlockState()),0.0);pose.translate(0.5,0.5,0.5);
        switch(facing){
            case NORTH->{pose.translate(lateral,y,0.124);pose.mulPose(Axis.YP.rotationDegrees(180));}
            case SOUTH->{pose.translate(-lateral,y,-0.124);}
            case EAST->{pose.translate(-0.124,y,lateral);pose.mulPose(Axis.YP.rotationDegrees(90));}
            case WEST->{pose.translate(0.124,y,-lateral);pose.mulPose(Axis.YP.rotationDegrees(-90));}
            default->{pose.translate(lateral,y,0.124);pose.mulPose(Axis.YP.rotationDegrees(180));}
        }
        float scale=0.028f;pose.scale(scale,-scale,scale);float x=-font.width(text)/2f,yText=font.lineHeight/2f;
        font.drawInBatch(text,x,yText,0xFFFFFFFF,false,pose.last().pose(),buffers,Font.DisplayMode.POLYGON_OFFSET,0,FULL_BRIGHT);pose.popPose();
    }
}
