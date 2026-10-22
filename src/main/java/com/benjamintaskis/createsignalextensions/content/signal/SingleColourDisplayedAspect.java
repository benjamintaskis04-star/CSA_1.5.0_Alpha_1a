package com.benjamintaskis.createsignalextensions.content.signal;

import net.minecraft.util.StringRepresentable;

/** Physical indications supported by the NSW single-colour-light signal. */
public enum SingleColourDisplayedAspect implements StringRepresentable {
    UNLINKED("unlinked"),
    DARK("dark"),
    OUT_OF_USE("out_of_use"),
    STOP("stop"),
    CAUTION("caution"),
    MEDIUM_ON("medium_on"),
    MEDIUM_OFF("medium_off"),
    PROCEED("proceed"),
    TURNOUT_LEFT("turnout_left"),
    TURNOUT_RIGHT("turnout_right"),
    MEDIUM_TURNOUT_LEFT_ON("medium_turnout_left_on"),
    MEDIUM_TURNOUT_LEFT_OFF("medium_turnout_left_off"),
    MEDIUM_TURNOUT_RIGHT_ON("medium_turnout_right_on"),
    MEDIUM_TURNOUT_RIGHT_OFF("medium_turnout_right_off");

    private final String serializedName;
    SingleColourDisplayedAspect(String serializedName) { this.serializedName = serializedName; }
    @Override public String getSerializedName() { return serializedName; }
}
