package com.cukkoo.whip.client;

public class WhipChargeTracker {

    public enum SwingType {
        NONE,
        OVERHEAD,
        SWEEP
    }

    private static boolean isCharging = false;
    private static SwingType chargeType = SwingType.NONE;
    private static int chargeTicks = 0;
    private static int swingTicks = 0;
    private static SwingType activeSwing = SwingType.NONE;

    public static final int MAX_CHARGE_TICKS = 40; // 2 seconds at 20tps
    public static final int OVERHEAD_SWING_TICKS = 10;
    public static final int SWEEP_SWING_TICKS = 8;

    // Grapple state
    private static boolean isGrappling = false;
    private static int grappleTicks = 0;
    private static double grappleTargetX, grappleTargetY, grappleTargetZ;

    public static void startCharge(SwingType type) {
        isCharging = true;
        chargeType = type;
        chargeTicks = 0;
        swingTicks = 0;
        activeSwing = SwingType.NONE;
    }

    public static void tickCharge() {
        if (isCharging && chargeTicks < MAX_CHARGE_TICKS) {
            chargeTicks++;
        }
    }

    public static void releaseCharge() {
        if (!isCharging) return;
        isCharging = false;
        activeSwing = chargeType;
        if (activeSwing == SwingType.OVERHEAD) {
            swingTicks = OVERHEAD_SWING_TICKS;
        } else {
            swingTicks = SWEEP_SWING_TICKS;
        }
    }

    public static void cancelCharge() {
        isCharging = false;
        chargeType = SwingType.NONE;
        chargeTicks = 0;
        swingTicks = 0;
        activeSwing = SwingType.NONE;
    }

    public static void startGrapple(double x, double y, double z) {
        isGrappling = true;
        grappleTicks = 15;
        grappleTargetX = x;
        grappleTargetY = y;
        grappleTargetZ = z;
    }

    public static void tick() {
        if (swingTicks > 0) {
            swingTicks--;
            if (swingTicks == 0) {
                activeSwing = SwingType.NONE;
                chargeTicks = 0;
            }
        }
        if (grappleTicks > 0) {
            grappleTicks--;
            if (grappleTicks == 0) {
                isGrappling = false;
            }
        }
        if (isCharging) {
            tickCharge();
        }
    }

    // ---- Getters ----

    public static boolean isCharging() {
        return isCharging;
    }

    public static SwingType getChargeType() {
        return chargeType;
    }

    public static int getChargeTicks() {
        return chargeTicks;
    }

    public static int getSwingTicks() {
        return swingTicks;
    }

    public static SwingType getActiveSwing() {
        return activeSwing;
    }

    public static float getChargeProgress() {
        return Math.min((float) chargeTicks / MAX_CHARGE_TICKS, 1f);
    }

    public static float getSwingProgress() {
        if (activeSwing == SwingType.OVERHEAD && OVERHEAD_SWING_TICKS > 0) {
            return 1f - (float) swingTicks / OVERHEAD_SWING_TICKS;
        }
        if (activeSwing == SwingType.SWEEP && SWEEP_SWING_TICKS > 0) {
            return 1f - (float) swingTicks / SWEEP_SWING_TICKS;
        }
        return 0f;
    }

    public static boolean isGrappling() {
        return isGrappling;
    }

    public static int getGrappleTicks() {
        return grappleTicks;
    }

    public static double getGrappleTargetX() { return grappleTargetX; }
    public static double getGrappleTargetY() { return grappleTargetY; }
    public static double getGrappleTargetZ() { return grappleTargetZ; }
}
