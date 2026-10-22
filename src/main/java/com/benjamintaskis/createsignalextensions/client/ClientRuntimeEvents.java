package com.benjamintaskis.createsignalextensions.client;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import com.benjamintaskis.createsignalextensions.ModTags;
import com.benjamintaskis.createsignalextensions.content.signal.SignalPlateBlock;
import com.benjamintaskis.createsignalextensions.network.AdjustSignalVerticalPayload;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = CreateSignallingAdditions.ID, value = Dist.CLIENT)
public final class ClientRuntimeEvents {
    private ClientRuntimeEvents() {}

    @SubscribeEvent
    public static void click(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().isEmpty()) return;

        var state = event.getLevel().getBlockState(event.getPos());
        if (ClientEvents.VERTICAL_ADJUST.isDown() && state.is(ModTags.HEIGHTVARIABLE_BLOCKS)) {
            PacketDistributor.sendToServer(new AdjustSignalVerticalPayload(
                    event.getPos(), event.getEntity().isShiftKeyDown() ? -1 : 1));
            event.setCanceled(true);
            return;
        }

        if (state.getBlock() instanceof SignalPlateBlock) {
            SignalPlateDirectEditor.begin(event.getPos());
            event.setCanceled(true);
        }
    }
}
