package com.benjamintaskis.createsignalextensions.content.signal;

import net.minecraft.util.StringRepresentable;

/** Physical indications supported by the NSW shunt signal family. */
public enum ShuntDisplayedAspect implements StringRepresentable {
    STOP("stop", false),
    SHUNT_PROCEED("shunt_proceed", true),
    RUNNING_PROCEED("running_proceed", false);

    private final String serializedName;
    private final boolean restrictiveProceed;

    ShuntDisplayedAspect(String serializedName, boolean restrictiveProceed) {
        this.serializedName = serializedName;
        this.restrictiveProceed = restrictiveProceed;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /**
     * Alpha 1 speed-system foundation. SHUNT PROCEED is deliberately marked as
     * a restrictive authority. A later 0.1.4 build can attach the actual speed
     * value/enforcement to this flag without changing the displayed aspect.
     */
    public boolean isRestrictiveProceed() {
        return restrictiveProceed;
    }
}
