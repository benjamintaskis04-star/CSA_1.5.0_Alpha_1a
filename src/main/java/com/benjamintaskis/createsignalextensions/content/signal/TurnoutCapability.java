package com.benjamintaskis.createsignalextensions.content.signal;

import net.minecraft.util.StringRepresentable;

/** Physical turnout directions a signal head is capable of displaying. */
public enum TurnoutCapability implements StringRepresentable {
    NONE("none"),
    LEFT("left"),
    RIGHT("right"),
    BOTH("both");

    private final String serializedName;

    TurnoutCapability(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public boolean supports(TurnoutDirection direction) {
        return switch (direction) {
            case LEFT -> this == LEFT || this == BOTH;
            case RIGHT -> this == RIGHT || this == BOTH;
            case NONE -> false;
        };
    }
}
