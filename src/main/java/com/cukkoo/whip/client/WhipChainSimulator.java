package com.cukkoo.whip.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 40-segment Verlet chain simulated in WORLD SPACE at 20 TPS.
 *
 * Stores both current and previous-tick positions so the renderer
 * can interpolate with partialTick for smooth 60+ FPS visuals.
 *
 * Physics runs ONLY in ClientTickEvent (stable 20 TPS).
 * The renderer NEVER calls tick() -- it only reads positions.
 */
public class WhipChainSimulator {

    public static final int    MAX_SEGMENTS  = 60;
    private static int         activeSegments = 30;
    public static final float  SEG_LEN       = 0.25f;
    public static final float  HANDLE_LEN    = 0.6f;
    private static final float GRAVITY       = 0.15f;
    private static final float DAMPING       = 0.82f;
    private static final int   ITERS         = 50;
    private static final float EPSILON       = 0.0001f;
    private static final float GROUND_OFFSET = 0.1f;

    // Current tick positions (written by physics, read by renderer)
    private static final Vec3[] pos  = new Vec3[MAX_SEGMENTS + 1];
    // Verlet "previous" positions (used internally by physics for velocity)
    private static final Vec3[] prev = new Vec3[MAX_SEGMENTS + 1];
    // Snapshot of positions at the START of each tick (for renderer interpolation)
    private static final Vec3[] renderPrev = new Vec3[MAX_SEGMENTS + 1];

    private static boolean ready = false;

    /** Fallback anchor computed from player position. */
    private static Vec3 anchorWorld = Vec3.ZERO;

    /**
     * Renderer-provided anchor: exact world position of the handle tip,
     * extracted from the BEWLR PoseStack. Updated every render frame.
     */
    private static volatile Vec3 rendererAnchor = null;

    /** Called by the BEWLR to provide the precise handle-tip world position. */
    public static void setRendererAnchor(Vec3 p) {
        rendererAnchor = p;
    }

    /** Called ONLY from ClientTickEvent (Phase.END) -- stable 20 TPS. */
    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) {
            ready = false;
            return;
        }
        
        boolean holdingWhip = player.getMainHandItem().getItem() instanceof com.cukkoo.whip.item.DarkWhipItem;
        if (!holdingWhip) {
            ready = false;
            return;
        }

        int reachLevel = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                com.cukkoo.whip.enchantment.ModEnchantments.REACH.get(), player.getMainHandItem());
        activeSegments = 30 + reachLevel * 10;

        // ---- Compute anchor (handle tip) world position ----
        Vec3 rAnchor = rendererAnchor;
        if (rAnchor != null) {
            anchorWorld = rAnchor;
        } else {
            Vec3 feet = player.position();
            double handY = feet.y + 1.15;
            Vec3 look = player.getLookAngle();
            Vec3 handPos = new Vec3(
                    feet.x + look.x * 0.3,
                    handY,
                    feet.z + look.z * 0.3
            );
            anchorWorld = handPos.add(look.scale(HANDLE_LEN));
        }

        // ---- Initialize on first tick ----
        if (!ready) {
            for (int i = 0; i <= MAX_SEGMENTS; i++) {
                Vec3 p = new Vec3(anchorWorld.x, anchorWorld.y - i * SEG_LEN, anchorWorld.z);
                pos[i]        = p;
                prev[i]       = p;
                renderPrev[i] = p;
            }
            ready = true;
        }

        // ---- Snapshot current positions for renderer interpolation ----
        // The renderer will lerp between renderPrev and pos using partialTick.
        for (int i = 0; i <= activeSegments; i++) {
            renderPrev[i] = pos[i];
        }

        // ---- Pin anchor ----
        prev[0] = pos[0];
        pos[0]  = anchorWorld;

        // ---- Verlet integration ----
        for (int i = 1; i <= activeSegments; i++) {
            Vec3 vel   = pos[i].subtract(prev[i]).scale(DAMPING);
            Vec3 guess = pos[i].add(vel);

            // Gravity: Y+ is UP, subtract to pull DOWN
            guess = new Vec3(guess.x, guess.y - GRAVITY, guess.z);

            // NaN guard
            if (Double.isNaN(guess.x) || Double.isNaN(guess.y) || Double.isNaN(guess.z)) {
                guess = new Vec3(anchorWorld.x, anchorWorld.y - i * SEG_LEN, anchorWorld.z);
            }

            // ---- Swept Collision (Velocity) ----
            net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(
                    prev[i], guess,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, player));
            
            boolean hitGround = false;
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                Vec3 normal = new Vec3(hit.getDirection().step());
                net.minecraft.core.Direction dir = hit.getDirection();
                
                // Anti-Snag Corner Rounding (Pre-Constraint)
                if (dir.getAxis().isHorizontal()) {
                    double hitY = hit.getLocation().y;
                    double blockTop = hit.getBlockPos().getY() + 1.0;
                    net.minecraft.core.BlockPos abovePos = hit.getBlockPos().above();
                    if (blockTop - hitY < 0.4 && level.getBlockState(abovePos).getCollisionShape(level, abovePos).isEmpty()) {
                        // Slide up onto the ledge
                        guess = new Vec3(guess.x, blockTop + 0.05, guess.z);
                        hitGround = true; // Act as if we landed
                    } else {
                        guess = hit.getLocation().add(normal.scale(0.05));
                    }
                } else {
                    guess = hit.getLocation().add(normal.scale(0.05));
                    if (dir == net.minecraft.core.Direction.UP) hitGround = true;
                }
            }

            Vec3 oldPos = pos[i];
            pos[i]  = guess;

            // Kill vertical velocity on ground contact, and add horizontal ground friction (drag)
            if (hitGround) {
                double lerpX = net.minecraft.util.Mth.lerp(0.3, oldPos.x, guess.x);
                double lerpZ = net.minecraft.util.Mth.lerp(0.3, oldPos.z, guess.z);
                prev[i] = new Vec3(lerpX, guess.y, lerpZ);
            } else {
                prev[i] = oldPos;
            }
        }

        // ---- Swing Physics (Physical Whip Lash) ----
        if (WhipChargeTracker.getActiveSwing() != WhipChargeTracker.SwingType.NONE) {
            int ticks = WhipChargeTracker.getSwingTicks();
            if (ticks == WhipChargeTracker.OVERHEAD_SWING_TICKS - 1) {
                // First tick of overhead swing: Apply massive forward physical impulse
                Vec3 look = player.getLookAngle();
                for(int i = 1; i <= activeSegments; i++) {
                    double factor = (double)i / activeSegments; // 0 to 1
                    pos[i] = pos[i].add(look.scale(3.0 * factor).add(0, 1.0 * factor, 0));
                }
            } else if (ticks == WhipChargeTracker.SWEEP_SWING_TICKS - 1) {
                // First tick of sweep swing: Apply horizontal sweeping impulse
                Vec3 look = player.getLookAngle();
                Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
                for(int i = 1; i <= activeSegments; i++) {
                    double factor = (double)i / activeSegments;
                    pos[i] = pos[i].add(look.scale(2.0 * factor).add(right.scale(4.0 * factor)));
                }
            }
        }

        // ---- Forward Snap on Swing ----
        float swingProgress = WhipChargeTracker.getSwingProgress();
        if (swingProgress > 0) {
            Vec3 look = player.getLookAngle().normalize();
            for (int i = 1; i <= activeSegments; i++) {
                // Apply strong forward momentum during swing, pushing segments to target
                pos[i] = pos[i].add(look.scale(swingProgress * 1.5));
            }
        }

        // ---- Distance constraints ----
        for (int iter = 0; iter < ITERS; iter++) {
            pos[0] = anchorWorld;

            for (int i = 0; i < activeSegments; i++) {
                Vec3 delta = pos[i + 1].subtract(pos[i]);
                double dist = delta.length();

                if (dist < EPSILON) {
                    pos[i + 1] = pos[i].add(0, -SEG_LEN, 0);
                    continue;
                }

                // Snap logic for extremely fast movements / teleports
                if (dist > SEG_LEN * 4 && iter == 0) {
                    pos[i + 1] = pos[i].add(delta.normalize().scale(SEG_LEN));
                    continue;
                }

                double error = dist - SEG_LEN;
                double corrScale = error * 0.5 / dist;

                if (Double.isNaN(corrScale) || Double.isInfinite(corrScale)) {
                    pos[i + 1] = pos[i].add(0, -SEG_LEN, 0);
                    continue;
                }

                Vec3 corr = delta.scale(corrScale);

                if (i > 0) {
                    pos[i] = pos[i].add(corr);
                }
                pos[i + 1] = pos[i + 1].subtract(corr);
            }
            // AABB Push-out enforced DURING constraint solving!
            // We run this every few iterations to keep it fast, but often enough to prevent stretching.
            if (iter % 3 == 0 || iter == ITERS - 1) {
                for (int i = 1; i <= activeSegments; i++) {
                    int gx = net.minecraft.util.Mth.floor(pos[i].x);
                    int gy = net.minecraft.util.Mth.floor(pos[i].y);
                    int gz = net.minecraft.util.Mth.floor(pos[i].z);
                    net.minecraft.core.BlockPos bp = new net.minecraft.core.BlockPos(gx, gy, gz);
                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(bp);
                    
                    if (!state.isAir() && !state.getCollisionShape(level, bp).isEmpty()) {
                        double dx0 = pos[i].x - gx; 
                        double dx1 = (gx + 1) - pos[i].x;
                        double dy0 = pos[i].y - gy;
                        double dy1 = (gy + 1) - pos[i].y;
                        double dz0 = pos[i].z - gz;
                        double dz1 = (gz + 1) - pos[i].z;
                        
                        double biasedDy1 = dy1 - 0.4; 
                        double min = Math.min(Math.min(Math.min(dx0, dx1), Math.min(dy0, biasedDy1)), Math.min(dz0, dz1));
                        
                        if (min == biasedDy1) {
                            pos[i] = new Vec3(pos[i].x, gy + 1.05, pos[i].z); // Just push it to the top face
                            if (iter == ITERS - 1) prev[i] = new Vec3(prev[i].x, pos[i].y, prev[i].z); // Ground drag on last iter
                        } else if (min == dx0) {
                            pos[i] = new Vec3(gx - 0.05, pos[i].y, pos[i].z);
                        } else if (min == dx1) {
                            pos[i] = new Vec3(gx + 1.05, pos[i].y, pos[i].z);
                        } else if (min == dz0) {
                            pos[i] = new Vec3(pos[i].x, pos[i].y, gz - 0.05);
                        } else if (min == dz1) {
                            pos[i] = new Vec3(pos[i].x, pos[i].y, gz + 1.05);
                        } else {
                            pos[i] = new Vec3(pos[i].x, gy - 0.05, pos[i].z);
                        }
                    }
                }
            }
        }

        pos[0] = anchorWorld;
    }

    public static void reset() {
        ready = false;
        rendererAnchor = null;
    }

    // ---- Accessors ----
    public static int       segmentCount()       { return activeSegments; }
    public static Vec3[]    positions()          { return pos; }
    public static Vec3[]    renderPrevPositions() { return renderPrev; }
    public static boolean   isReady()            { return ready; }
    public static Vec3      anchorWorldPos()     { return anchorWorld; }
}
