package com.benjamintaskis.createsignalextensions.content.speed;

import com.benjamintaskis.createsignalextensions.ModBlocks;
import com.benjamintaskis.createsignalextensions.network.OpenSpeedBoardConfigPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

public class SpeedBoardBlockEntity extends TrackLinkedBlockEntity {
    private int defaultSpeedKmh = 60;
    private int muSpeedKmh = 60;
    private int highSpeedKmh = 60;
    private boolean muOverridden = false;
    private boolean highOverridden = false;
    private boolean turnoutMode = false;

    public SpeedBoardBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    @Override public void initialize() {
        super.initialize();
        if (level instanceof net.minecraft.server.level.ServerLevel server) SpeedZoneBoardData.get(server).update(this);
    }
    @Override public void setChanged() {
        super.setChanged();
        if (level instanceof net.minecraft.server.level.ServerLevel server) SpeedZoneBoardData.get(server).update(this);
    }
    public int getSpeedKmh() { return defaultSpeedKmh; }
    public int getDefaultSpeedKmh() { return defaultSpeedKmh; }
    public int getMuSpeedKmh() { return muOverridden ? muSpeedKmh : defaultSpeedKmh; }
    public int getHighSpeedKmh() { return highOverridden ? highSpeedKmh : defaultSpeedKmh; }
    public boolean isMuOverridden() { return muOverridden; }
    public boolean isHighOverridden() { return highOverridden; }
    public boolean hasMuPanel() { Block b=getBlockState().getBlock(); return b==ModBlocks.SPEED_BOARD_DEFAULT_MU.get() || b==ModBlocks.SPEED_BOARD_DEFAULT_MU_HIGH_SPEED.get(); }
    public boolean hasHighPanel() { Block b=getBlockState().getBlock(); return b==ModBlocks.SPEED_BOARD_DEFAULT_HIGH_SPEED.get() || b==ModBlocks.SPEED_BOARD_DEFAULT_MU_HIGH_SPEED.get(); }
    public boolean isTurnoutBoard() { return turnoutMode; }
    public boolean isUnrestricted() { return getBlockState().getBlock()==ModBlocks.UNRESTRICTED_SPEED_BOARD.get() || (getBlockState().hasProperty(SpeedBoardBlock.UNRESTRICTED) && getBlockState().getValue(SpeedBoardBlock.UNRESTRICTED)); }

    public int getApplicableSpeedKmh(TrainSpeedCategory category, boolean manual) {
        if (manual || category == null || category == TrainSpeedCategory.DEFAULT) return defaultSpeedKmh;
        if (category == TrainSpeedCategory.MULTIPLE_UNIT) return hasMuPanel() ? getMuSpeedKmh() : defaultSpeedKmh;
        if (category == TrainSpeedCategory.HIGH_SPEED) {
            if (hasHighPanel()) return getHighSpeedKmh();
            if (hasMuPanel()) return getMuSpeedKmh();
            return defaultSpeedKmh;
        }
        return defaultSpeedKmh;
    }

    public void setConfiguration(int def, int mu, int high, boolean muOverride, boolean highOverride, boolean turnout) {
        defaultSpeedKmh=clamp(def); muSpeedKmh=clamp(mu); highSpeedKmh=clamp(high);
        muOverridden=hasMuPanel() && muOverride; highOverridden=hasHighPanel() && highOverride; turnoutMode=turnout;
        setChanged(); notifyUpdate();
    }
    private static int clamp(int v){ return Math.max(1,Math.min(999,v)); }

    public void openConfigurationScreen(ServerPlayer player) {
        if (isUnrestricted()) return;
        PacketDistributor.sendToPlayer(player, new OpenSpeedBoardConfigPayload(worldPosition, defaultSpeedKmh, getMuSpeedKmh(), getHighSpeedKmh(), hasMuPanel(), hasHighPanel(), muOverridden, highOverridden, turnoutMode));
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("DefaultSpeedKmh", defaultSpeedKmh); tag.putInt("MuSpeedKmh", muSpeedKmh); tag.putInt("HighSpeedKmh", highSpeedKmh);
        tag.putBoolean("MuOverridden",muOverridden); tag.putBoolean("HighOverridden",highOverridden); tag.putBoolean("TurnoutMode",turnoutMode);
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        // Alpha 2/3 compatibility: old SpeedKmh becomes the Default value.
        defaultSpeedKmh=clamp(tag.contains("DefaultSpeedKmh")?tag.getInt("DefaultSpeedKmh"):(tag.contains("SpeedKmh")?tag.getInt("SpeedKmh"):60));
        muSpeedKmh=clamp(tag.contains("MuSpeedKmh")?tag.getInt("MuSpeedKmh"):defaultSpeedKmh);
        highSpeedKmh=clamp(tag.contains("HighSpeedKmh")?tag.getInt("HighSpeedKmh"):defaultSpeedKmh);
        muOverridden=tag.getBoolean("MuOverridden"); highOverridden=tag.getBoolean("HighOverridden"); turnoutMode=tag.getBoolean("TurnoutMode");
    }
}
