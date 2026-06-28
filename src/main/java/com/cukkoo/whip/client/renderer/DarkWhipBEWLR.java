package com.cukkoo.whip.client.renderer;

import com.cukkoo.whip.client.WhipChainSimulator;
import com.cukkoo.whip.client.WhipChargeTracker;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * BEWLR -- renders the rigid diagonal handle in the item rendering pipeline.
 * Extracts the handle tip position for the level tail renderer.
 *
 * The PoseStack provided by Minecraft is in VIEW SPACE (camera-relative).
 * To get world-space position of the handle tip, we transform the tip through
 * the PoseStack matrix, then invert the view matrix to get back to world space.
 * This works identically for both first-person and third-person views.
 */
public class DarkWhipBEWLR extends BlockEntityWithoutLevelRenderer {

    private static final float HANDLE_THICK = 0.07f;

    private static final RenderType HANDLE_RT = RenderType.create(
            "whip_handle", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
            512, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setTransparencyState(new RenderStateShard.TransparencyStateShard(
                            "whip_handle_alpha",
                            () -> { com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                                    com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc(); },
                            () -> com.mojang.blaze3d.systems.RenderSystem.disableBlend()))
                    .setCullState(new RenderStateShard.CullStateShard(false))
                    .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, true))
                    .createCompositeState(false)
    );

    public DarkWhipBEWLR(BlockEntityRenderDispatcher d, EntityModelSet m) { super(d, m); }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx,
                             PoseStack ps, MultiBufferSource buf,
                             int light, int overlay) {

        ps.pushPose();

        Minecraft mc = Minecraft.getInstance();
        boolean isHand = (ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                       || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                       || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                       || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND);

        // Animation is handled by DarkWhipItem.applyForgeHandTransform()
        // The PoseStack already has the hand animation baked in

        // ---- Draw the handle ----
        Matrix4f pose = ps.last().pose();
        VertexConsumer vc = buf.getBuffer(HANDLE_RT);

        Vec3 base = new Vec3(0.1, 0.1, 0.5);
        Vec3 tip  = new Vec3(0.9, 0.9, 0.5);

        Vec3 dir = tip.subtract(base).normalize();
        double totalLen = tip.distanceTo(base);
        
        // Pommel
        Vec3 p1 = base;
        Vec3 p2 = base.add(dir.scale(totalLen * 0.15));
        renderBox(pose, vc, p1, p2, 0.12f, 0.12f, 0.2f, 0.2f, 0.25f, 1.0f);
        
        // Grip (Leather-like dark crimson)
        Vec3 p3 = base.add(dir.scale(totalLen * 0.85));
        renderBox(pose, vc, p2, p3, 0.07f, 0.07f, 0.15f, 0.05f, 0.05f, 1.0f);
        
        // Guard / Emitter
        Vec3 p4 = tip;
        renderBox(pose, vc, p3, p4, 0.14f, 0.14f, 0.1f, 0.1f, 0.15f, 1.0f);

        // ---- Extract handle-tip world position for the tail renderer ----
        if (isHand && mc.player != null) {
            Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();

            // Transform tip point (0.9, 0.9, 0.5) through the full PoseStack matrix
            // This gives us the tip position in VIEW SPACE (camera-relative)
            Matrix4f m = ps.last().pose();
            float vx = m.m00() * 0.9f + m.m10() * 0.9f + m.m20() * 0.5f + m.m30();
            float vy = m.m01() * 0.9f + m.m11() * 0.9f + m.m21() * 0.5f + m.m31();
            float vz = m.m02() * 0.9f + m.m12() * 0.9f + m.m22() * 0.5f + m.m32();

            // Invert the view matrix to go from view space -> world space
            // WhipTailRenderer.currentViewMatrix is captured at AFTER_SKY stage
            Vector4f viewPos = new Vector4f(vx, vy, vz, 1.0f);
            Matrix4f invView = new Matrix4f(WhipTailRenderer.currentViewMatrix).invert();
            invView.transform(viewPos);

            Vec3 tipWorld = new Vec3(
                    viewPos.x() + camPos.x,
                    viewPos.y() + camPos.y,
                    viewPos.z() + camPos.z
            );

            WhipTailRenderer.currentAnchorWorldPos = tipWorld;
            WhipChainSimulator.setRendererAnchor(tipWorld);
        }

        ps.popPose();
    }

    // ================================================================
    //  Box geometry
    // ================================================================
    private static void renderBox(Matrix4f pose, VertexConsumer vc,
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

        Vec3 a1 = a.add(hw).add(hh),       a2 = a.add(hw).subtract(hh),
             a3 = a.subtract(hw).add(hh),  a4 = a.subtract(hw).subtract(hh);
        Vec3 b1 = b.add(hw).add(hh),       b2 = b.add(hw).subtract(hh),
             b3 = b.subtract(hw).add(hh),  b4 = b.subtract(hw).subtract(hh);

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
