package com.benjamintaskis.createsignalextensions.network;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SetAutomaticRouteRangePayload(BlockPos pos, long sessionId, int range) implements CustomPacketPayload {
    public static final Type<SetAutomaticRouteRangePayload> TYPE =
            new Type<>(CreateSignallingAdditions.id("set_automatic_route_range"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetAutomaticRouteRangePayload> STREAM_CODEC =
            StreamCodec.ofMember(SetAutomaticRouteRangePayload::encode, SetAutomaticRouteRangePayload::new);

    public SetAutomaticRouteRangePayload(RegistryFriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readLong(), buf.readVarInt());
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeLong(sessionId);
        buf.writeVarInt(range);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
