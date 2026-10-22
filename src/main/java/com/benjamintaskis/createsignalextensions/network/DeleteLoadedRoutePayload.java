package com.benjamintaskis.createsignalextensions.network;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DeleteLoadedRoutePayload(BlockPos pos, long sessionId, String routeKey) implements CustomPacketPayload {
    public static final Type<DeleteLoadedRoutePayload> TYPE =
            new Type<>(CreateSignallingAdditions.id("delete_loaded_route"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteLoadedRoutePayload> STREAM_CODEC =
            StreamCodec.ofMember(DeleteLoadedRoutePayload::encode, DeleteLoadedRoutePayload::new);

    public DeleteLoadedRoutePayload(RegistryFriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readLong(), buf.readUtf(8192));
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeLong(sessionId);
        buf.writeUtf(routeKey, 8192);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
