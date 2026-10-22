package com.benjamintaskis.createsignalextensions.network;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SetConvergingSignalDirectionPayload(BlockPos pos, String direction) implements CustomPacketPayload {
    public static final Type<SetConvergingSignalDirectionPayload> TYPE =
            new Type<>(CreateSignallingAdditions.id("set_converging_signal_direction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetConvergingSignalDirectionPayload> STREAM_CODEC =
            StreamCodec.ofMember(SetConvergingSignalDirectionPayload::encode, SetConvergingSignalDirectionPayload::new);

    public SetConvergingSignalDirectionPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBlockPos(), buffer.readUtf(16));
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeUtf(direction, 16);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
