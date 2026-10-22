package com.benjamintaskis.createsignalextensions.client;
import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import com.benjamintaskis.createsignalextensions.network.ToggleManualShuntPayload;
import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ManualShuntKeybind {
    public static final KeyMapping TOGGLE = new KeyMapping("key.create_signal_extensions.toggle_shunt", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.create_signal_extensions");
    @EventBusSubscriber(modid=CreateSignallingAdditions.ID, bus=EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
    public static final class Registration { @SubscribeEvent public static void register(RegisterKeyMappingsEvent e){ e.register(TOGGLE); } }
    public static void clientTick(ClientTickEvent.Post e){
        Minecraft mc=Minecraft.getInstance(); if(mc.player==null) return;
        while(TOGGLE.consumeClick()) { Train t=findControlledTrain(mc); if(t!=null && t.id!=null) PacketDistributor.sendToServer(new ToggleManualShuntPayload(t.id)); }
    }
    private static Train findControlledTrain(Minecraft mc){
        for(Train t: Create.RAILWAYS.trains.values()) if(t!=null && t.carriages!=null) for(Carriage c:t.carriages){ var e=c.anyAvailableEntity(); if(e!=null && e.getControllingPlayer().map(id->id.equals(mc.player.getUUID())).orElse(false)) return t; }
        return null;
    }
}
