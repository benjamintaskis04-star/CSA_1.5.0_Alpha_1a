package com.benjamintaskis.createsignalextensions.content.display;

import com.benjamintaskis.createsignalextensions.content.route.RouteInterfaceBlockEntity;
import com.benjamintaskis.createsignalextensions.content.route.RouteProbe;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

public class SelectedRouteDebugDisplaySource extends DisplaySource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        if (!(context.getSourceBlockEntity() instanceof RouteInterfaceBlockEntity routeInterface))
            return EMPTY;

        RouteProbe.Result result = routeInterface.getRouteProbeResult();
        if (!result.resolved())
            return List.of(Component.literal("Route: " + result.summary()));

        return List.of(
                Component.literal("Route: " + result.routeKey()),
                Component.literal("Train: " + result.trainName())
        );
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 5;
    }
}
