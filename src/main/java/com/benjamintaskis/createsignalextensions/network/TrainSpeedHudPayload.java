package com.benjamintaskis.createsignalextensions.network;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record TrainSpeedHudPayload(double speedKmh, double limiterKmh) implements CustomPacketPayload {
    public static final Type<TrainSpeedHudPayload> TYPE = new Type<>(CreateSignallingAdditions.id("train_speed_hud"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrainSpeedHudPayload> STREAM_CODEC =
            StreamCodec.ofMember(TrainSpeedHudPayload::encode, TrainSpeedHudPayload::new);

    public TrainSpeedHudPayload(RegistryFriendlyByteBuf b) { this(b.readDouble(), b.readDouble()); }
    private void encode(RegistryFriendlyByteBuf b) { b.writeDouble(speedKmh); b.writeDouble(limiterKmh); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
