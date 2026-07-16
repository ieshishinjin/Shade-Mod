package io.github.shade;

import io.github.shade.camp.CampCommand;
import io.github.shade.camp.CampEventHandler;
import io.github.shade.story.StoryCommand;
import io.github.shade.story.StoryEventHandler;
import io.github.shade.story.adapter.AdapterRegistry;
import io.github.shade.story.adapter.CampAdapter;
import io.github.shade.story.aigen.AiCommand;
import io.github.shade.story.gallery.GalleryCommand;
import io.github.shade.story.network.StoryPayloads;
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

        // 所有 register() 调用集中于此，按模块归类
        // 每个模块的 register() 负责注册该模块所需的 Fabric 事件监听器

        // — 生命周期 & 事件 —
        CampEventHandler.register();   // 据点系统（刷怪禁用、Tick、Camp 生命周期）
        StoryEventHandler.register();  // 故事系统（脚本加载、Quest、触发器、合成/击杀事件）

        // — 网络包 —
        StoryPayloads.register();      // S2C/C2S 自定义数据包

        // — 适配器 —
        AdapterRegistry.register(new CampAdapter());       // 据点系统（OCCUPY_CAMP, ATTACK_CAMP 等）
        AdapterRegistry.register(new io.github.shade.story.adapter.InventoryAdapter());  // 物品收集（COLLECT_ITEM, CRAFT_ITEM）
        AdapterRegistry.register(new io.github.shade.story.adapter.CombatAdapter());     // 战斗统计（KILL_MOB, KILL_BOSS）
        AdapterRegistry.register(new io.github.shade.story.adapter.VillagerAdapter());   // 村民交易（TRADE_VILLAGER）

        // — 命令 —
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            CampCommand.register(dispatcher);
            StoryCommand.register(dispatcher);
            AiCommand.register(dispatcher);
            GalleryCommand.register(dispatcher);
            WorldLevelCommand.register(dispatcher);
        });

        LOGGER.info("Shade 模组初始化完成");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
