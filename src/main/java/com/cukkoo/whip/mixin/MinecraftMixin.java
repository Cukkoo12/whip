package com.cukkoo.whip.mixin;

import com.cukkoo.whip.item.DarkWhipItem;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    /**
     * Prevent vanilla attack when holding the Dark Whip.
     * The whip uses charge mechanics instead of vanilla attacking.
     */
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void whip$startAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player != null
                && mc.player.getMainHandItem().getItem() instanceof DarkWhipItem) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Prevent block breaking while holding the Dark Whip.
     */
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void whip$continueAttack(boolean attacking, CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player != null
                && mc.player.getMainHandItem().getItem() instanceof DarkWhipItem) {
            ci.cancel();
        }
    }
}
