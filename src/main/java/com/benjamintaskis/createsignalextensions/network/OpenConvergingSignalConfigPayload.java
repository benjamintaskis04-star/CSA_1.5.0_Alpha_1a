package com.benjamintaskis.createsignalextensions.network;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenConvergingSignalConfigPayload(BlockPos pos, String direction) implements CustomPacketPayload {
    public static final Type<OpenConvergingSignalConfigPayload> TYPE =
            new Type<>(CreateSignallingAdditions.id("open_converging_signal_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenConvergingSignalConfigPayload> STREAM_CODEC =
            StreamCodec.ofMember(OpenConvergingSignalConfigPayload::encode, OpenConvergingSignalConfigPayload::new);

    public OpenConvergingSignalConfigPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBlockPos(), buffer.readUtf(16));
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeUtf(direction, 16);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
