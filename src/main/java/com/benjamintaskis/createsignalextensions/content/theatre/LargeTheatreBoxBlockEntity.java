package com.benjamintaskis.createsignalextensions.content.theatre;

import com.benjamintaskis.createsignalextensions.content.route.RouteInterfaceBlockEntity;
import com.benjamintaskis.createsignalextensions.content.signal.DoubleColourDisplayedAspect;
import com.benjamintaskis.createsignalextensions.content.signal.DoubleColourLightSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.SingleColourDisplayedAspect;
import com.benjamintaskis.createsignalextensions.content.signal.SingleColourLightSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.SignalAspectLinkManager;
import com.benjamintaskis.createsignalextensions.content.signal.TurnoutDirection;
import net.minecraft.server.level.ServerLevel;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Objects;

public class LargeTheatreBoxBlockEntity extends SmartBlockEntity {
    private BlockPos linkedRouteInterface;
    private BlockPos linkedSignal;
    private String indication = "";

    public LargeTheatreBoxBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(4);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level == null || level.isClientSide)
            return;
        refreshIndication();
    }

    public void setLinkedRouteInterface(BlockPos pos) {
        linkedRouteInterface = pos == null ? null : pos.immutable();
        linkedSignal = null;
        setChanged();
        refreshIndication();
        notifyUpdate();
    }

    public void clearLinkedRouteInterface() {
        linkedRouteInterface = null;
        linkedSignal = null;
        indication = "";
        setChanged();
        notifyUpdate();
    }

    public BlockPos getLinkedRouteInterfacePos() {
        return linkedRouteInterface;
    }

    public void setLinkedSignal(BlockPos pos) {
        linkedSignal = pos == null ? null : pos.immutable();
        linkedRouteInterface = null;
        setChanged();
        refreshIndication();
        notifyUpdate();
    }

    public BlockPos getLinkedSignalPos() {
        return linkedSignal;
    }

    public String getIndication() {
        return indication;
    }

    private void refreshIndication() {
        String next = "";

        if (level != null && linkedRouteInterface != null && level.hasChunkAt(linkedRouteInterface)) {
            BlockEntity be = level.getBlockEntity(linkedRouteInterface);
            if (be instanceof RouteInterfaceBlockEntity routeInterface) {
                String mapped = routeInterface.getSelectedTheatreIndication();
                if (mapped != null && !mapped.isBlank())
                    next = mapped;
            }
        } else if (level instanceof ServerLevel serverLevel && linkedSignal != null && level.hasChunkAt(linkedSignal)) {
            BlockState state = level.getBlockState(linkedSignal);
            TurnoutDirection direction = TurnoutDirection.NONE;

            if (state.getBlock() instanceof SingleColourLightSignalBlock) {
                SingleColourDisplayedAspect aspect = state.getValue(SingleColourLightSignalBlock.ASPECT);
                direction = switch (aspect) {
                    case TURNOUT_LEFT, MEDIUM_TURNOUT_LEFT_ON, MEDIUM_TURNOUT_LEFT_OFF -> TurnoutDirection.LEFT;
                    case TURNOUT_RIGHT, MEDIUM_TURNOUT_RIGHT_ON, MEDIUM_TURNOUT_RIGHT_OFF -> TurnoutDirection.RIGHT;
                    default -> TurnoutDirection.NONE;
                };
            } else if (state.getBlock() instanceof DoubleColourLightSignalBlock) {
                DoubleColourDisplayedAspect aspect = state.getValue(DoubleColourLightSignalBlock.ASPECT);
                if (aspect == DoubleColourDisplayedAspect.CAUTION_TURNOUT
                        || aspect == DoubleColourDisplayedAspect.MEDIUM_TURNOUT)
                    direction = SignalAspectLinkManager.get(serverLevel).displayedTurnoutDirection(serverLevel, linkedSignal);
            }

            // Viewed from the front of the theatre: \ rises toward the left, / toward the right.
            if (direction == TurnoutDirection.LEFT) next = "\\";
            else if (direction == TurnoutDirection.RIGHT) next = "/";
        }

        if (!Objects.equals(indication, next)) {
            indication = next;
            setChanged();
            notifyUpdate();
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (linkedRouteInterface != null)
            tag.putLong("LinkedRouteInterface", linkedRouteInterface.asLong());
        if (linkedSignal != null)
            tag.putLong("LinkedSignal", linkedSignal.asLong());
        tag.putString("Indication", indication);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        linkedRouteInterface = tag.contains("LinkedRouteInterface")
                ? BlockPos.of(tag.getLong("LinkedRouteInterface"))
                : null;
        linkedSignal = tag.contains("LinkedSignal")
                ? BlockPos.of(tag.getLong("LinkedSignal"))
                : null;
        indication = tag.getString("Indication");
    }
}
