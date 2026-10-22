package com.benjamintaskis.createsignalextensions.mixin;

import com.benjamintaskis.createsignalextensions.content.speed.SpeedRestrictionManager;
import com.simibubi.create.content.trains.entity.Navigation;
import com.simibubi.create.content.trains.entity.Train;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Alpha 5c3 corrective build (internal/public version remains Alpha 5c).
 *
 * Hook Create's automatic Navigation controller itself. Navigation.tick() first
 * calculates and writes Train.targetSpeed, then immediately calls
 * Train.approachTargetSpeed().  Capping at that exact boundary means CSA edits
 * the automatic driver's freshly-calculated command rather than correcting the
 * train later in Train's movement/tick path.
 */
@Mixin(Navigation.class)
public abstract class TrainSpeedMixin {
    @Shadow @Final
    private Train train;

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/trains/entity/Train;approachTargetSpeed(F)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void csa$capAutomaticNavigationTarget(CallbackInfo ci) {
        SpeedRestrictionManager.applyNavigationTargetCap(train);
    }
}
