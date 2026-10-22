package com.benjamintaskis.createsignalextensions.network;
import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import net.minecraft.core.BlockPos; import net.minecraft.network.RegistryFriendlyByteBuf; import net.minecraft.network.codec.StreamCodec; import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public record OpenSpeedBoardConfigPayload(BlockPos pos,int defaultKmh,int muKmh,int highKmh,boolean hasMu,boolean hasHigh,boolean muOverridden,boolean highOverridden,boolean turnoutMode) implements CustomPacketPayload {
 public static final Type<OpenSpeedBoardConfigPayload> TYPE=new Type<>(CreateSignallingAdditions.id("open_speed_board_config"));
 public static final StreamCodec<RegistryFriendlyByteBuf,OpenSpeedBoardConfigPayload> STREAM_CODEC=StreamCodec.ofMember(OpenSpeedBoardConfigPayload::encode,OpenSpeedBoardConfigPayload::new);
 public OpenSpeedBoardConfigPayload(RegistryFriendlyByteBuf b){this(b.readBlockPos(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readBoolean());}
 private void encode(RegistryFriendlyByteBuf b){b.writeBlockPos(pos);b.writeVarInt(defaultKmh);b.writeVarInt(muKmh);b.writeVarInt(highKmh);b.writeBoolean(hasMu);b.writeBoolean(hasHigh);b.writeBoolean(muOverridden);b.writeBoolean(highOverridden);b.writeBoolean(turnoutMode);}
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
