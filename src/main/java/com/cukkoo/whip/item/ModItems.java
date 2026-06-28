package com.cukkoo.whip.item;

import com.cukkoo.whip.WhipMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, WhipMod.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WhipMod.MOD_ID);

    public static final RegistryObject<Item> DARK_WHIP =
            ITEMS.register("dark_whip", DarkWhipItem::new);

    public static final RegistryObject<CreativeModeTab> WHIP_TAB = TABS.register("whip_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.whip"))
                    .icon(() -> new ItemStack(DARK_WHIP.get()))
                    .displayItems((params, output) -> output.accept(DARK_WHIP.get()))
                    .build());
}
