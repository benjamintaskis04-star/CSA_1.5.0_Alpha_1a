package com.benjamintaskis.createsignalextensions.content.speed;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import com.simibubi.create.content.trains.schedule.Schedule;
import net.createmod.catnip.data.Pair;

/** Registers CSA additions to Create's schedule instruction list. */
public final class ModScheduleInstructions {
    private ModScheduleInstructions() {}

    public static void register() {
        Schedule.INSTRUCTION_TYPES.add(Pair.of(
                CreateSignallingAdditions.id("set_speed_category"),
                SetTrainCategoryInstruction::new));
        Schedule.INSTRUCTION_TYPES.add(Pair.of(
                CreateSignallingAdditions.id("set_shunt_mode"),
                SetShuntModeInstruction::new));
    }
}
