package com.benjamintaskis.createsignalextensions.content.speed;

import net.minecraft.network.chat.Component;

public enum TrainSpeedCategory {
    DEFAULT("Default"),
    MULTIPLE_UNIT("Multiple Unit"),
    HIGH_SPEED("High Speed");

    private final String displayName;
    TrainSpeedCategory(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
    public Component component() { return Component.literal(displayName); }

    public static TrainSpeedCategory fromOrdinal(int value) {
        TrainSpeedCategory[] values = values();
        return value < 0 || value >= values.length ? DEFAULT : values[value];
    }
}
