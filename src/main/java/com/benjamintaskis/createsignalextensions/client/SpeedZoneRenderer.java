package com.benjamintaskis.createsignalextensions.client;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import com.benjamintaskis.createsignalextensions.config.ClientConfig;
import com.benjamintaskis.createsignalextensions.content.speed.SpeedZoneBoardData.Board;
import com.benjamintaskis.createsignalextensions.network.SpeedZoneBoardsPayload;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.trains.graph.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import java.util.*;

/** Depth-tested, client-only visualisation; never supplies operational authority. */
@EventBusSubscriber(modid = CreateSignallingAdditions.ID, value = Dist.CLIENT)
public final class SpeedZoneRenderer {
    private SpeedZoneRenderer() {}
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("create", "textures/block/track_assembly_overlay.png");
    private static final RenderType TYPE = RenderType.entityTranslucent(TEXTURE);
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new ByteBufferBuilder(65536));
    private static List<Board> boards = List.of();
    private static ResourceLocation dimension;
    private static ClientLevel cachedLevel;
    private static long lastBuild = Long.MIN_VALUE;
    private static int cachedRange;
    private static Vec3 cachedCamera = Vec3.ZERO;
    private static final List<Zone> zones = new ArrayList<>();
    private record EdgeKey(TrackGraph graph, TrackNode from, TrackNode to) {}
    private record Mapped(Board board, EdgeKey edge) {}
    private record Piece(SpeedZoneTrackMesh.Quad quad, float v0, float v1) {}
    private record Zone(int speed, List<Piece> pieces) {}

    public static void accept(SpeedZoneBoardsPayload payload) {
        dimension = payload.dimension(); boards = payload.boards(); lastBuild = Long.MIN_VALUE;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        boards = List.of(); zones.clear(); dimension = null; cachedLevel = null;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;
        if (!mc.level.dimension().location().equals(dimension)) { zones.clear(); return; }
        Vec3 camera = event.getCamera().getPosition();
        int range = ClientConfig.speedZoneRenderDistance();
        if (cachedLevel != mc.level || lastBuild == Long.MIN_VALUE || mc.level.getGameTime() - lastBuild >= 20
                || cachedCamera.distanceToSqr(camera) > 256 || cachedRange != range) {
            rebuild(mc.level, camera, range);
            cachedLevel = mc.level; lastBuild = mc.level.getGameTime(); cachedCamera = camera; cachedRange = range;
        }
        // Crosshair inspection selects the nearest visible track zone independently
        // of the global overlay toggle, without selecting through solid terrain.
        Vec3 look = Vec3.directionFromRotation(event.getCamera().getXRot(), event.getCamera().getYRot());
        double reach = Math.min(range, mc.hitResult == null ? range : mc.hitResult.getLocation().distanceTo(camera) + .5);
        Zone selected = null;
        double best = reach;
        for (Zone zone : zones) for (Piece piece : zone.pieces()) {
            var q = piece.quad();
            Vec3 a = q.leftStart().add(q.rightStart()).scale(.5);
            Vec3 b = q.leftEnd().add(q.rightEnd()).scale(.5);
            Vec3 mid = a.add(b).scale(.5);
            double along = mid.subtract(camera).dot(look);
            if (along < 0 || along > best) continue;
            if (mid.distanceToSqr(camera.add(look.scale(along))) < .65) { selected = zone; best = along; }
        }
        PoseStack pose = event.getPoseStack();
        pose.pushPose(); pose.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer consumer = BUFFERS.getBuffer(TYPE);
        for (Zone zone : zones) {
            if (!ClientConfig.showAllSpeedZones() && zone != selected) continue;
            int rgb = SpeedZoneColour.rgb(zone.speed());
            int alpha = Math.round(ClientConfig.speedZoneOpacity() * 255);
            for (Piece piece : zone.pieces()) {
                var q = piece.quad();
                Vec3 mid = q.leftStart().add(q.rightEnd()).scale(.5);
                if (mid.distanceToSqr(camera) > range * range || !mc.level.hasChunkAt(BlockPos.containing(mid))) continue;
                vertex(consumer, pose, q.leftStart(), 0, piece.v0(), rgb, alpha);
                vertex(consumer, pose, q.rightStart(), 1, piece.v0(), rgb, alpha);
                vertex(consumer, pose, q.rightEnd(), 1, piece.v1(), rgb, alpha);
                vertex(consumer, pose, q.leftEnd(), 0, piece.v1(), rgb, alpha);
            }
        }
        BUFFERS.endBatch(TYPE); pose.popPose();
    }
    private static void vertex(VertexConsumer c, PoseStack pose, Vec3 p, float u, float v, int rgb, int alpha) {
        c.addVertex(pose.last(), (float)p.x, (float)p.y, (float)p.z)
                .setColor(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 1, 0);
    }

    private static void rebuild(ClientLevel level, Vec3 camera, int range) {
        zones.clear();
        Map<EdgeKey, List<Board>> byEdge = new HashMap<>();
        List<Mapped> starts = new ArrayList<>();
        for (Board board : boards) {
            TrackNodeLocation a = new TrackNodeLocation(board.from()).in(level);
            TrackNodeLocation b = new TrackNodeLocation(board.to()).in(level);
            for (TrackGraph graph : CreateClient.RAILWAYS.trackNetworks.values()) {
                TrackNode from = graph.locateNode(a), to = graph.locateNode(b);
                if (from == null || to == null || graph.getConnectionsFrom(from).get(to) == null) continue;
                EdgeKey key = new EdgeKey(graph, from, to);
                byEdge.computeIfAbsent(key, $ -> new ArrayList<>()).add(board);
                break;
            }
        }
        // Coincident boards must not paint contradictory zones over each other.
        // A positive restriction wins over unrestricted; otherwise take the lower.
        for (var entry : byEdge.entrySet()) {
            var ordered = entry.getValue();
            ordered.sort(Comparator.comparingDouble(Board::fraction));
            List<Board> distinct = new ArrayList<>();
            for (Board board : ordered) {
                if (!distinct.isEmpty() && Math.abs(distinct.getLast().fraction() - board.fraction()) < 1.0e-6) {
                    Board previous = distinct.getLast();
                    if (previous.speed() == 0 || board.speed() > 0 && board.speed() < previous.speed())
                        distinct.set(distinct.size() - 1, board);
                } else distinct.add(board);
            }
            entry.setValue(distinct);
            for (Board board : distinct) starts.add(new Mapped(board, entry.getKey()));
        }
        for (Mapped start : starts) {
            if (start.board().speed() == 0) continue; // Unrestricted boards terminate preceding zones.
            List<Piece> pieces = new ArrayList<>();
            EdgeKey key = start.edge();
            double fraction = start.board().fraction(), distance = 0;
            Set<EdgeKey> visited = new HashSet<>();
            for (int count = 0; count < 256 && distance < 2048 && visited.add(key); count++) {
                TrackEdge edge = key.graph().getConnectionsFrom(key.from()).get(key.to());
                if (edge == null || !key.from().getLocation().dimension.equals(level.dimension())
                        || !key.to().getLocation().dimension.equals(level.dimension()) || edge.getLength() <= 1.0e-6) break;
                double end = 1;
                boolean boundary = false;
                for (Board next : byEdge.getOrDefault(key, List.of())) {
                    if (next.fraction() > fraction + 1.0e-6 && next.fraction() <= end) {
                        end = next.fraction(); boundary = true;
                    }
                }
                double length = Math.min((end - fraction) * edge.getLength(), 2048 - distance);
                double endFraction = fraction + length / edge.getLength();
                int steps = Math.max(1, (int)Math.ceil(length * 4));
                for (int i = 0; i < steps; i++) {
                    double f0 = fraction + (endFraction - fraction) * i / steps;
                    double f1 = fraction + (endFraction - fraction) * (i + 1) / steps;
                    Vec3 p0 = edge.getPosition(key.graph(), f0), p1 = edge.getPosition(key.graph(), f1);
                    if (p0.distanceToSqr(camera) > (range + 3.0) * (range + 3.0)) continue;
                    if (!level.hasChunkAt(BlockPos.containing(p0)) || !level.hasChunkAt(BlockPos.containing(p1))) continue;
                    // Repeat Create's existing transparent assembly sleeper mask.
                    double offset = distance + length * i / steps;
                    float v0 = (float)(offset - Math.floor(offset));
                    float v1 = v0 + (float)(length / steps);
                    for (var q : SpeedZoneTrackMesh.build(List.of(p0, p1), 2.75, .035))
                        pieces.add(new Piece(q, v0, v1));
                }
                distance += length;
                if (boundary || endFraction < 1 - 1.0e-6) break;
                TrackNode nextNode = null;
                int exits = 0;
                for (var exit : key.graph().getConnectionsFrom(key.to()).entrySet()) {
                    if (exit.getKey() == key.from() || !edge.canTravelTo(exit.getValue())) continue;
                    nextNode = exit.getKey(); exits++;
                }
                if (exits != 1) break;
                key = new EdgeKey(key.graph(), key.to(), nextNode); fraction = 0;
                // A board exactly at the next edge's start also ends this zone.
                if (byEdge.getOrDefault(key, List.of()).stream().anyMatch(bd -> bd.fraction() < 1.0e-6)) break;
            }
            if (!pieces.isEmpty()) zones.add(new Zone(start.board().speed(), List.copyOf(pieces)));
        }
    }
}
