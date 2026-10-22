package com.benjamintaskis.createsignalextensions.client;

import com.benjamintaskis.createsignalextensions.network.OpenRouteConfigPayload;
import com.benjamintaskis.createsignalextensions.network.OpenSmallTheatreConfigPayload;
import com.benjamintaskis.createsignalextensions.network.OpenConvergingSignalConfigPayload;
import com.benjamintaskis.createsignalextensions.network.OpenSpeedBoardConfigPayload;
import com.benjamintaskis.createsignalextensions.network.TrainSpeedHudPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-only payload handling. Keeping all Minecraft client classes here prevents
 * dedicated servers from attempting to load GUI classes during common networking setup.
 */
public final class ClientPayloadHandlers {
    private static final Map<BlockPos, Long> CLOSED_ROUTE_SESSIONS = new HashMap<>();

    private ClientPayloadHandlers() {}

    public static void markRouteInterfaceClosed(BlockPos pos, long sessionId) {
        CLOSED_ROUTE_SESSIONS.put(pos.immutable(), sessionId);
    }

    public static void handleOpenRouteConfig(OpenRouteConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof RouteInterfaceScreen screen && screen.isFor(payload.pos())) {
                screen.applyUpdate(payload);
            } else if (minecraft.screen instanceof SmallTheatreMappingScreen screen && screen.isFor(payload.pos())) {
                screen.applyUpdate(payload);
            } else if (minecraft.screen instanceof DestinationRuleSmallTheatreMappingScreen screen && screen.isFor(payload.pos())) {
                screen.applyUpdate(payload);
            } else if (minecraft.screen instanceof RoutePathMapScreen screen && screen.isFor(payload.pos())) {
                screen.applyUpdate(payload);
            } else if (!payload.refreshResponse()) {
                // A delayed/duplicate initial-open packet from the same right-click must not
                // resurrect a Route Interface screen the player has already closed. A new
                // right-click receives a new session id and is therefore allowed to open.
                Long closedSession = CLOSED_ROUTE_SESSIONS.get(payload.pos());
                if (closedSession == null || closedSession.longValue() != payload.sessionId()) {
                    CLOSED_ROUTE_SESSIONS.remove(payload.pos());
                    minecraft.setScreen(new RouteInterfaceScreen(payload));
                }
            }
        });
    }

    public static void handleOpenSmallTheatreConfig(OpenSmallTheatreConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new SmallTheatreConfigScreen(payload)));
    }

    public static void handleOpenConvergingSignalConfig(OpenConvergingSignalConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new ConvergingSignalConfigScreen(payload)));
    }

    public static void handleOpenSpeedBoardConfig(OpenSpeedBoardConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new SpeedBoardConfigScreen(payload)));
    }
    public static void handleTrainSpeedHud(TrainSpeedHudPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TrainSpeedHud.acceptServerState(payload.speedKmh(), payload.limiterKmh()));
    }
}
