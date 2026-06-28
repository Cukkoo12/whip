package com.cukkoo.whip.enchantment;

import com.cukkoo.whip.WhipMod;
import com.cukkoo.whip.item.DarkWhipItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, WhipMod.MOD_ID);

    public static final EnchantmentCategory WHIP_CATEGORY =
            EnchantmentCategory.create("whip", item -> item instanceof DarkWhipItem);

    public static final RegistryObject<Enchantment> REACH =
            ENCHANTMENTS.register("reach", ReachEnchantment::new);

    public static final RegistryObject<Enchantment> GRAPPLE =
            ENCHANTMENTS.register("grapple", GrappleEnchantment::new);
}
