package io.github.shade.story;

import io.github.shade.ShadeMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * 故事系统生命周期事件入口
 *
 * 职责：
 * - 服务器启动时加载所有剧情脚本
 * - 服务器关闭时保存所有玩家进度
 * - 玩家加入/离开时加载/保存进度
 */
public class StoryEventHandler {

    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;

        ShadeMod.LOGGER.info("[story] 注册故事系统事件监听器");

        ServerLifecycleEvents.SERVER_STARTED.register(StoryEventHandler::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(StoryEventHandler::onServerStopping);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // 玩家加入时加载进度（由 StoryManager 懒加载，无需主动操作）
            ShadeMod.LOGGER.debug("[story] 玩家加入: {}", handler.getPlayer().getName().getString());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            // 玩家离开时保存进度
            var player = handler.getPlayer();
            if (player != null) {
                var world = player.serverLevel();
                StoryManager manager = StoryManager.getInstance(world);
                manager.save(player);

                // 清理活跃故事状态
                StoryEngine engine = StoryEngine.getInstance(world);
                engine.endStory(player);
            }
        });
    }

    private static void onServerStarted(MinecraftServer server) {
        ShadeMod.LOGGER.info("[story] ===== 加载剧情脚本 =====");

        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) continue;

            StoryEngine engine = StoryEngine.getInstance(level);
            engine.loadScripts();
        }
    }

    private static void onServerStopping(MinecraftServer server) {
        ShadeMod.LOGGER.info("[story] 保存所有故事进度...");
        StoryManager.cleanupAll();
        StoryEngine.cleanupAll();
    }
}
