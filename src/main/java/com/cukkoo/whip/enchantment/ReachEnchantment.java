package com.cukkoo.whip.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class ReachEnchantment extends Enchantment {

    public ReachEnchantment() {
        super(Rarity.UNCOMMON, ModEnchantments.WHIP_CATEGORY,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }
}
