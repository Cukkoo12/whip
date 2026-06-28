package com.cukkoo.whip.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

public class ClientSetup {
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        initialized = true;
        // ClientEvents is registered via @Mod.EventBusSubscriber
        // BEWLR is registered via DarkWhipItem.initializeClient()
    }
}
