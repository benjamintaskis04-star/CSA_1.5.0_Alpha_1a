package com.benjamintaskis.createsignalextensions.content.speed;

import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.BezierTrackPointLocation;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Shared persistence for track-linked auxiliary railway equipment.
 * The target is deliberately kept separate from Create edge-point ownership:
 * speed boards and auxiliary signs observe a selected track but do not split
 * the track graph or occupy a Create signal/station/observer edge point.
 */
public abstract class TrackLinkedBlockEntity extends SmartBlockEntity {
    private BlockPos targetTrack;
    private boolean targetDirection;
    private BlockPos bezierCurveTarget;
    private int bezierSegment = -1;

    protected TrackLinkedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide)
            SpeedRestrictionManager.registerTrackLinked(this);
    }

    @Override
    public void invalidate() {
        if (level != null && !level.isClientSide)
            SpeedRestrictionManager.unregisterTrackLinked(this);
        super.invalidate();
    }

    public boolean hasTrackTarget() {
        return targetTrack != null;
    }

    public BlockPos getTargetTrack() {
        return targetTrack;
    }

    public boolean getTargetDirection() {
        return targetDirection;
    }

    public boolean hasBezierTarget() {
        return targetTrack != null && bezierCurveTarget != null && bezierSegment >= 0;
    }

    public BezierTrackPointLocation getBezierTarget() {
        return hasBezierTarget() ? new BezierTrackPointLocation(bezierCurveTarget, bezierSegment) : null;
    }

    public void setTrackTarget(BlockPos targetTrack, boolean targetDirection) {
        setTrackTarget(targetTrack, targetDirection, null);
    }

    public void setTrackTarget(BlockPos targetTrack, boolean targetDirection, BezierTrackPointLocation bezier) {
        this.targetTrack = targetTrack == null ? null : targetTrack.immutable();
        this.targetDirection = targetDirection;
        if (bezier == null) {
            bezierCurveTarget = null;
            bezierSegment = -1;
        } else {
            bezierCurveTarget = bezier.curveTarget().immutable();
            bezierSegment = bezier.segment();
        }
        setChanged();
        notifyUpdate();
    }

    /** Returns the actual selected point in world coordinates, including curves. */
    public Vec3 getTrackTargetPosition(Level level) {
        if (targetTrack == null)
            return null;
        if (hasBezierTarget()) {
            // Beta 2a: speed scanning must not make a block-entity lookup that
            // synchronously loads the selected track's chunk.
            if (!level.hasChunkAt(targetTrack))
                return null;
            if (level.getBlockEntity(targetTrack) instanceof TrackBlockEntity trackBE) {
                BezierConnection connection = trackBE.getConnections().get(bezierCurveTarget);
                if (connection != null) {
                    double segmentCount = Mth.floor(connection.getLength() * 2);
                    if (segmentCount > 0) {
                        double t = (bezierSegment + 1) / segmentCount;
                        return connection.getPosition(Mth.clamp(t, 0, 1));
                    }
                }
            }
        }
        return Vec3.atCenterOf(targetTrack);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (targetTrack != null) {
            tag.putLong("TargetTrackPos", targetTrack.asLong());
            tag.putBoolean("TargetDirection", targetDirection);
        }
        if (bezierCurveTarget != null && bezierSegment >= 0) {
            tag.putLong("TargetBezierCurve", bezierCurveTarget.asLong());
            tag.putInt("TargetBezierSegment", bezierSegment);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        targetTrack = tag.contains("TargetTrackPos") ? BlockPos.of(tag.getLong("TargetTrackPos")) : null;
        targetDirection = tag.getBoolean("TargetDirection");
        bezierCurveTarget = tag.contains("TargetBezierCurve") ? BlockPos.of(tag.getLong("TargetBezierCurve")) : null;
        bezierSegment = tag.contains("TargetBezierSegment") ? tag.getInt("TargetBezierSegment") : -1;
    }
}
