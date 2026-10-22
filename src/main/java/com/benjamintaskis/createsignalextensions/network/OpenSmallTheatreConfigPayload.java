package com.benjamintaskis.createsignalextensions.network;
import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public record OpenSmallTheatreConfigPayload(BlockPos pos,int rows,String linked) implements CustomPacketPayload {
 public static final Type<OpenSmallTheatreConfigPayload> TYPE=new Type<>(CreateSignallingAdditions.id("open_small_theatre_config"));
 public static final StreamCodec<RegistryFriendlyByteBuf,OpenSmallTheatreConfigPayload> STREAM_CODEC=StreamCodec.ofMember(OpenSmallTheatreConfigPayload::encode,OpenSmallTheatreConfigPayload::new);
 public OpenSmallTheatreConfigPayload(RegistryFriendlyByteBuf b){this(b.readBlockPos(),b.readVarInt(),b.readUtf(128));}
 private void encode(RegistryFriendlyByteBuf b){b.writeBlockPos(pos);b.writeVarInt(rows);b.writeUtf(linked,128);}
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
