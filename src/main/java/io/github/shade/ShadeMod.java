package io.github.shade;

import io.github.shade.camp.CampCommand;
import io.github.shade.camp.CampEventHandler;
import io.github.shade.worldlevel.WorldLevelCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.resources.ResourceLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShadeMod implements ModInitializer {
    public static final String MOD_ID = "shade";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Shade 模组初始化...");

        CampEventHandler.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            CampCommand.register(dispatcher);
            WorldLevelCommand.register(dispatcher);
        });

        LOGGER.info("Shade 模组初始化完成");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
