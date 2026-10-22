package com.benjamintaskis.createsignalextensions.network;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record OpenRouteConfigPayload(
        BlockPos pos,
        String signal,
        String aspect,
        String destination,
        String currentRouteKey,
        List<RouteEntry> routes,
        List<DestinationRule> destinationRules,
        int automaticRouteRange,
        boolean allowNewRoutes,
        boolean refreshResponse,
        long sessionId
) implements CustomPacketPayload {

    public record PathPoint(double x, double y, double z) {}

    public record RouteEntry(String key, String label, String theatreIndication,
                             List<String> smallTheatreIndications, String routeType, String routeIndication, List<PathPoint> visualPath) {
        public RouteEntry {
            visualPath = visualPath == null ? List.of() : List.copyOf(visualPath);
        }
    }

    public record DestinationRule(String pattern, String theatreIndication, List<String> smallTheatreIndications) {}

    public static final Type<OpenRouteConfigPayload> TYPE =
            new Type<>(CreateSignallingAdditions.id("open_route_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRouteConfigPayload> STREAM_CODEC =
            StreamCodec.ofMember(OpenRouteConfigPayload::encode, OpenRouteConfigPayload::new);

    public OpenRouteConfigPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readUtf(128), buf.readUtf(64), buf.readUtf(128),
                buf.readUtf(8192), readRoutes(buf), readDestinationRules(buf), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readLong());
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(signal, 128);
        buf.writeUtf(aspect, 64);
        buf.writeUtf(destination, 128);
        buf.writeUtf(currentRouteKey, 8192);
        buf.writeVarInt(routes.size());
        for (RouteEntry entry : routes) {
            buf.writeUtf(entry.key(), 8192);
            buf.writeUtf(entry.label(), 64);
            buf.writeUtf(entry.theatreIndication(), 8);
            List<String> cells = entry.smallTheatreIndications();
            for (int i = 0; i < 6; i++)
                buf.writeUtf(cells != null && i < cells.size() ? cells.get(i) : "", 4);
            buf.writeUtf(entry.routeType(), 32);
            buf.writeUtf(entry.routeIndication(), 16);
            List<PathPoint> path = entry.visualPath();
            int pathCount = Math.min(path == null ? 0 : path.size(), 256);
            buf.writeVarInt(pathCount);
            for (int i = 0; i < pathCount; i++) {
                PathPoint point = path.get(i);
                buf.writeDouble(point.x());
                buf.writeDouble(point.y());
                buf.writeDouble(point.z());
            }
        }
        buf.writeVarInt(destinationRules.size());
        for (DestinationRule rule : destinationRules) {
            buf.writeUtf(rule.pattern(), 128);
            buf.writeUtf(rule.theatreIndication(), 8);
            List<String> cells = rule.smallTheatreIndications();
            for (int i = 0; i < 6; i++)
                buf.writeUtf(cells != null && i < cells.size() ? cells.get(i) : "", 4);
        }
        buf.writeVarInt(automaticRouteRange);
        buf.writeBoolean(allowNewRoutes);
        buf.writeBoolean(refreshResponse);
        buf.writeLong(sessionId);
    }

    private static List<RouteEntry> readRoutes(RegistryFriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), 64);
        List<RouteEntry> routes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String key = buf.readUtf(8192);
            String label = buf.readUtf(64);
            String theatre = buf.readUtf(8);
            List<String> cells = new ArrayList<>(6);
            for (int cell = 0; cell < 6; cell++)
                cells.add(buf.readUtf(4));
            String routeType = buf.readUtf(32);
            String routeIndication = buf.readUtf(16);
            int pathCount = Math.min(buf.readVarInt(), 256);
            List<PathPoint> path = new ArrayList<>(pathCount);
            for (int point = 0; point < pathCount; point++)
                path.add(new PathPoint(buf.readDouble(), buf.readDouble(), buf.readDouble()));
            routes.add(new RouteEntry(key, label, theatre, cells, routeType, routeIndication, path));
        }
        return routes;
    }


    private static List<DestinationRule> readDestinationRules(RegistryFriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), 64);
        List<DestinationRule> rules = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String pattern = buf.readUtf(128);
            String theatre = buf.readUtf(8);
            List<String> cells = new ArrayList<>(6);
            for (int cell = 0; cell < 6; cell++)
                cells.add(buf.readUtf(4));
            rules.add(new DestinationRule(pattern, theatre, cells));
        }
        return rules;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
