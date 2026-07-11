package io.github.shade.story;

import io.github.shade.ShadeMod;
import io.github.shade.story.adapter.AdapterRegistry;
import io.github.shade.story.quest.QuestManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;

/**
 * 故事系统生命周期事件入口
 *
 * 职责：
 * - 服务器启动时加载所有剧情脚本 + 注册适配器
 * - 服务器关闭时保存所有玩家进度
 * - 每 tick 更新 Quest 进度
 * - 绑定游戏事件（击杀、Camp 清空等）到 Quest 进度
 */
public class StoryEventHandler {

    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;

        ShadeMod.LOGGER.info("[story] 注册故事系统事件监听器");

        ServerLifecycleEvents.SERVER_STARTED.register(StoryEventHandler::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(StoryEventHandler::onServerStopping);

        // Quest 管理器每 tick 更新
        ServerTickEvents.END_SERVER_TICK.register(StoryEventHandler::onServerTick);

        // 玩家加入/离开
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ShadeMod.LOGGER.debug("[story] 玩家加入: {}", handler.getPlayer().getName().getString());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var player = handler.getPlayer();
            if (player != null) {
                var world = player.serverLevel();
                StoryManager.getInstance(world).save(player);
                StoryEngine.getInstance(world).endStory(player);
            }
        });

        // 击杀事件 → Quest 进度更新
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, target, killer) -> {
            if (killer instanceof ServerPlayer player && target instanceof LivingEntity living) {
                handleMobKill(player, living);
            }
        });
    }

    private static void onServerStarted(MinecraftServer server) {
        ShadeMod.LOGGER.info("[story] ===== 初始化故事系统 =====");

        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) continue;

            // 加载剧情脚本
            StoryEngine.getInstance(level).loadScripts();

            // 初始化 Quest 管理器
            QuestManager.getInstance(level);
        }
    }

    private static void onServerTick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) continue;
            QuestManager.getInstance(level).tick();
        }
    }

    private static void onServerStopping(MinecraftServer server) {
        ShadeMod.LOGGER.info("[story] 保存所有故事进度...");
        StoryManager.cleanupAll();
        StoryEngine.cleanupAll();
        QuestManager.cleanupAll();
    }

    // ==================== 游戏事件 → Quest 进度 ====================

    /**
     * 处理玩家击杀实体 → 通知适配器系统
     */
    private static void handleMobKill(ServerPlayer player, LivingEntity entity) {
        String entityId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(entity.getType()).toString();

        // 通知 Quest 管理器：KILL_MOB 进度 +1
        AdapterRegistry.notifyProgress(player, "KILL_MOB", entityId, 1);

        // 如果是 Boss 类实体，也触发 KILL_BOSS
        if (!(entity instanceof Monster)) {
            AdapterRegistry.notifyProgress(player, "KILL_BOSS", entityId, 1);
        }

        ShadeMod.LOGGER.debug("[story] 玩家 {} 击杀 {} → 进度更新",
                player.getName().getString(), entityId);
    }
}
