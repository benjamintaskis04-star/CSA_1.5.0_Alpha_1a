package com.benjamintaskis.createsignalextensions.content.theatre;

import com.benjamintaskis.createsignalextensions.content.route.RouteInterfaceBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos; import net.minecraft.core.HolderLookup; import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity; import net.minecraft.world.level.block.entity.BlockEntityType; import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Integral theatre data for Alpha 5a shunts. Cells mirror Route Interface small-theatre top-left/top-right. */
public class ShuntTheatreBlockEntity extends SmartBlockEntity {
 private BlockPos linkedRouteInterface; private List<String> indications=new ArrayList<>(List.of("",""));
 public ShuntTheatreBlockEntity(BlockEntityType<?> t,BlockPos p,BlockState s){super(t,p,s);setLazyTickRate(4);}
 @Override public void addBehaviours(List<BlockEntityBehaviour> b){}
 @Override public void lazyTick(){super.lazyTick();if(level!=null&&!level.isClientSide)refresh();}
 public void setLinkedRouteInterface(BlockPos p){linkedRouteInterface=p==null?null:p.immutable();setChanged();refresh();notifyUpdate();}
 public BlockPos getLinkedRouteInterfacePos(){return linkedRouteInterface;}
 public List<String> getIndications(){return List.copyOf(indications);}
 private void refresh(){List<String> n=List.of("",""); if(level!=null&&linkedRouteInterface!=null&&level.hasChunkAt(linkedRouteInterface)){BlockEntity be=level.getBlockEntity(linkedRouteInterface);if(be instanceof RouteInterfaceBlockEntity ri){var all=ri.getSelectedSmallTheatreIndications();n=List.of(all.size()>0?all.get(0):"",all.size()>1?all.get(1):"");}} if(!indications.equals(n)){indications=new ArrayList<>(n);setChanged();notifyUpdate();}}
 @Override protected void write(CompoundTag t,HolderLookup.Provider r,boolean c){super.write(t,r,c);if(linkedRouteInterface!=null)t.putLong("LinkedRouteInterface",linkedRouteInterface.asLong());t.putString("Cell0",indications.get(0));t.putString("Cell1",indications.get(1));}
 @Override protected void read(CompoundTag t,HolderLookup.Provider r,boolean c){super.read(t,r,c);linkedRouteInterface=t.contains("LinkedRouteInterface")?BlockPos.of(t.getLong("LinkedRouteInterface")):null;indications=new ArrayList<>(List.of(t.getString("Cell0"),t.getString("Cell1")));}
}
