package com.cukkoo.whip.client;

import com.cukkoo.whip.WhipMod;
import com.cukkoo.whip.item.DarkWhipItem;
import com.cukkoo.whip.network.ModNetwork;
import com.cukkoo.whip.network.WhipAttackPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WhipMod.MOD_ID, value = Dist.CLIENT)
public class ClientEvents {

    private static boolean wasAttackDown = false;
    private static boolean wasUseDown = false;

    /**
     * Intercept attack/use key at the INPUT level to prevent Minecraft
     * from ever calling player.swing() or startAttack().
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.player.getMainHandItem().getItem() instanceof DarkWhipItem) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /**
     * Third-person arm animation: override the player model's arm rotation
     * AFTER setupAnim() has run (using Post event).
     * 
     * We cancel the vanilla attack animation by zeroing attackAnim/oAttackAnim
     * in Pre, and apply custom arm rotation in Post by modifying the model parts
     * (which affects the NEXT frame's rendering through the cached model state).
     */
    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity().getMainHandItem().getItem() instanceof DarkWhipItem)) return;

        // Kill vanilla attack animation for third person
        event.getEntity().attackAnim = 0;
        event.getEntity().oAttackAnim = 0;
        event.getEntity().swingTime = 0;
        event.getEntity().swinging = false;

        // Set the model's attackTime to 0 so setupAnim() skips vanilla swing
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        model.attackTime = 0;
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (!(event.getEntity().getMainHandItem().getItem() instanceof DarkWhipItem)) return;

        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();

        float charge = WhipChargeTracker.getChargeProgress();
        float swing = WhipChargeTracker.getSwingProgress();
        WhipChargeTracker.SwingType chargeType = WhipChargeTracker.getChargeType();
        WhipChargeTracker.SwingType swingType = WhipChargeTracker.getActiveSwing();

        // 3rd person arm animations are now handled correctly in HumanoidModelMixin.java
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Tick whip state (decrements swing timer, etc.)
        WhipChargeTracker.tick();

        ItemStack mainHand = mc.player.getMainHandItem();
        boolean holdingWhip = mainHand.getItem() instanceof DarkWhipItem;

        if (holdingWhip) {
            // Extra safety: force-kill any vanilla swing
            mc.player.swingTime = 0;
            mc.player.swinging = false;
            mc.player.attackAnim = 0;
            mc.player.oAttackAnim = 0;

            boolean attackDown = mc.options.keyAttack.isDown();
            boolean useDown = mc.options.keyUse.isDown();

            // Left click = Charge + Overhead
            if (attackDown && !wasAttackDown && !useDown) {
                WhipChargeTracker.startCharge(WhipChargeTracker.SwingType.OVERHEAD);
            } else if (!attackDown && wasAttackDown && WhipChargeTracker.getChargeType() == WhipChargeTracker.SwingType.OVERHEAD) {
                int charge = WhipChargeTracker.getChargeTicks();
                WhipChargeTracker.releaseCharge();
                sendAttackPacket(WhipAttackPacket.SwingType.OVERHEAD, charge);
            }

            // Right click = Charge + Sweep
            if (useDown && !wasUseDown && !attackDown) {
                WhipChargeTracker.startCharge(WhipChargeTracker.SwingType.SWEEP);
            } else if (!useDown && wasUseDown && WhipChargeTracker.getChargeType() == WhipChargeTracker.SwingType.SWEEP) {
                int charge = WhipChargeTracker.getChargeTicks();
                WhipChargeTracker.releaseCharge();
                sendAttackPacket(WhipAttackPacket.SwingType.SWEEP, charge);
            }

            wasAttackDown = attackDown;
            wasUseDown = useDown;

            // ---- Verlet chain physics ----
            WhipChainSimulator.tick(mc);

        } else {
            if (wasAttackDown) wasAttackDown = false;
            if (wasUseDown)  wasUseDown = false;
            if (WhipChargeTracker.isCharging()) {
                WhipChargeTracker.cancelCharge();
            }
            WhipChainSimulator.reset();
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity().getMainHandItem().getItem() instanceof DarkWhipItem) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if (event.getEntity().getMainHandItem().getItem() instanceof DarkWhipItem) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity().getMainHandItem().getItem() instanceof DarkWhipItem) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().getMainHandItem().getItem() instanceof DarkWhipItem) {
            event.setCanceled(true);
        }
    }

    private static void sendAttackPacket(WhipAttackPacket.SwingType type, int chargeTicks) {
        ModNetwork.CHANNEL.sendToServer(new WhipAttackPacket(type, chargeTicks));
    }
}
