package com.benjamintaskis.createsignalextensions.network;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.ArrayList;
import java.util.List;

public record SaveSmallTheatreCellsPayload(BlockPos pos, String routeKey, List<String> cells)
        implements CustomPacketPayload {
    public static final Type<SaveSmallTheatreCellsPayload> TYPE =
            new Type<>(CreateSignallingAdditions.id("save_small_theatre_cells"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveSmallTheatreCellsPayload> STREAM_CODEC =
            StreamCodec.ofMember(SaveSmallTheatreCellsPayload::encode, SaveSmallTheatreCellsPayload::new);

    public SaveSmallTheatreCellsPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readUtf(8192), readCells(buf));
    }
    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos); buf.writeUtf(routeKey, 8192);
        for (int i = 0; i < 6; i++)
            buf.writeUtf(cells != null && i < cells.size() ? cells.get(i) : "", 4);
    }
    private static List<String> readCells(RegistryFriendlyByteBuf buf) {
        List<String> cells = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) cells.add(buf.readUtf(4));
        return cells;
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
