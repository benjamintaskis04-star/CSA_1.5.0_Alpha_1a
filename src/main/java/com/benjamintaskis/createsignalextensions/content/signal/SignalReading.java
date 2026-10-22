package com.benjamintaskis.createsignalextensions.content.signal;

public record SignalReading(SignalAspect aspect, TurnoutDirection direction) {
    public static SignalReading stop() { return new SignalReading(SignalAspect.STOP, TurnoutDirection.NONE); }
    public static SignalReading caution() { return new SignalReading(SignalAspect.CAUTION, TurnoutDirection.NONE); }
    public static SignalReading medium() { return new SignalReading(SignalAspect.MEDIUM, TurnoutDirection.NONE); }
    public static SignalReading preliminaryMedium() { return new SignalReading(SignalAspect.PRELIMINARY_MEDIUM, TurnoutDirection.NONE); }
    public static SignalReading cautionTurnout(TurnoutDirection d) {
        return d == TurnoutDirection.NONE ? caution() : new SignalReading(SignalAspect.CAUTION_TURNOUT, d);
    }
    public static SignalReading mediumTurnout(TurnoutDirection d) {
        return d == TurnoutDirection.NONE ? medium() : new SignalReading(SignalAspect.MEDIUM_TURNOUT, d);
    }
    /** Compatibility name retained for older callers: turnout defaults to Caution Turnout. */
    public static SignalReading turnout(TurnoutDirection d) { return cautionTurnout(d); }
    public static SignalReading proceed() { return new SignalReading(SignalAspect.PROCEED, TurnoutDirection.NONE); }
}
