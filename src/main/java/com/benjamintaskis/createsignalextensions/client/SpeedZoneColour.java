package com.benjamintaskis.createsignalextensions.client;

/** Deterministic, continuous speed-colour palette for CSA visualisation. */
public final class SpeedZoneColour {
    private SpeedZoneColour() {}
    private static final int[] SPEEDS = {30, 65, 100, 150, 175, 200};
    private static final int[][] RGB = {
            {0, 102, 255}, {0, 205, 190}, {0, 205, 65},
            {255, 220, 0}, {255, 125, 0}, {255, 45, 45}
    };
    public static int rgb(int kmh) {
        if (kmh <= SPEEDS[0]) return pack(RGB[0]);
        if (kmh >= SPEEDS[SPEEDS.length - 1]) return pack(RGB[RGB.length - 1]);
        for (int i = 0; i < SPEEDS.length - 1; i++) {
            if (kmh <= SPEEDS[i + 1]) {
                float t = (kmh - SPEEDS[i]) / (float)(SPEEDS[i + 1] - SPEEDS[i]);
                int r = Math.round(RGB[i][0] + t * (RGB[i + 1][0] - RGB[i][0]));
                int g = Math.round(RGB[i][1] + t * (RGB[i + 1][1] - RGB[i][1]));
                int b = Math.round(RGB[i][2] + t * (RGB[i + 1][2] - RGB[i][2]));
                return (r << 16) | (g << 8) | b;
            }
        }
        return pack(RGB[RGB.length - 1]);
    }
    private static int pack(int[] c) { return (c[0] << 16) | (c[1] << 8) | c[2]; }
}
