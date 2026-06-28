package com.cukkoo.whip.mixin;

import com.cukkoo.whip.client.WhipChargeTracker;
import com.cukkoo.whip.item.DarkWhipItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public class HumanoidModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            at = @At("TAIL"))
    private void whip$setupAnim(LivingEntity entity, float limbSwing,
                                 float limbSwingAmount, float ageInTicks,
                                 float netHeadYaw, float headPitch, CallbackInfo ci) {

        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;

        if (!(entity.getMainHandItem().getItem() instanceof DarkWhipItem)) return;

        float chargeProgress = WhipChargeTracker.getChargeProgress();
        WhipChargeTracker.SwingType swing = WhipChargeTracker.getActiveSwing();
        float swingProgress = WhipChargeTracker.getSwingProgress();
        boolean isCharging = WhipChargeTracker.isCharging();

        // Right arm animation
        if (isCharging && chargeProgress > 0) {
            WhipChargeTracker.SwingType chargeType = WhipChargeTracker.getChargeType();
            if (chargeType == WhipChargeTracker.SwingType.OVERHEAD) {
                // Raise arm UP
                model.rightArm.xRot = -chargeProgress * 2.5f;
                model.rightArm.yRot = 0f;
                model.rightArm.zRot = 0f;
            } else if (chargeType == WhipChargeTracker.SwingType.SWEEP) {
                // Move arm RIGHT and slightly FORWARD
                model.rightArm.xRot = -1.0f;
                model.rightArm.yRot = -chargeProgress * 1.0f;
                model.rightArm.zRot = 0.5f;
            }
        } else if (swingProgress > 0) {
            // swingProgress goes from 0.0 (start) to 1.0 (end)
            if (swing == WhipChargeTracker.SwingType.OVERHEAD) {
                // Slam DOWN: from -2.5 (up) to 0.5 (down)
                model.rightArm.xRot = -2.5f + (swingProgress * 3.0f);
                model.rightArm.yRot = 0f;
                model.rightArm.zRot = 0f;
            } else if (swing == WhipChargeTracker.SwingType.SWEEP) {
                // Sweep LEFT: from -1.0 (right) to 1.0 (left)
                model.rightArm.xRot = -1.0f;
                model.rightArm.yRot = -1.0f + (swingProgress * 2.0f);
                model.rightArm.zRot = 0.5f;
            }
        }
    }
}
