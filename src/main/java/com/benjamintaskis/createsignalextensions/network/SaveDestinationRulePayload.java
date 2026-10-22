package com.benjamintaskis.createsignalextensions.network;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.ArrayList;
import java.util.List;

public record SaveDestinationRulePayload(BlockPos pos, String oldPattern, String pattern, String theatreIndication, List<String> smallTheatreIndications, boolean delete)
        implements CustomPacketPayload {
    public static final Type<SaveDestinationRulePayload> TYPE =
            new Type<>(CreateSignallingAdditions.id("save_destination_rule"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveDestinationRulePayload> STREAM_CODEC =
            StreamCodec.ofMember(SaveDestinationRulePayload::encode, SaveDestinationRulePayload::new);

    public SaveDestinationRulePayload(RegistryFriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readUtf(128), buf.readUtf(128), buf.readUtf(8), readCells(buf), buf.readBoolean());
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(oldPattern, 128);
        buf.writeUtf(pattern, 128);
        buf.writeUtf(theatreIndication, 8);
        for (int i = 0; i < 6; i++)
            buf.writeUtf(smallTheatreIndications != null && i < smallTheatreIndications.size() ? smallTheatreIndications.get(i) : "", 4);
        buf.writeBoolean(delete);
    }

    private static List<String> readCells(RegistryFriendlyByteBuf buf) {
        List<String> cells = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) cells.add(buf.readUtf(4));
        return cells;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
