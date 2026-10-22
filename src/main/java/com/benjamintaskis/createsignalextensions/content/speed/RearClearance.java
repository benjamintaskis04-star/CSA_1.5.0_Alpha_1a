package com.benjamintaskis.createsignalextensions.content.speed;

import net.minecraft.world.phys.Vec3;

/** A rear must pass the saved plane in the original direction, not just approach it. */
public final class RearClearance {
    private RearClearance() {}
    public static boolean crossed(Vec3 previous, Vec3 rear, Vec3 target, Vec3 forward, double radius) {
        if (previous == null || rear == null || target == null || forward == null || forward.lengthSqr() < 1.0e-8) return false;
        Vec3 motion = rear.subtract(previous);
        if (motion.dot(forward) <= 1.0e-6 || rear.subtract(target).dot(forward) < 0) return false;
        double length = motion.lengthSqr();
        if (length < 1.0e-12) return false;
        double t = Math.max(0, Math.min(1, target.subtract(previous).dot(motion) / length));
        return previous.add(motion.scale(t)).distanceToSqr(target) <= radius * radius;
    }
}
