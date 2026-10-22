package com.benjamintaskis.createsignalextensions.client;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import com.benjamintaskis.createsignalextensions.ModBlockEntityTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.client.KeyMapping; import com.mojang.blaze3d.platform.InputConstants; import org.lwjgl.glfw.GLFW; import net.minecraft.world.InteractionHand; import com.benjamintaskis.createsignalextensions.content.signal.SignalPlateBlock;

@EventBusSubscriber(modid = CreateSignallingAdditions.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {
    public static final KeyMapping VERTICAL_ADJUST = new KeyMapping("key.create_signal_extensions.vertical_adjust", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.create_signal_extensions");
    @SubscribeEvent public static void registerKeys(RegisterKeyMappingsEvent e){e.register(VERTICAL_ADJUST);}
    @SubscribeEvent
    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        // Alpha 7a4-1: preserve the normal baked/blockstate rendering pipeline and
        // translate only the finished CSA quads. This keeps orientation, model
        // selection, textures and proportions identical to the ordinary model.
        // Signal Plate is explicitly ignored by HeightVariableBakedModel because
        // its dedicated renderer already handles the complete moving assembly.
        event.getModels().replaceAll((location, model) ->
                CreateSignallingAdditions.ID.equals(location.id().getNamespace())
                        ? new HeightVariableBakedModel(model)
                        : model);
    }
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntityTypes.LARGE_THEATRE_BOX.get(), LargeTheatreBoxRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.SMALL_THEATRE_BOX.get(), SmallTheatreBoxRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.SPEED_BOARD.get(), SpeedBoardRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.SHUNT_THEATRE.get(), ShuntTheatreRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.SIGNAL_PLATE.get(), SignalPlateRenderer::new);
    }
}
