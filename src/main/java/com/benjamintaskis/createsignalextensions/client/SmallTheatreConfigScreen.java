package com.benjamintaskis.createsignalextensions.client;
import com.benjamintaskis.createsignalextensions.network.OpenSmallTheatreConfigPayload;
import com.benjamintaskis.createsignalextensions.network.SetSmallTheatreRowsPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
public class SmallTheatreConfigScreen extends Screen {
 private final OpenSmallTheatreConfigPayload payload; private int rows,left,top;
 public SmallTheatreConfigScreen(OpenSmallTheatreConfigPayload p){super(Component.literal("Small Theatre Box"));payload=p;rows=p.rows();}
 @Override protected void init(){left=(width-260)/2;top=Math.max(10,(height-150)/2);}
 @Override public boolean mouseClicked(double x,double y,int b){if(b==0){for(int r=1;r<=3;r++){int bx=left+35+(r-1)*65;if(x>=bx&&x<bx+55&&y>=top+68&&y<top+92){rows=r;PacketDistributor.sendToServer(new SetSmallTheatreRowsPayload(payload.pos(),r));return true;}}if(x>=left+180&&x<left+235&&y>=top+112&&y<top+132){onClose();return true;}}return super.mouseClicked(x,y,b);}
 @Override public void render(GuiGraphics g,int mx,int my,float pt){g.fill(0,0,width,height,0x78000000);g.fill(left,top,left+260,top+145,0xF0181818);g.fill(left+1,top+1,left+259,top+28,0xFF292925);g.drawCenteredString(font,title,width/2,top+10,0xFFF3F0E8);g.drawString(font,"Linked RI: "+payload.linked(),left+20,top+40,0xFFC8C8C8);g.drawString(font,"Array height",left+20,top+56,0xFFAAA58F);for(int r=1;r<=3;r++){int bx=left+35+(r-1)*65;boolean h=mx>=bx&&mx<bx+55&&my>=top+68&&my<top+92;g.fill(bx,top+68,bx+55,top+92,rows==r?0xFF666158:h?0xFF504D47:0xFF383632);g.drawCenteredString(font,"2x"+r,bx+27,top+76,0xFFF0EEE8);}g.fill(left+180,top+112,left+235,top+132,0xFF3B3934);g.drawCenteredString(font,"DONE",left+207,top+118,0xFFF0EEE8);}
 @Override public boolean isPauseScreen(){return false;}
}
