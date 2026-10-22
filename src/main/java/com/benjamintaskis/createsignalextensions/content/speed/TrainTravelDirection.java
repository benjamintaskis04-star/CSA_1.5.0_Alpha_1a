package com.benjamintaskis.createsignalextensions.content.speed;

import com.simibubi.create.content.trains.entity.Train;

/** The active leading end must remain stable while automatic trains stop. */
public final class TrainTravelDirection {
    private TrainTravelDirection() {}

    public static boolean reversed(Train train) {
        if (Math.abs(train.speed) > 1.0e-6) return train.speed < 0;
        if (train.navigation != null && train.navigation.isActive())
            return train.navigation.destinationBehindTrain;
        return train.targetSpeed < 0;
    }
}
