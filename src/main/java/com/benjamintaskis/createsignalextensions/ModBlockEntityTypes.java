package com.benjamintaskis.createsignalextensions;

import com.benjamintaskis.createsignalextensions.content.route.RouteInterfaceBlockEntity;
import com.benjamintaskis.createsignalextensions.content.theatre.LargeTheatreBoxBlockEntity;
import com.benjamintaskis.createsignalextensions.content.theatre.SmallTheatreBoxBlockEntity;
import com.benjamintaskis.createsignalextensions.content.theatre.ShuntTheatreBlockEntity;
import com.benjamintaskis.createsignalextensions.content.speed.SpeedBoardBlockEntity;
import com.benjamintaskis.createsignalextensions.content.speed.EndOfShuntSignBlockEntity;
import com.benjamintaskis.createsignalextensions.content.signal.SignalPlateBlockEntity;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

public class ModBlockEntityTypes {
    public static final BlockEntityEntry<RouteInterfaceBlockEntity> ROUTE_INTERFACE = CreateSignallingAdditions.REGISTRATE
            .blockEntity("route_interface", RouteInterfaceBlockEntity::new)
            .validBlock(ModBlocks.ROUTE_INTERFACE)
            .register();

    public static final BlockEntityEntry<LargeTheatreBoxBlockEntity> LARGE_THEATRE_BOX = CreateSignallingAdditions.REGISTRATE
            .blockEntity("large_theatre_box", LargeTheatreBoxBlockEntity::new)
            .validBlock(ModBlocks.LARGE_THEATRE_BOX)
            .register();

    public static final BlockEntityEntry<SmallTheatreBoxBlockEntity> SMALL_THEATRE_BOX = CreateSignallingAdditions.REGISTRATE
            .blockEntity("small_theatre_box", SmallTheatreBoxBlockEntity::new)
            .validBlock(ModBlocks.SMALL_THEATRE_BOX)
            .register();

    public static final BlockEntityEntry<ShuntTheatreBlockEntity> SHUNT_THEATRE = CreateSignallingAdditions.REGISTRATE
            .blockEntity("shunt_theatre", ShuntTheatreBlockEntity::new)
            .validBlock(ModBlocks.NSW_GROUND_THEATRE_SHUNT_SIGNAL).validBlock(ModBlocks.NSW_POST_THEATRE_SHUNT_SIGNAL)
            .validBlock(ModBlocks.NSW_GROUND_RUNNING_SHUNT_SIGNAL).validBlock(ModBlocks.NSW_POST_RUNNING_SHUNT_SIGNAL)
            .validBlock(ModBlocks.NSW_HANGING_THEATRE_SHUNT_SIGNAL).validBlock(ModBlocks.NSW_HANGING_RUNNING_SHUNT_SIGNAL).register();

    public static final BlockEntityEntry<SpeedBoardBlockEntity> SPEED_BOARD = CreateSignallingAdditions.REGISTRATE
            .blockEntity("speed_board", SpeedBoardBlockEntity::new)
            .validBlock(ModBlocks.SPEED_BOARD)
            .validBlock(ModBlocks.SPEED_BOARD_DEFAULT_MU)
            .validBlock(ModBlocks.SPEED_BOARD_DEFAULT_HIGH_SPEED)
            .validBlock(ModBlocks.SPEED_BOARD_DEFAULT_MU_HIGH_SPEED)
            .validBlock(ModBlocks.UNRESTRICTED_SPEED_BOARD)
            .register();

    public static final BlockEntityEntry<EndOfShuntSignBlockEntity> END_OF_SHUNT_SIGN = CreateSignallingAdditions.REGISTRATE
            .blockEntity("end_of_shunt_sign", EndOfShuntSignBlockEntity::new)
            .validBlock(ModBlocks.END_OF_SHUNT_SIGN)
            .register();

    public static final BlockEntityEntry<SignalPlateBlockEntity> SIGNAL_PLATE = CreateSignallingAdditions.REGISTRATE
            .blockEntity("signal_plate", SignalPlateBlockEntity::new).validBlock(ModBlocks.SIGNAL_PLATE).register();


    public static void register() {}
}
