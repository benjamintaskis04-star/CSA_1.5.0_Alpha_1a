package com.benjamintaskis.createsignalextensions.content.theatre;

import com.benjamintaskis.createsignalextensions.content.route.RouteInterfaceBlockEntity;
import com.benjamintaskis.createsignalextensions.network.OpenSmallTheatreConfigPayload;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;

public class SmallTheatreBoxBlockEntity extends SmartBlockEntity {
    private BlockPos linkedRouteInterface;
    private List<String> indications = new ArrayList<>(List.of("","","","","",""));
    public SmallTheatreBoxBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);setLazyTickRate(4);}
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours){}
    @Override public void lazyTick(){super.lazyTick();if(level!=null&&!level.isClientSide)refreshIndications();}
    public void setLinkedRouteInterface(BlockPos pos){linkedRouteInterface=pos==null?null:pos.immutable();setChanged();refreshIndications();notifyUpdate();}
    public BlockPos getLinkedRouteInterfacePos(){return linkedRouteInterface;}
    public List<String> getIndications(){return List.copyOf(indications);}
    public void setRows(int rows){
        if(level==null)return; int value=Math.max(1,Math.min(3,rows));
        BlockState state=getBlockState(); if(state.hasProperty(SmallTheatreBoxBlock.ROWS)&&state.getValue(SmallTheatreBoxBlock.ROWS)!=value)
            level.setBlock(worldPosition,state.setValue(SmallTheatreBoxBlock.ROWS,value),3);
        setChanged();notifyUpdate();
    }
    public void openConfigurationScreen(ServerPlayer player){
        int rows=getBlockState().getValue(SmallTheatreBoxBlock.ROWS);
        String linked=linkedRouteInterface==null?"Unlinked":linkedRouteInterface.getX()+", "+linkedRouteInterface.getY()+", "+linkedRouteInterface.getZ();
        PacketDistributor.sendToPlayer(player,new OpenSmallTheatreConfigPayload(worldPosition,rows,linked));
    }
    private void refreshIndications(){
        List<String> next=List.of("","","","","","");
        if(level!=null&&linkedRouteInterface!=null&&level.hasChunkAt(linkedRouteInterface)){
            BlockEntity be=level.getBlockEntity(linkedRouteInterface);
            if(be instanceof RouteInterfaceBlockEntity ri)next=ri.getSelectedSmallTheatreIndications();
        }
        if(!indications.equals(next)){indications=new ArrayList<>(next);setChanged();notifyUpdate();}
    }
    @Override protected void write(CompoundTag tag,HolderLookup.Provider registries,boolean clientPacket){super.write(tag,registries,clientPacket);if(linkedRouteInterface!=null)tag.putLong("LinkedRouteInterface",linkedRouteInterface.asLong());for(int i=0;i<6;i++)tag.putString("Cell"+i,i<indications.size()?indications.get(i):"");}
    @Override protected void read(CompoundTag tag,HolderLookup.Provider registries,boolean clientPacket){super.read(tag,registries,clientPacket);linkedRouteInterface=tag.contains("LinkedRouteInterface")?BlockPos.of(tag.getLong("LinkedRouteInterface")):null;indications=new ArrayList<>(6);for(int i=0;i<6;i++)indications.add(tag.getString("Cell"+i));}
}
