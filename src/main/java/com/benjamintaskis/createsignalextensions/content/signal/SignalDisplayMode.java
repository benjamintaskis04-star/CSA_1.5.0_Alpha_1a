package com.benjamintaskis.createsignalextensions.content.signal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Beta 4 display/demo mode for unlinked NSW signal heads.
 *
 * Operational indications are held for three seconds. Flashing indications retain
 * the normal synchronized one-second ON/OFF cadence while their stage is selected.
 * DARK, OUT_OF_USE and UNLINKED are diagnostic states and are not part of the cycle.
 */
public final class SignalDisplayMode {
    private static final long STAGE_TICKS = 60L;

    private SignalDisplayMode() {}

    public static void apply(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof SingleColourLightSignalBlock single) {
            applySingle(level, pos, state, single);
        } else if (state.getBlock() instanceof DoubleColourLightSignalBlock dual) {
            applyDouble(level, pos, state, dual);
        }
    }

    private static void applySingle(ServerLevel level, BlockPos pos, BlockState state,
                                    SingleColourLightSignalBlock block) {
        boolean flashOn = ((level.getGameTime() / 20L) & 1L) == 0L;
        long stage = level.getGameTime() / STAGE_TICKS;

        SingleColourDisplayedAspect wanted = switch (block.physicalType()) {
            case NORMAL -> switch ((int) Math.floorMod(stage, 4L)) {
                case 0 -> SingleColourDisplayedAspect.STOP;
                case 1 -> SingleColourDisplayedAspect.CAUTION;
                case 2 -> flashOn ? SingleColourDisplayedAspect.MEDIUM_ON : SingleColourDisplayedAspect.MEDIUM_OFF;
                default -> SingleColourDisplayedAspect.PROCEED;
            };
            case DISTANT -> switch ((int) Math.floorMod(stage, 3L)) {
                case 0 -> SingleColourDisplayedAspect.CAUTION;
                case 1 -> flashOn ? SingleColourDisplayedAspect.MEDIUM_ON : SingleColourDisplayedAspect.MEDIUM_OFF;
                default -> SingleColourDisplayedAspect.PROCEED;
            };
            case DIVERGING -> switch ((int) Math.floorMod(stage, 8L)) {
                case 0 -> SingleColourDisplayedAspect.STOP;
                case 1 -> SingleColourDisplayedAspect.CAUTION;
                case 2 -> flashOn ? SingleColourDisplayedAspect.MEDIUM_ON : SingleColourDisplayedAspect.MEDIUM_OFF;
                case 3 -> SingleColourDisplayedAspect.PROCEED;
                case 4 -> SingleColourDisplayedAspect.TURNOUT_LEFT;
                case 5 -> flashOn ? SingleColourDisplayedAspect.MEDIUM_TURNOUT_LEFT_ON
                        : SingleColourDisplayedAspect.MEDIUM_TURNOUT_LEFT_OFF;
                case 6 -> SingleColourDisplayedAspect.TURNOUT_RIGHT;
                default -> flashOn ? SingleColourDisplayedAspect.MEDIUM_TURNOUT_RIGHT_ON
                        : SingleColourDisplayedAspect.MEDIUM_TURNOUT_RIGHT_OFF;
            };
            case CONVERGING -> switch ((int) Math.floorMod(stage, 5L)) {
                case 0 -> SingleColourDisplayedAspect.STOP;
                case 1 -> SingleColourDisplayedAspect.TURNOUT_LEFT;
                case 2 -> flashOn ? SingleColourDisplayedAspect.MEDIUM_TURNOUT_LEFT_ON
                        : SingleColourDisplayedAspect.MEDIUM_TURNOUT_LEFT_OFF;
                case 3 -> SingleColourDisplayedAspect.TURNOUT_RIGHT;
                default -> flashOn ? SingleColourDisplayedAspect.MEDIUM_TURNOUT_RIGHT_ON
                        : SingleColourDisplayedAspect.MEDIUM_TURNOUT_RIGHT_OFF;
            };
            case FIXED_RED -> SingleColourDisplayedAspect.STOP;
            case TWO_ASPECT -> (stage % 2L == 0L) ? SingleColourDisplayedAspect.STOP : SingleColourDisplayedAspect.PROCEED;
        };

        if (state.getValue(SingleColourLightSignalBlock.ASPECT) != wanted)
            level.setBlock(pos, state.setValue(SingleColourLightSignalBlock.ASPECT, wanted), 3);
    }

    private static void applyDouble(ServerLevel level, BlockPos pos, BlockState state,
                                    DoubleColourLightSignalBlock block) {
        boolean flashOn = ((level.getGameTime() / 20L) & 1L) == 0L;
        long stage = level.getGameTime() / STAGE_TICKS;

        DoubleColourDisplayedAspect wanted = switch (block.physicalType()) {
            case NORMAL -> commonDouble((int) Math.floorMod(stage, 5L), flashOn);
            case CONVERGING -> switch ((int) Math.floorMod(stage, 3L)) {
                case 0 -> DoubleColourDisplayedAspect.STOP;
                case 1 -> DoubleColourDisplayedAspect.CAUTION_TURNOUT;
                default -> DoubleColourDisplayedAspect.MEDIUM_TURNOUT;
            };
            case DIVERGING, UNIVERSAL -> switch ((int) Math.floorMod(stage, 7L)) {
                case 0 -> DoubleColourDisplayedAspect.STOP;
                case 1 -> DoubleColourDisplayedAspect.CAUTION;
                case 2 -> DoubleColourDisplayedAspect.MEDIUM;
                case 3 -> flashOn ? DoubleColourDisplayedAspect.PRELIMINARY_MEDIUM_ON
                        : DoubleColourDisplayedAspect.PRELIMINARY_MEDIUM_OFF;
                case 4 -> DoubleColourDisplayedAspect.PROCEED;
                case 5 -> DoubleColourDisplayedAspect.CAUTION_TURNOUT;
                default -> DoubleColourDisplayedAspect.MEDIUM_TURNOUT;
            };
            case FIXED_RED -> DoubleColourDisplayedAspect.STOP;
        };

        if (state.getValue(DoubleColourLightSignalBlock.ASPECT) != wanted)
            level.setBlock(pos, state.setValue(DoubleColourLightSignalBlock.ASPECT, wanted), 3);
    }

    private static DoubleColourDisplayedAspect commonDouble(int stage, boolean flashOn) {
        return switch (stage) {
            case 0 -> DoubleColourDisplayedAspect.STOP;
            case 1 -> DoubleColourDisplayedAspect.CAUTION;
            case 2 -> DoubleColourDisplayedAspect.MEDIUM;
            case 3 -> flashOn ? DoubleColourDisplayedAspect.PRELIMINARY_MEDIUM_ON
                    : DoubleColourDisplayedAspect.PRELIMINARY_MEDIUM_OFF;
            default -> DoubleColourDisplayedAspect.PROCEED;
        };
    }
}
