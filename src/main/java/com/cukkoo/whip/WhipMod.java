package com.cukkoo.whip;

import com.cukkoo.whip.enchantment.ModEnchantments;
import com.cukkoo.whip.item.ModItems;
import com.cukkoo.whip.network.ModNetwork;
import com.cukkoo.whip.sound.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cukkoo.whip.item.DarkWhipItem;
import com.cukkoo.whip.network.WhipAttackPacket;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3f;

import java.util.List;

@Mod(WhipMod.MOD_ID)
public class WhipMod {
    public static final String MOD_ID = "whip";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public WhipMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        ModEnchantments.ENCHANTMENTS.register(modBus);
        ModSounds.SOUNDS.register(modBus);

        ModNetwork.init();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.cukkoo.whip.client.ClientSetup.init();
        }

        LOGGER.info("Dark Whip mod loaded!");
    }

    public static class ServerEvents {

        public static void processAttack(ServerPlayer player,
                                          WhipAttackPacket.SwingType swingType,
                                          int chargeTicks) {
            ItemStack whip = player.getMainHandItem();
            if (!(whip.getItem() instanceof DarkWhipItem)) return;

            int reachLevel = EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.REACH.get(), whip);
            boolean hasGrapple = EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.GRAPPLE.get(), whip) > 0;

            if (swingType == WhipAttackPacket.SwingType.OVERHEAD) {
                performOverheadStrike(player, chargeTicks, reachLevel, hasGrapple);
            } else {
                performSideSlash(player, chargeTicks, reachLevel);
            }
        }

        private static void performOverheadStrike(ServerPlayer player, int chargeTicks,
                                                   int reachLevel, boolean hasGrapple) {
            float chargeProgress = Math.min(chargeTicks / 40f, 1f);
            double range = 4.5 + reachLevel * 1.5 + chargeProgress * 4.0;
            float damage = 6f + chargeProgress * 12f;

            Vec3 eyePos = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            Vec3 end = eyePos.add(look.scale(range));

            ServerLevel level = (ServerLevel) player.level();

            // Entity raytrace
            AABB scanBox = player.getBoundingBox()
                    .expandTowards(look.scale(range))
                    .inflate(0.5);
            List<Entity> entities = level.getEntities(player, scanBox,
                    e -> e instanceof LivingEntity && e.isPickable());

            EntityHitResult entityHit = null;
            double closestDist = range;
            for (Entity e : entities) {
                AABB entityBox = e.getBoundingBox().inflate(0.3);
                Vec3 hit = entityBox.clip(eyePos, end).orElse(null);
                if (hit != null) {
                    double dist = eyePos.distanceTo(hit);
                    if (dist < closestDist) {
                        closestDist = dist;
                        entityHit = new EntityHitResult(e, hit);
                    }
                }
            }

            if (entityHit != null) {
                // Hit an entity
                Entity target = entityHit.getEntity();
                target.hurt(player.damageSources().playerAttack(player), damage);
                spawnHitEffects(level, entityHit.getLocation(), chargeProgress);
                playWhipCrack(level, entityHit.getLocation(), chargeProgress);

                // Cooldown
                player.getCooldowns().addCooldown(
                        player.getMainHandItem().getItem(), 30);
            } else {
                // Raytrace blocks for grapple
                BlockHitResult blockHit = level.clip(new ClipContext(
                        eyePos, end, ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE, player));
                if (blockHit.getType() == HitResult.Type.BLOCK && hasGrapple) {
                    applyGrapple(player, blockHit.getLocation(), chargeProgress, reachLevel);
                    playWhipCrack(level, blockHit.getLocation(), chargeProgress);
                    player.getCooldowns().addCooldown(
                            player.getMainHandItem().getItem(), 40);
                } else if (blockHit.getType() == HitResult.Type.BLOCK) {
                    playWhipCrack(level, blockHit.getLocation(), chargeProgress);
                    player.getCooldowns().addCooldown(
                            player.getMainHandItem().getItem(), 30);
                }
            }
        }

        private static void performSideSlash(ServerPlayer player, int chargeTicks, int reachLevel) {
            float chargeProgress = Math.min(chargeTicks / 40f, 1f);
            double range = 3.5 + reachLevel * 1.5 + chargeProgress * 3.5;
            float damage = 4f + chargeProgress * 8f;

            Vec3 eyePos = player.getEyePosition();
            Vec3 look = player.getLookAngle().normalize();
            ServerLevel level = (ServerLevel) player.level();

            AABB scanBox = player.getBoundingBox().inflate(range);
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                    scanBox, e -> e != player && e.isAlive() && e.isPickable());

            int hitCount = 0;
            for (LivingEntity target : targets) {
                Vec3 toTarget = target.position().subtract(player.position());
                Vec3 toTargetXZ = new Vec3(toTarget.x, 0, toTarget.z).normalize();
                Vec3 lookXZ = new Vec3(look.x, 0, look.z).normalize();

                double dot = toTargetXZ.dot(lookXZ);
                double halfAngle = Math.cos(Math.toRadians(90)); // 180-degree arc

                if (dot > halfAngle && player.distanceTo(target) <= range) {
                    target.hurt(player.damageSources().playerAttack(player), damage);
                    Vec3 knockback = toTarget.normalize().scale(0.8);
                    target.push(knockback.x, 0.4, knockback.z);
                    hitCount++;

                    Vec3 hitPos = target.position().add(0, target.getBbHeight() / 2, 0);
                    spawnHitEffects(level, hitPos, 0.3f);
                    playWhipCrack(level, hitPos, 0.3f);
                }
            }

            if (hitCount > 0) {
                player.getCooldowns().addCooldown(
                        player.getMainHandItem().getItem(), 20);
            }
        }

        private static void spawnHitEffects(ServerLevel level, Vec3 pos, float intensity) {
            BlockParticleOption bloodParticle = new BlockParticleOption(
                    ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());

            level.sendParticles(bloodParticle,
                    pos.x, pos.y, pos.z,
                    25, 0.2, 0.2, 0.2, 0.05);

            level.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.SLIME_ATTACK, SoundSource.PLAYERS,
                    0.8f, 0.6f);
        }

        private static void playWhipCrack(ServerLevel level, Vec3 pos, float intensity) {
            float pitch = 0.8f + intensity * 0.4f; // 0.8 to 1.2
            level.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS,
                    1.0f, pitch * 0.8f);
            level.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS,
                    1.0f, pitch * 1.2f);
        }

        private static void applyGrapple(ServerPlayer player, Vec3 grapplePoint,
                                         float chargeProgress, int reachLevel) {
            Vec3 toPoint = grapplePoint.subtract(player.position());
            double distance = toPoint.length();
            
            // Pull player toward the grapple point with an upward arc
            Vec3 direction = toPoint.normalize();
            // Scale speed based on distance and reach level, but clamped to reasonable values
            double speed = 0.8 + chargeProgress * 0.5 + Math.min(distance * 0.02, 0.4) + (reachLevel * 0.05);
            
            Vec3 velocity = direction.scale(speed);
            // Add upward component for a swinging arc feel
            velocity = velocity.add(0, 0.2 + chargeProgress * 0.2 + reachLevel * 0.05, 0);

            player.setDeltaMovement(velocity);
            player.hurtMarked = true;
            player.fallDistance = 0;

            LOGGER.debug("Grapple applied to player: vel={}", velocity);
        }
    }
}
