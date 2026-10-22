package com.benjamintaskis.createsignalextensions.network;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RequestRouteConfigPayload(BlockPos pos, long sessionId) implements CustomPacketPayload {
    public static final Type<RequestRouteConfigPayload> TYPE =
            new Type<>(CreateSignallingAdditions.id("request_route_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestRouteConfigPayload> STREAM_CODEC =
            StreamCodec.ofMember(RequestRouteConfigPayload::encode, RequestRouteConfigPayload::new);

    public RequestRouteConfigPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readLong());
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeLong(sessionId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
