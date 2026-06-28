package com.cukkoo.whip.sound;

import com.cukkoo.whip.WhipMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, WhipMod.MOD_ID);

    public static final RegistryObject<SoundEvent> WHIP_CRACK =
            SOUNDS.register("whip_crack",
                    () -> SoundEvent.createVariableRangeEvent(
                            new ResourceLocation(WhipMod.MOD_ID, "whip_crack")));
}
