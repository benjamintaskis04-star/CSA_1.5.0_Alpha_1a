package com.benjamintaskis.createsignalextensions;

import com.benjamintaskis.createsignalextensions.content.GuidedBlockItem;
import com.benjamintaskis.createsignalextensions.content.route.RouteInterfaceBlock;
import com.benjamintaskis.createsignalextensions.content.signal.SingleColourLightSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.TwoAspectSingleColourLightSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.DivergingSingleColourLightSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.ConvergingSingleColourLightSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.DistantSingleColourLightSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.DoubleColourLightSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.FixedRedSingleColourLightSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.FixedRedDoubleColourLightSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.DivergingDoubleColourLightSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.ConvergingDoubleColourLightSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.UniversalDoubleColourLightSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.EndOfLineMarkerBlock;
import com.benjamintaskis.createsignalextensions.content.signal.TunnelUniversalDoubleColourLightSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.GroundShuntSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.PostShuntSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.GroundTheatreShuntSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.PostTheatreShuntSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.GroundRunningShuntSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.PostRunningShuntSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.HangingShuntSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.HangingTheatreShuntSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.HangingRunningShuntSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.MainlineAddonLampBlock;
import com.benjamintaskis.createsignalextensions.content.signal.SignalPlateBlock;
import com.benjamintaskis.createsignalextensions.content.signal.RepeaterSignalBlock;
import com.benjamintaskis.createsignalextensions.content.speed.SpeedBoardBlock;
import com.benjamintaskis.createsignalextensions.content.speed.EndOfShuntSignBlock;
import com.benjamintaskis.createsignalextensions.content.speed.TrackLinkedBlockItem;
import com.benjamintaskis.createsignalextensions.content.theatre.LargeTheatreBoxBlock;
import com.benjamintaskis.createsignalextensions.content.theatre.SmallTheatreBoxBlock;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.world.level.block.Blocks;

public class ModBlocks {
    public static final BlockEntry<RouteInterfaceBlock> ROUTE_INTERFACE = CreateSignallingAdditions.REGISTRATE
            .block("route_interface", RouteInterfaceBlock::new)
            .initialProperties(() -> Blocks.POLISHED_ANDESITE)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion())
            .transform(DisplaySource.displaySource(ModDisplaySources.SIGNAL_ASPECT))
            .transform(DisplaySource.displaySource(ModDisplaySources.SIGNAL_DEBUG))
            .transform(DisplaySource.displaySource(ModDisplaySources.SELECTED_ROUTE))
            .transform(DisplaySource.displaySource(ModDisplaySources.SELECTED_ROUTE_DESTINATION))
            .transform(DisplaySource.displaySource(ModDisplaySources.SELECTED_ROUTE_DEBUG))
            .item((block, properties) -> new GuidedBlockItem(block, properties, "route_interface"))
            .build()
            .register();

    public static final BlockEntry<LargeTheatreBoxBlock> LARGE_THEATRE_BOX = CreateSignallingAdditions.REGISTRATE
            .block("large_theatre_box", LargeTheatreBoxBlock::new)
            .initialProperties(() -> Blocks.POLISHED_ANDESITE)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion())
            .item((block, properties) -> new GuidedBlockItem(block, properties, "large_theatre_box"))
            .build()
            .register();

    public static final BlockEntry<SmallTheatreBoxBlock> SMALL_THEATRE_BOX = CreateSignallingAdditions.REGISTRATE
            .block("small_theatre_box", SmallTheatreBoxBlock::new)
            .initialProperties(() -> Blocks.POLISHED_ANDESITE)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion())
            .item((block, properties) -> new GuidedBlockItem(block, properties, "small_theatre_box"))
            .build()
            .register();


    public static final BlockEntry<SingleColourLightSignalBlock> NSW_SINGLE_COLOUR_SIGNAL = CreateSignallingAdditions.REGISTRATE
            .block("nsw_single_colour_signal", SingleColourLightSignalBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(state -> 7))
            .item()
            .build()
            .register();


    public static final BlockEntry<TwoAspectSingleColourLightSignalBlock> NSW_SINGLE_COLOUR_TWO_ASPECT_SIGNAL = CreateSignallingAdditions.REGISTRATE
            .block("nsw_single_colour_two_aspect_signal", TwoAspectSingleColourLightSignalBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(state -> 7))
            .item().build().register();

    public static final BlockEntry<DivergingSingleColourLightSignalBlock> NSW_SINGLE_COLOUR_DIVERGING_SIGNAL = CreateSignallingAdditions.REGISTRATE
            .block("nsw_single_colour_diverging_signal", DivergingSingleColourLightSignalBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(state -> 7))
            .item().build().register();

    public static final BlockEntry<ConvergingSingleColourLightSignalBlock> NSW_SINGLE_COLOUR_CONVERGING_SIGNAL = CreateSignallingAdditions.REGISTRATE
            .block("nsw_single_colour_converging_signal", ConvergingSingleColourLightSignalBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(state -> 7))
            .item().build().register();

    public static final BlockEntry<DistantSingleColourLightSignalBlock> NSW_SINGLE_COLOUR_DISTANT_SIGNAL = CreateSignallingAdditions.REGISTRATE
            .block("nsw_single_colour_distant_signal", DistantSingleColourLightSignalBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(state -> 7))
            .item().build().register();



    public static final BlockEntry<FixedRedSingleColourLightSignalBlock> NSW_SINGLE_COLOUR_FIXED_RED_SIGNAL = CreateSignallingAdditions.REGISTRATE
            .block("nsw_single_colour_fixed_red_signal", FixedRedSingleColourLightSignalBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(state -> 7))
            .item().build().register();

    public static final BlockEntry<DoubleColourLightSignalBlock> NSW_DOUBLE_COLOUR_SIGNAL = CreateSignallingAdditions.REGISTRATE
            .block("nsw_double_colour_signal", DoubleColourLightSignalBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(state -> 7))
            .item().build().register();


    public static final BlockEntry<FixedRedDoubleColourLightSignalBlock> NSW_DOUBLE_COLOUR_FIXED_RED_SIGNAL = CreateSignallingAdditions.REGISTRATE
            .block("nsw_double_colour_fixed_red_signal", FixedRedDoubleColourLightSignalBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(state -> 7))
            .item().build().register();

    public static final BlockEntry<DivergingDoubleColourLightSignalBlock> NSW_DOUBLE_COLOUR_DIVERGING_SIGNAL = CreateSignallingAdditions.REGISTRATE
            .block("nsw_double_colour_diverging_signal", DivergingDoubleColourLightSignalBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(state -> 7))
            .item().build().register();

    public static final BlockEntry<ConvergingDoubleColourLightSignalBlock> NSW_DOUBLE_COLOUR_CONVERGING_SIGNAL = CreateSignallingAdditions.REGISTRATE
            .block("nsw_double_colour_converging_signal", ConvergingDoubleColourLightSignalBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(state -> 7))
            .item().build().register();

    public static final BlockEntry<UniversalDoubleColourLightSignalBlock> NSW_DOUBLE_COLOUR_UNIVERSAL_SIGNAL = CreateSignallingAdditions.REGISTRATE
            .block("nsw_double_colour_universal_signal", UniversalDoubleColourLightSignalBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(state -> 7))
            .item().build().register();

    public static final BlockEntry<TunnelUniversalDoubleColourLightSignalBlock> NSW_DOUBLE_COLOUR_UNIVERSAL_TUNNEL_SIGNAL = CreateSignallingAdditions.REGISTRATE
            .block("nsw_double_colour_universal_tunnel_signal", TunnelUniversalDoubleColourLightSignalBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(state -> 7))
            .item().build().register();

    public static final BlockEntry<GroundShuntSignalBlock> NSW_GROUND_SHUNT_SIGNAL = CreateSignallingAdditions.REGISTRATE
            .block("nsw_ground_shunt_signal", GroundShuntSignalBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(state -> 7))
            .item().build().register();

    public static final BlockEntry<PostShuntSignalBlock> NSW_POST_SHUNT_SIGNAL = CreateSignallingAdditions.REGISTRATE
            .block("nsw_post_shunt_signal", PostShuntSignalBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(state -> 7))
            .item().build().register();

    public static final BlockEntry<GroundTheatreShuntSignalBlock> NSW_GROUND_THEATRE_SHUNT_SIGNAL = CreateSignallingAdditions.REGISTRATE
            .block("nsw_ground_theatre_shunt_signal", GroundTheatreShuntSignalBlock::new).initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(state -> 7)).item().build().register();

    public static final BlockEntry<PostTheatreShuntSignalBlock> NSW_POST_THEATRE_SHUNT_SIGNAL = CreateSignallingAdditions.REGISTRATE
            .block("nsw_post_theatre_shunt_signal", PostTheatreShuntSignalBlock::new).initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(state -> 7)).item().build().register();

    public static final BlockEntry<HangingShuntSignalBlock> NSW_HANGING_SHUNT_SIGNAL = CreateSignallingAdditions.REGISTRATE
            .block("nsw_hanging_shunt_signal", HangingShuntSignalBlock::new).initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(state -> 7)).item().build().register();

    public static final BlockEntry<HangingTheatreShuntSignalBlock> NSW_HANGING_THEATRE_SHUNT_SIGNAL = CreateSignallingAdditions.REGISTRATE
            .block("nsw_hanging_theatre_shunt_signal", HangingTheatreShuntSignalBlock::new).initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(state -> 7)).item().build().register();


    public static final BlockEntry<GroundRunningShuntSignalBlock> NSW_GROUND_RUNNING_SHUNT_SIGNAL = CreateSignallingAdditions.REGISTRATE
            .block("nsw_ground_running_shunt_signal", GroundRunningShuntSignalBlock::new).initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(state -> 7)).item().build().register();

    public static final BlockEntry<PostRunningShuntSignalBlock> NSW_POST_RUNNING_SHUNT_SIGNAL = CreateSignallingAdditions.REGISTRATE
            .block("nsw_post_running_shunt_signal", PostRunningShuntSignalBlock::new).initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(state -> 7)).item().build().register();

    public static final BlockEntry<HangingRunningShuntSignalBlock> NSW_HANGING_RUNNING_SHUNT_SIGNAL = CreateSignallingAdditions.REGISTRATE
            .block("nsw_hanging_running_shunt_signal", HangingRunningShuntSignalBlock::new).initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(state -> 7)).item().build().register();

    public static final BlockEntry<MainlineAddonLampBlock> NSW_MAINLINE_SHUNT_LAMP = CreateSignallingAdditions.REGISTRATE
            .block("nsw_mainline_shunt_lamp", p -> new MainlineAddonLampBlock(p, MainlineAddonLampBlock.Kind.SHUNT))
            .initialProperties(() -> Blocks.IRON_BLOCK).properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(s -> s.getValue(MainlineAddonLampBlock.LIT) ? 7 : 0))
            .item().build().register();

    public static final BlockEntry<MainlineAddonLampBlock> NSW_MAINLINE_DEAD_END_LAMP = CreateSignallingAdditions.REGISTRATE
            .block("nsw_mainline_dead_end_lamp", p -> new MainlineAddonLampBlock(p, MainlineAddonLampBlock.Kind.DEAD_END))
            .initialProperties(() -> Blocks.IRON_BLOCK).properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(s -> s.getValue(MainlineAddonLampBlock.LIT) ? 7 : 0))
            .item().build().register();

    public static final BlockEntry<SignalPlateBlock> SIGNAL_PLATE = CreateSignallingAdditions.REGISTRATE
            .block("signal_plate", SignalPlateBlock::new).initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(1.0f, 3.0f).noOcclusion()).item().build().register();

    public static final BlockEntry<RepeaterSignalBlock> NSW_REPEATER_SIGNAL = CreateSignallingAdditions.REGISTRATE
            .block("nsw_repeater_signal", RepeaterSignalBlock::new).initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(s -> s.getValue(RepeaterSignalBlock.PROCEED) ? 7 : 7))
            .item().build().register();

    public static final BlockEntry<SpeedBoardBlock> SPEED_BOARD = CreateSignallingAdditions.REGISTRATE
            .block("speed_board", SpeedBoardBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion())
            .item((block, properties) -> new TrackLinkedBlockItem(block, properties)).build().register();

    public static final BlockEntry<SpeedBoardBlock> SPEED_BOARD_DEFAULT_MU = CreateSignallingAdditions.REGISTRATE
            .block("speed_board_default_mu", SpeedBoardBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion())
            .item((block, properties) -> new TrackLinkedBlockItem(block, properties)).build().register();

    public static final BlockEntry<SpeedBoardBlock> SPEED_BOARD_DEFAULT_HIGH_SPEED = CreateSignallingAdditions.REGISTRATE
            .block("speed_board_default_high_speed", SpeedBoardBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion())
            .item((block, properties) -> new TrackLinkedBlockItem(block, properties)).build().register();

    public static final BlockEntry<SpeedBoardBlock> SPEED_BOARD_DEFAULT_MU_HIGH_SPEED = CreateSignallingAdditions.REGISTRATE
            .block("speed_board_default_mu_high_speed", SpeedBoardBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion())
            .item((block, properties) -> new TrackLinkedBlockItem(block, properties)).build().register();

    public static final BlockEntry<SpeedBoardBlock> UNRESTRICTED_SPEED_BOARD = CreateSignallingAdditions.REGISTRATE
            .block("unrestricted_speed_board", SpeedBoardBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion())
            .item((block, properties) -> new TrackLinkedBlockItem(block, properties)).build().register();

    public static final BlockEntry<EndOfShuntSignBlock> END_OF_SHUNT_SIGN = CreateSignallingAdditions.REGISTRATE
            .block("end_of_shunt_sign", EndOfShuntSignBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion())
            .item((block, properties) -> new TrackLinkedBlockItem(block, properties)).build().register();

    public static final BlockEntry<EndOfLineMarkerBlock> NSW_END_OF_LINE_MARKER = CreateSignallingAdditions.REGISTRATE
            .block("nsw_end_of_line_marker", EndOfLineMarkerBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f, 6.0f).noOcclusion().lightLevel(state -> 7))
            .item().build().register();

    public static void register() {}
}
