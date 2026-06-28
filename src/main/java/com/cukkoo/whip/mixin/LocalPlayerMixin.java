package com.cukkoo.whip.mixin;

import com.cukkoo.whip.client.WhipChargeTracker;
import com.cukkoo.whip.item.DarkWhipItem;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ensures the player's movement speed is not reduced while charging the whip.
 * Since whip charging uses the attack key (not the use key), speed reduction
 * shouldn't naturally occur, but this mixin provides a safety net.
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void whip$aiStep(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;

        if (!(self.getMainHandItem().getItem() instanceof DarkWhipItem)) return;
        if (!WhipChargeTracker.isCharging()) return;

        // Ensure movement speed stays at base value while charging
        float baseSpeed = (float) self.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (self.getSpeed() < baseSpeed * 0.95f) {
            self.setSpeed(baseSpeed);
        }
    }
}
