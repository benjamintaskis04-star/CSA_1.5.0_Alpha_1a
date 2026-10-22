package com.benjamintaskis.createsignalextensions.network;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClearLoadedRoutesPayload(BlockPos pos, long sessionId) implements CustomPacketPayload {
    public static final Type<ClearLoadedRoutesPayload> TYPE =
            new Type<>(CreateSignallingAdditions.id("clear_loaded_routes"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClearLoadedRoutesPayload> STREAM_CODEC =
            StreamCodec.ofMember(ClearLoadedRoutesPayload::encode, ClearLoadedRoutesPayload::new);

    public ClearLoadedRoutesPayload(RegistryFriendlyByteBuf buf) {
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
