package com.benjamintaskis.createsignalextensions.network;
import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.UUID;
public record ToggleManualShuntPayload(UUID trainId) implements CustomPacketPayload {
    public static final Type<ToggleManualShuntPayload> TYPE = new Type<>(CreateSignallingAdditions.id("toggle_manual_shunt"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleManualShuntPayload> STREAM_CODEC = StreamCodec.ofMember(ToggleManualShuntPayload::encode, ToggleManualShuntPayload::new);
    public ToggleManualShuntPayload(RegistryFriendlyByteBuf b){ this(b.readUUID()); }
    private void encode(RegistryFriendlyByteBuf b){ b.writeUUID(trainId); }
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }
}
