package com.benjamintaskis.createsignalextensions.client;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = CreateSignallingAdditions.ID, dist = Dist.CLIENT)
public final class CreateSignallingAdditionsClient {
    public CreateSignallingAdditionsClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(TrainSpeedHud::onRenderGui);
        NeoForge.EVENT_BUS.addListener(ManualShuntKeybind::clientTick);
    }
}
