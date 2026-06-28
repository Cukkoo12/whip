package com.cukkoo.whip.client.renderer;

import com.cukkoo.whip.WhipMod;
import com.cukkoo.whip.client.WhipChainSimulator;
import com.cukkoo.whip.client.WhipChargeTracker;
import com.cukkoo.whip.item.DarkWhipItem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Renders the 40-segment Verlet tail in TRUE WORLD SPACE via
 * RenderLevelStageEvent (AFTER_TRANSLUCENT_BLOCKS).
 *
 * Uses the event's provided PoseStack (DO NOT create a new one).
 * Vertices are positioned at (chain[i] - cameraPos) so they render
 * in correct world space through the level's projection pipeline.
 *
 * Interpolates between previous-tick and current-tick positions
 * using partialTick for smooth 60+ FPS rendering while physics
 * stays at stable 20 TPS.
 */
@Mod.EventBusSubscriber(modid = WhipMod.MOD_ID, value = Dist.CLIENT)
public class WhipTailRenderer {

    /* ---- Chain taper ---- */
    private static final float TAPER_BASE = 0.11f;
    private static final float TAPER_TIP  = 0.01f;

    /* ---- RenderType: position+colour, alpha blend, no cull ---- */
    private static final RenderType TAIL_RT = RenderType.create(
            "whip_tail", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
            8192, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setCullState(new RenderStateShard.CullStateShard(false))
                    .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, true))
                    .createCompositeState(false)
    );

    /**
     * The exact world position of the handle tip, set by DarkWhipBEWLR
     * every render frame in third person.
     */
    public static volatile Vec3 currentAnchorWorldPos = null;
    public static Matrix4f currentViewMatrix = new Matrix4f();

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            currentViewMatrix.set(event.getPoseStack().last().pose());
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!WhipChainSimulator.isReady()) return;
        if (!(mc.player.getMainHandItem().getItem() instanceof DarkWhipItem)) return;

        Vec3[] currPos = WhipChainSimulator.positions();
        Vec3[] prevPos = WhipChainSimulator.renderPrevPositions();
        int n = WhipChainSimulator.segmentCount();

        // ---- Camera position ----
        Vec3 cam = event.getCamera().getPosition();
        float pt = event.getPartialTick();

        // ---- Use the event's PoseStack as-is (already has camera rotation) ----
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        Matrix4f pose = ps.last().pose();

        // ---- Get buffer ----
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer vc = bufferSource.getBuffer(TAIL_RT);

        // ---- Read attack state ----
        float charge = WhipChargeTracker.getChargeProgress();
        WhipChargeTracker.SwingType swing = WhipChargeTracker.getActiveSwing();
        float swingP = WhipChargeTracker.getSwingProgress();

        int gradStart = (int)(n * 0.85f);

        // ---- Calculate anchor (handle tip) position ----
        Vec3 anchor = currentAnchorWorldPos;
        if (anchor == null) {
            // Fallback using player entity body yaw
            float bodyYaw = Mth.lerp(pt, mc.player.yBodyRotO, mc.player.yBodyRot);
            float yawRad = bodyYaw * (float)Math.PI / 180F;
            double d3 = Math.sin(yawRad);
            double d4 = Math.cos(yawRad);
            double handX = mc.player.getX() - d4 * 0.35 - d3 * 0.5;
            double handY = mc.player.getY() + mc.player.getEyeHeight() - 0.45;
            double handZ = mc.player.getZ() - d3 * 0.35 + d4 * 0.5;
            anchor = new Vec3(handX, handY, handZ);
        }
        Vec3 physicsAnchor = lerpVec3(prevPos[0], currPos[0], pt);
        Vec3 anchorOffset = anchor.subtract(physicsAnchor);

        Vec3[] finalPoints = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            Vec3 pWorld = lerpVec3(prevPos[i], currPos[i], pt);
            
            // Anchor Blending: Smoothly blend the high-framerate handle position into the 20-TPS physics chain
            if (i < 8) {
                float weight = 1.0f - (i / 8.0f);
                pWorld = pWorld.add(anchorOffset.scale(weight));
            }
            
            if (pWorld == null || Double.isNaN(pWorld.x)) continue;
            
            float t = (float)i / n;
            if (swing == WhipChargeTracker.SwingType.OVERHEAD) {
                Vec3 snap = mc.player.getLookAngle().scale(swingP * 4.5f * t);
                Vec3 lift = new Vec3(0, (1f - swingP) * 3.5f * t, 0);
                pWorld = pWorld.add(snap).add(lift);
            }
            if (swing == WhipChargeTracker.SwingType.SWEEP) {
                float ang = (float)Math.toRadians(-70 + 140 * swingP);
                Vec3 side = new Vec3(Math.sin(ang), 0, Math.cos(ang)).scale(2.8f * t);
                pWorld = pWorld.add(side);
            }
            finalPoints[i] = pWorld;
        }

        Vec3[][] crossSections = new Vec3[n + 1][4];
        Vec3 prevRight = null;
        for (int i = 0; i <= n; i++) {
            if (finalPoints[i] == null) continue;
            Vec3 pRel = finalPoints[i].subtract(cam);
            Vec3 dir;
            if (i == 0 && finalPoints[1] != null) dir = finalPoints[1].subtract(finalPoints[0]);
            else if (i == n && finalPoints[n-1] != null) dir = finalPoints[n].subtract(finalPoints[n-1]);
            else if (finalPoints[i-1] != null && finalPoints[i+1] != null) dir = finalPoints[i+1].subtract(finalPoints[i-1]);
            else dir = new Vec3(0, -1, 0);
            
            if (dir.lengthSqr() < 0.0001) dir = new Vec3(0, -1, 0);
            dir = dir.normalize();
            
            Vec3 right;
            if (prevRight != null) {
                // Orthogonalize previous right vector against new direction to prevent twisting
                right = prevRight.subtract(dir.scale(prevRight.dot(dir))).normalize();
                if (Double.isNaN(right.x) || right.lengthSqr() < 0.001) {
                    right = Math.abs(dir.y) > 0.999 ? dir.cross(new Vec3(0, 0, -1)).normalize() : dir.cross(new Vec3(0, 1, 0)).normalize();
                }
            } else {
                right = Math.abs(dir.y) > 0.999 ? dir.cross(new Vec3(0, 0, -1)).normalize() : dir.cross(new Vec3(0, 1, 0)).normalize();
            }
            prevRight = right;
            Vec3 up = right.cross(dir).normalize();
            
            float thick = Mth.lerp((float)i / n, TAPER_BASE, TAPER_TIP);
            Vec3 hw = right.scale(thick * 0.5);
            Vec3 hh = up.scale(thick * 0.5);
            
            crossSections[i][0] = pRel.add(hw).add(hh);
            crossSections[i][1] = pRel.add(hw).subtract(hh);
            crossSections[i][2] = pRel.subtract(hw).subtract(hh);
            crossSections[i][3] = pRel.subtract(hw).add(hh);
        }

        for (int i = 0; i < n; i++) {
            if (crossSections[i][0] == null || crossSections[i+1][0] == null) continue;
            
            float r, g, bl;
            if (i < gradStart) {
                r = 0.05f; g = 0.05f; bl = 0.05f;
            } else {
                float gt = (float)(i - gradStart) / (n - gradStart);
                r = 0.05f + gt * 0.35f;
                g = 0.05f * (1f - gt);
                bl = 0.05f * (1f - gt);
            }
            
            Vec3[] a = crossSections[i];
            Vec3[] b = crossSections[i+1];
            
            // Top, Bottom, Left, Right faces of the tube segment
            emitQuad(pose, vc, b[0], a[0], a[1], b[1], r, g, bl, 1.0f);
            emitQuad(pose, vc, b[1], a[1], a[2], b[2], r*0.8f, g*0.8f, bl*0.8f, 1.0f);
            emitQuad(pose, vc, b[2], a[2], a[3], b[3], r*0.5f, g*0.5f, bl*0.5f, 1.0f);
            emitQuad(pose, vc, b[3], a[3], a[0], b[0], r*0.7f, g*0.7f, bl*0.7f, 1.0f);
        }

        // Flush the tail geometry
        bufferSource.endBatch(TAIL_RT);
        ps.popPose();
    }

    /** Linearly interpolate between two Vec3 positions using partialTick. */
    private static Vec3 lerpVec3(Vec3 from, Vec3 to, float t) {
        if (from == null || to == null) return to != null ? to : Vec3.ZERO;
        return new Vec3(
                Mth.lerp(t, from.x, to.x),
                Mth.lerp(t, from.y, to.y),
                Mth.lerp(t, from.z, to.z)
        );
    }

    // ================================================================
    //  Oriented box between two camera-relative points
    // ================================================================
    private static void renderSegment(Matrix4f pose, VertexConsumer vc,
                                      Vec3 a, Vec3 b, float w, float h,
                                      float r, float g, float bl, float alpha) {
        Vec3 dir = b.subtract(a);
        double len = dir.length();
        if (len < 0.0005) return;
        dir = dir.normalize();

        Vec3 right;
        if (Math.abs(dir.y) > 0.999)
            right = dir.cross(new Vec3(0, 0, -1)).normalize();
        else
            right = dir.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = right.cross(dir).normalize();

        Vec3 hw = right.scale(w * 0.5);
        Vec3 hh = up.scale(h * 0.5);

        // Extend segments slightly to overlap joints and prevent gaps
        double overlap = 0.05;
        Vec3 aExt = a.subtract(dir.scale(overlap));
        Vec3 bExt = b.add(dir.scale(overlap));

        Vec3 a1 = aExt.add(hw).add(hh),       a2 = aExt.add(hw).subtract(hh),
             a3 = aExt.subtract(hw).add(hh),  a4 = aExt.subtract(hw).subtract(hh);
        Vec3 b1 = bExt.add(hw).add(hh),       b2 = bExt.add(hw).subtract(hh),
             b3 = bExt.subtract(hw).add(hh),  b4 = bExt.subtract(hw).subtract(hh);

        emitQuad(pose, vc, b1,b3,b4,b2, r,g,bl,alpha);
        emitQuad(pose, vc, a3,a1,a2,a4, r*0.7f,g*0.7f,bl*0.7f,alpha);
        emitQuad(pose, vc, b1,a1,a3,b3, r*1.1f,g*1.1f,bl*1.1f,alpha);
        emitQuad(pose, vc, a2,b2,b4,a4, r*0.5f,g*0.5f,bl*0.5f,alpha);
        emitQuad(pose, vc, b1,b2,a2,a1, r,g,bl,alpha);
        emitQuad(pose, vc, b4,b3,a3,a4, r*0.8f,g*0.8f,bl*0.8f,alpha);
    }

    private static void emitQuad(Matrix4f pose, VertexConsumer vc,
                                 Vec3 v1, Vec3 v2, Vec3 v3, Vec3 v4,
                                 float r, float g, float b, float a) {
        vc.vertex(pose,(float)v1.x,(float)v1.y,(float)v1.z).color(r,g,b,a).endVertex();
        vc.vertex(pose,(float)v2.x,(float)v2.y,(float)v2.z).color(r,g,b,a).endVertex();
        vc.vertex(pose,(float)v3.x,(float)v3.y,(float)v3.z).color(r,g,b,a).endVertex();
        vc.vertex(pose,(float)v4.x,(float)v4.y,(float)v4.z).color(r,g,b,a).endVertex();
    }
}
