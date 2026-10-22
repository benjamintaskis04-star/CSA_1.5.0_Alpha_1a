package com.benjamintaskis.createsignalextensions;

import com.benjamintaskis.createsignalextensions.content.display.SignalAspectDisplaySource;
import com.benjamintaskis.createsignalextensions.content.display.SignalDebugDisplaySource;
import com.benjamintaskis.createsignalextensions.content.display.SelectedRouteDebugDisplaySource;
import com.benjamintaskis.createsignalextensions.content.display.SelectedRouteDisplaySource;
import com.benjamintaskis.createsignalextensions.content.display.SelectedRouteDestinationDisplaySource;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.tterrag.registrate.util.entry.RegistryEntry;

public class ModDisplaySources {
    public static final RegistryEntry<DisplaySource, SignalAspectDisplaySource> SIGNAL_ASPECT =
            CreateSignallingAdditions.REGISTRATE.displaySource("signal_aspect", SignalAspectDisplaySource::new).register();

    public static final RegistryEntry<DisplaySource, SignalDebugDisplaySource> SIGNAL_DEBUG =
            CreateSignallingAdditions.REGISTRATE.displaySource("signal_debug", SignalDebugDisplaySource::new).register();

    public static final RegistryEntry<DisplaySource, SelectedRouteDisplaySource> SELECTED_ROUTE =
            CreateSignallingAdditions.REGISTRATE.displaySource("selected_route", SelectedRouteDisplaySource::new).register();

    public static final RegistryEntry<DisplaySource, SelectedRouteDestinationDisplaySource> SELECTED_ROUTE_DESTINATION =
            CreateSignallingAdditions.REGISTRATE.displaySource("selected_route_destination", SelectedRouteDestinationDisplaySource::new).register();

    public static final RegistryEntry<DisplaySource, SelectedRouteDebugDisplaySource> SELECTED_ROUTE_DEBUG =
            CreateSignallingAdditions.REGISTRATE.displaySource("selected_route_debug", SelectedRouteDebugDisplaySource::new).register();

    public static void register() {}
}
