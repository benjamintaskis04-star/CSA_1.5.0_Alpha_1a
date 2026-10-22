package com.benjamintaskis.createsignalextensions.network;
import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public record SetSmallTheatreRowsPayload(BlockPos pos,int rows) implements CustomPacketPayload {
 public static final Type<SetSmallTheatreRowsPayload> TYPE=new Type<>(CreateSignallingAdditions.id("set_small_theatre_rows"));
 public static final StreamCodec<RegistryFriendlyByteBuf,SetSmallTheatreRowsPayload> STREAM_CODEC=StreamCodec.ofMember(SetSmallTheatreRowsPayload::encode,SetSmallTheatreRowsPayload::new);
 public SetSmallTheatreRowsPayload(RegistryFriendlyByteBuf b){this(b.readBlockPos(),b.readVarInt());}
 private void encode(RegistryFriendlyByteBuf b){b.writeBlockPos(pos);b.writeVarInt(rows);}
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
