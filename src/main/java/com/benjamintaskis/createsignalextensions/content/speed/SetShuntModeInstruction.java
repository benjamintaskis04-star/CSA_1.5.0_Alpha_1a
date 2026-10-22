package com.benjamintaskis.createsignalextensions.content.speed;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import net.createmod.catnip.data.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import java.util.List;

/** Instantaneous Start Shunt / End Shunt schedule instruction. */
public class SetShuntModeInstruction extends ScheduleInstruction {
    public static final String MODE="Mode";
    public SetShuntModeInstruction(){data.putInt(MODE,0);}
    public boolean isShunting(){return intData(MODE)==0;}
    @Override public ResourceLocation getId(){return CreateSignallingAdditions.id("set_shunt_mode");}
    @Override public boolean supportsConditions(){return false;}
    private String name(){return isShunting()?"Start Shunt":"End Shunt";}
    @Override public Pair<ItemStack,Component> getSummary(){return Pair.of(icon(),Component.literal(name()));}
    @Override public ItemStack getSecondLineIcon(){return icon();}
    @Override public List<Component> getTitleAs(String type){return List.of(Component.literal(name()));}
    @Override public void initConfigurationWidgets(com.simibubi.create.foundation.gui.ModularGuiLineBuilder b){
        b.addSelectionScrollInput(0,95,(input,label)->input.forOptions(List.of(Component.literal("Start Shunt"),Component.literal("End Shunt"))).titled(Component.literal("Movement Mode")),MODE);
    }
    @Override public DiscoveredPath start(ScheduleRuntime runtime,Level level){if(isShunting()) TrainMovementModeManager.requestStartShunt(level,runtime.train); else TrainMovementModeManager.endShunt(level,runtime.train);runtime.state=ScheduleRuntime.State.PRE_TRANSIT;runtime.currentEntry++;return null;}
    private ItemStack icon(){return new ItemStack(Items.MINECART);}
}
