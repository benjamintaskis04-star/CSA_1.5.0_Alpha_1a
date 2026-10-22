package com.benjamintaskis.createsignalextensions.client;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.Locale;

/** Driver speed display. Dedicated servers supply the values by payload; an
 * integrated server can still use the local Create train as a fallback. */
public final class TrainSpeedHud {
    private static double serverSpeedKmh;
    private static double serverLimiterKmh;
    private static long serverStateExpiresAtMs;
    private TrainSpeedHud() {}

    public static void acceptServerState(double speedKmh, double limiterKmh) {
        serverSpeedKmh = speedKmh;
        serverLimiterKmh = limiterKmh;
        serverStateExpiresAtMs = System.currentTimeMillis() + 1000L;
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui)
            return;

        double speedKmh;
        double limiterKmh;
        if (System.currentTimeMillis() <= serverStateExpiresAtMs) {
            speedKmh = serverSpeedKmh;
            limiterKmh = serverLimiterKmh;
        } else {
            Train train = findControlledTrain(minecraft);
            if (train == null)
                return;
            speedKmh = Math.abs(train.speed) * 72.0;
            double manualTopSpeed = train.maxSpeed()
                    * AllConfigs.server().trains.manualTrainSpeedModifier.getF();
            limiterKmh = Math.max(0.0, manualTopSpeed * train.throttle * 72.0);
        }

        String text = String.format(Locale.ROOT, "%.0f km/h  |  LIMIT %.0f km/h", speedKmh, limiterKmh);
        var graphics = event.getGuiGraphics();
        int width = minecraft.font.width(text);
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - 105;
        graphics.drawString(minecraft.font, Component.literal(text), x, y, 0xFFFFFF, true);
    }

    private static Train findControlledTrain(Minecraft minecraft) {
        for (Train train : Create.RAILWAYS.trains.values()) {
            if (train == null || train.carriages == null)
                continue;
            for (Carriage carriage : train.carriages) {
                var entity = carriage.anyAvailableEntity();
                if (entity != null && entity.getControllingPlayer()
                        .map(uuid -> uuid.equals(minecraft.player.getUUID()))
                        .orElse(false))
                    return train;
            }
        }
        return null;
    }
}
