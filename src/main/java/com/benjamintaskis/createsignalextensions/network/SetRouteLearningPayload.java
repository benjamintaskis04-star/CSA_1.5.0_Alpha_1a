package com.benjamintaskis.createsignalextensions.network;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SetRouteLearningPayload(BlockPos pos, long sessionId, boolean allowNewRoutes) implements CustomPacketPayload {
    public static final Type<SetRouteLearningPayload> TYPE = new Type<>(CreateSignallingAdditions.id("set_route_learning"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetRouteLearningPayload> STREAM_CODEC = StreamCodec.ofMember(SetRouteLearningPayload::encode, SetRouteLearningPayload::new);
    public SetRouteLearningPayload(RegistryFriendlyByteBuf buf) { this(buf.readBlockPos(), buf.readLong(), buf.readBoolean()); }
    private void encode(RegistryFriendlyByteBuf buf) { buf.writeBlockPos(pos); buf.writeLong(sessionId); buf.writeBoolean(allowNewRoutes); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
