package com.benjamintaskis.createsignalextensions.content.signal;

import com.benjamintaskis.createsignalextensions.content.route.RouteProbe;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;
import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import com.simibubi.create.content.trains.signal.SignalBlock.SignalType;

import java.util.Locale;

public final class SignalAspectResolver {
    private SignalAspectResolver() {}

    public static SignalReading resolve(SignalBlockEntity current, SignalBlockEntity next) {
        return resolve(current, next, TurnoutDirection.NONE, null);
    }

    public static SignalReading resolve(SignalBlockEntity current, SignalBlockEntity next, TurnoutDirection routeDirection) {
        return resolve(current, next, routeDirection, null);
    }

    /**
     * Alpha 3 NSW progression. nextNswAspect is the logical indication currently shown
     * by the selected following NSW signal when one is available.
     */
    public static SignalReading resolve(SignalBlockEntity current, SignalBlockEntity next,
                                        TurnoutDirection routeDirection, SignalAspect nextNswAspect) {
        if (current == null || isStop(current)) return SignalReading.stop();

        boolean nextIsStop = next == null ? false : isStop(next);
        if (nextNswAspect == SignalAspect.STOP) nextIsStop = true;

        // A positively selected diverging route is the only turnout override.
        // STOP ahead = steady Caution Turnout; anything else = flashing Medium Turnout.
        if (routeDirection != null && routeDirection != TurnoutDirection.NONE)
            return nextIsStop ? SignalReading.cautionTurnout(routeDirection)
                    : SignalReading.mediumTurnout(routeDirection);

        // Compatibility fallback for a signal without a registered Route Interface.
        RouteProbe.Result route = RouteProbe.probe(current.getSignal(), current.getBlockPos(), 128);
        if (route.resolved()) {
            TurnoutDirection d = "DIVERGING LEFT".equals(route.routeType()) ? TurnoutDirection.LEFT
                    : "DIVERGING RIGHT".equals(route.routeType()) ? TurnoutDirection.RIGHT
                    : TurnoutDirection.NONE;
            if (d != TurnoutDirection.NONE)
                return nextIsStop ? SignalReading.cautionTurnout(d) : SignalReading.mediumTurnout(d);
        }

        // Shared NSW progression. Double-colour heads can display PRELIMINARY MEDIUM;
        // single-colour heads skip that physical step in their output mapping.
        // STOP -> CAUTION
        // CAUTION / any TURNOUT -> MEDIUM
        // MEDIUM -> PRELIMINARY MEDIUM
        // PRELIMINARY MEDIUM / PROCEED -> PROCEED
        if (nextIsStop) return SignalReading.caution();
        if (nextNswAspect == SignalAspect.CAUTION
                || nextNswAspect == SignalAspect.CAUTION_TURNOUT
                || nextNswAspect == SignalAspect.MEDIUM_TURNOUT)
            return SignalReading.medium();
        if (nextNswAspect == SignalAspect.MEDIUM)
            return SignalReading.preliminaryMedium();
        return SignalReading.proceed();
    }

    public static String createState(SignalBlockEntity signal) {
        return signal == null ? "MISSING" : signal.getState().name();
    }

    public static boolean isStop(SignalBlockEntity signal) {
        if (signal == null) return true;
        SignalBoundary boundary = signal.getSignal();
        if (boundary == null) return true;

        // Alpha 6c: derive danger from Create's live route-protection data instead of
        // blindly treating a cached RED state as danger. A reservation made by this
        // boundary is authority for this route and must remain permissive; a train,
        // forced-red input, or reservation from another/conflicting boundary remains STOP.
        Boolean side = sideFor(signal, boundary);
        if (side == null) return true;
        if (boundary.isForcedRed(side)) return true;

        // Alpha 6g: CROSS/chain signals must not be replaced to STOP merely because
        // their protected edge group becomes occupied while the approaching train still
        // holds route authority. Create deliberately keeps the chain signal permissive
        // until the train crosses the boundary, so its own live RED state is authoritative
        // for replacement. Ordinary ENTRY signals retain the occupancy-based behaviour.
        if (isChain(signal))
            return signal.getState().name().equals("RED");

        var groupId = boundary.groups.get(side);
        if (groupId == null) return true;
        SignalEdgeGroup group = Create.RAILWAYS.signalEdgeGroups.get(groupId);
        if (group == null) return true;
        return group.isOccupiedUnless(boundary);
    }

    private static Boolean sideFor(SignalBlockEntity signal, SignalBoundary boundary) {
        if (boundary.blockEntities.get(true).containsKey(signal.getBlockPos())) return true;
        if (boundary.blockEntities.get(false).containsKey(signal.getBlockPos())) return false;
        return null;
    }

    /**
     * Read Create's actual edge-group occupancy for the side of the boundary represented
     * by this particular signal block entity. This mirrors SignalBoundary.tickState()'s
     * immediate-block occupancy test and works for both ENTRY and CROSS/chain signals.
     */
    /** True when this physical Create signal is configured as a CROSS/chain signal. */
    public static boolean isChain(SignalBlockEntity signal) {
        if (signal == null || signal.getSignal() == null) return false;
        return signal.getSignal().getTypeFor(signal.getBlockPos()) == SignalType.CROSS_SIGNAL;
    }

    public static boolean isProtectedBlockOccupied(SignalBlockEntity signal) {
        if (signal == null) return false;
        SignalBoundary boundary = signal.getSignal();
        if (boundary == null) return false;

        Boolean side = sideFor(signal, boundary);
        if (side == null) return false;

        var groupId = boundary.groups.get(side);
        if (groupId == null) return false;
        SignalEdgeGroup group = Create.RAILWAYS.signalEdgeGroups.get(groupId);
        return group != null && group.isOccupiedUnless(boundary);
    }
}
