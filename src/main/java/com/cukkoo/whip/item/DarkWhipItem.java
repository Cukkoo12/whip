package com.cukkoo.whip.item;

import com.cukkoo.whip.client.WhipChargeTracker;
import com.cukkoo.whip.client.renderer.DarkWhipBEWLR;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

public class DarkWhipItem extends Item {

    public DarkWhipItem() {
        super(new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.UNCOMMON));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private DarkWhipBEWLR renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    Minecraft mc = Minecraft.getInstance();
                    renderer = new DarkWhipBEWLR(
                            mc.getBlockEntityRenderDispatcher(),
                            mc.getEntityModels());
                }
                return renderer;
            }

            @Override
            public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player,
                    HumanoidArm arm, ItemStack itemInHand,
                    float partialTick, float equipProcess, float swingProcess) {

                float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;

                // Base hand position (replicates vanilla default, without any swing)
                poseStack.translate(side * 0.56F, -0.52F + equipProcess * -0.6F, -0.72F);

                // In this coordinate space:
                //   +X = screen right (for right hand)
                //   +Y = screen up
                //   -Z = into screen (forward)

                float charge = WhipChargeTracker.getChargeProgress();
                float swing = WhipChargeTracker.getSwingProgress();
                WhipChargeTracker.SwingType chargeType = WhipChargeTracker.getChargeType();
                WhipChargeTracker.SwingType swingType = WhipChargeTracker.getActiveSwing();

                if (WhipChargeTracker.isCharging() && charge > 0) {
                    if (chargeType == WhipChargeTracker.SwingType.OVERHEAD) {
                        // ISTENEN: Kolun YUKARI kalkmasi
                        poseStack.translate(0, charge * 0.35F, 0);
                        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(charge * 40F));
                    } else if (chargeType == WhipChargeTracker.SwingType.SWEEP) {
                        // Move arm LEFT: -X, rotate LEFT: +Y, tilt top LEFT: +Z
                        poseStack.translate(-side * charge * 0.4F, 0, 0);
                        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(side * charge * 40F));
                        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(side * charge * 20F));
                    }
                } else if (swing > 0) {
                    float s = (float) Math.sin(swing * Math.PI);
                    if (swingType == WhipChargeTracker.SwingType.OVERHEAD) {
                        // ISTENEN: Kolun ASAGI vurmasi
                        poseStack.translate(0, -s * 0.5F, -s * 0.4F);
                        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-s * 80F));
                    } else if (swingType == WhipChargeTracker.SwingType.SWEEP) {
                        // Sweep RIGHT: +X, rotate RIGHT: -Y, tilt top RIGHT: -Z
                        poseStack.translate(side * s * 0.6F, 0, -s * 0.2F);
                        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-side * s * 80F));
                        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-side * s * 30F));
                    }
                }

                return true; // Skip ALL vanilla hand transforms (including the broken swing)
            }
        });
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 0;
    }
}
