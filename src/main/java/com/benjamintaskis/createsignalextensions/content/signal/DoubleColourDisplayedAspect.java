package com.benjamintaskis.createsignalextensions.content.signal;

import net.minecraft.util.StringRepresentable;

public enum DoubleColourDisplayedAspect implements StringRepresentable {
    DARK("dark"),
    OUT_OF_USE("out_of_use"),
    STOP("stop"),
    CAUTION("caution"),
    MEDIUM("medium"),
    PRELIMINARY_MEDIUM_ON("preliminary_medium_on"),
    PRELIMINARY_MEDIUM_OFF("preliminary_medium_off"),
    PROCEED("proceed"),
    CAUTION_TURNOUT("caution_turnout"),
    MEDIUM_TURNOUT("medium_turnout");

    private final String name;
    DoubleColourDisplayedAspect(String name) { this.name = name; }
    @Override public String getSerializedName() { return name; }
}
