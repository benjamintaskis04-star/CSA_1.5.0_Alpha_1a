package com.benjamintaskis.createsignalextensions.content.signal;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos; import net.minecraft.core.HolderLookup; import net.minecraft.nbt.CompoundTag; import net.minecraft.world.level.block.entity.BlockEntityType; import net.minecraft.world.level.block.state.BlockState; import java.util.List;
public class SignalPlateBlockEntity extends SmartBlockEntity {
 private String text="BN 511 DM";
 public SignalPlateBlockEntity(BlockEntityType<?> t, BlockPos p, BlockState s){super(t,p,s);}
 public String text(){return text;} public void setText(String v){text=sanitize(v);setChanged();sendData();}
 private static String sanitize(String v){if(v==null)return ""; String s=v.replaceAll("[^A-Za-z0-9 /\\.-]","").toUpperCase(); return s.length()>16?s.substring(0,16):s;}
 @Override protected void write(CompoundTag tag, HolderLookup.Provider r, boolean client){super.write(tag,r,client);tag.putString("PlateText",text);}
 @Override protected void read(CompoundTag tag, HolderLookup.Provider r, boolean client){super.read(tag,r,client);if(tag.contains("PlateText"))text=tag.getString("PlateText");}
 @Override public void addBehaviours(List<BlockEntityBehaviour> b){}
}
