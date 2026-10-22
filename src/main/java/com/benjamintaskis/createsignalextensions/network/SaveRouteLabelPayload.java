package com.benjamintaskis.createsignalextensions.network;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SaveRouteLabelPayload(BlockPos pos, String routeKey, String label, String theatreIndication, String routeType, String routeIndication)
        implements CustomPacketPayload {

    public static final Type<SaveRouteLabelPayload> TYPE =
            new Type<>(CreateSignallingAdditions.id("save_route_label"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveRouteLabelPayload> STREAM_CODEC =
            StreamCodec.ofMember(SaveRouteLabelPayload::encode, SaveRouteLabelPayload::new);

    public SaveRouteLabelPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readUtf(8192), buf.readUtf(64), buf.readUtf(8), buf.readUtf(32), buf.readUtf(16));
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(routeKey, 8192);
        buf.writeUtf(label, 64);
        buf.writeUtf(theatreIndication, 8);
        buf.writeUtf(routeType, 32);
        buf.writeUtf(routeIndication, 16);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
