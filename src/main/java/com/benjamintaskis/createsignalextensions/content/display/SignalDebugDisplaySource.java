package com.benjamintaskis.createsignalextensions.content.display;

import com.benjamintaskis.createsignalextensions.content.route.RouteInterfaceBlockEntity;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.content.trains.signal.SignalBlock;
import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

public class SignalDebugDisplaySource extends DisplaySource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        if (!(context.getSourceBlockEntity() instanceof RouteInterfaceBlockEntity routeInterface))
            return EMPTY;

        BlockPos pos = routeInterface.getLinkedSignalPos();
        if (pos == null)
            return List.of(Component.literal("Signal: UNLINKED"));

        SignalBlockEntity signal = routeInterface.getLinkedSignal();
        if (signal == null)
            return List.of(
                    Component.literal("Signal: MISSING"),
                    Component.literal(routeInterface.describeBlockAt(pos))
            );

        // getLinkedSignal() may have self-repaired the link, so refresh the position.
        pos = routeInterface.getLinkedSignalPos();

        String type = signal.getBlockState().getOptionalValue(SignalBlock.TYPE)
                .map(t -> t.getSerializedName())
                .orElse("unknown");

        return List.of(
                Component.literal("State: " + signal.getState().name()),
                Component.literal("Type: " + type),
                Component.literal("Boundary: " + (signal.getSignal() != null ? "OK" : "MISSING")),
                Component.literal("Pos: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
        );
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 5;
    }
}
