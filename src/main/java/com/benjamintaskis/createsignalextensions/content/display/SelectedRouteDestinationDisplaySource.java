package com.benjamintaskis.createsignalextensions.content.display;

import com.benjamintaskis.createsignalextensions.content.route.RouteInterfaceBlockEntity;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

public class SelectedRouteDestinationDisplaySource extends DisplaySource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        if (!(context.getSourceBlockEntity() instanceof RouteInterfaceBlockEntity routeInterface))
            return EMPTY;

        String destination = routeInterface.getCachedDestination();
        return List.of(Component.literal(destination == null || destination.isBlank() ? "NO DESTINATION" : destination));
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 5;
    }
}
