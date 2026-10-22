package com.benjamintaskis.createsignalextensions.content.speed;

/** Units match Create: speed in blocks/tick, acceleration in blocks/tick². */
public final class SpeedBrakingEnvelope {
    private SpeedBrakingEnvelope() {}
    public static double lookAheadBlocks(double speed, double trainAcceleration) {
        double acceleration = Math.max(1.0e-6, Math.min(1.5 / 400.0, trainAcceleration) * .9);
        return Math.max(640, Math.min(8192, Math.ceil(speed * speed / (2 * acceleration) + 16)));
    }
    public static double permittedKmh(double targetKmh, double distance, double speed, double trainAcceleration) {
        double acceleration = Math.max(0, Math.min(1.5 / 400.0, trainAcceleration)) * .9;
        double target = targetKmh / 72.0;
        double margin = 3.0 + Math.max(3.0, Math.abs(speed) * 2.0);
        return Math.sqrt(target * target + 2 * acceleration * Math.max(0, distance - margin)) * 72.0;
    }
}
