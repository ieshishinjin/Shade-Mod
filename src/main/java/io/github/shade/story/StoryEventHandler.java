package io.github.shade.story;

import io.github.shade.ShadeMod;
import io.github.shade.story.adapter.AdapterRegistry;
import io.github.shade.story.aigen.AiConfig;
import io.github.shade.story.gallery.GalleryManager;
import io.github.shade.story.quest.QuestManager;
import io.github.shade.story.trigger.TriggerManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * 故事系统生命周期事件入口
 *
 * 整合：脚本加载、Quest 管理、触发器检测、游戏事件绑定
 */
public class StoryEventHandler {

    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;

        ShadeMod.LOGGER.info("[story] 注册故事系统事件监听器");

        ServerLifecycleEvents.SERVER_STARTED.register(StoryEventHandler::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(StoryEventHandler::onServerStopping);

        // Quest + Trigger 每 tick 更新
        ServerTickEvents.END_SERVER_TICK.register(StoryEventHandler::onServerTick);

        // 玩家加入/离开
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {});
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

        // NPC 交互 → 触发器检测
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND) {
                String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                TriggerManager.getInstance(serverPlayer.serverLevel())
                        .checkNpcInteract(serverPlayer, entityId);
            }
            return InteractionResult.PASS;
        });
    }

    private static void onServerStarted(MinecraftServer server) {
        ShadeMod.LOGGER.info("[story] ===== 初始化故事系统 =====");

        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) continue;

            StoryEngine.getInstance(level).loadScripts();
            QuestManager.getInstance(level);
            TriggerManager.getInstance(level);
        }
    }

    private static void onServerTick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) continue;
            QuestManager.getInstance(level).tick();
            TriggerManager.getInstance(level).tick();
        }
    }

    private static void onServerStopping(MinecraftServer server) {
        ShadeMod.LOGGER.info("[story] 保存所有故事数据...");
        StoryManager.cleanupAll();
        StoryEngine.cleanupAll();
        QuestManager.cleanupAll();
        TriggerManager.cleanupAll();
        GalleryManager.cleanupAll();
        AiConfig.cleanup();
    }

    // ==================== 游戏事件 → Quest 进度 ====================

    private static void handleMobKill(ServerPlayer player, LivingEntity entity) {
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        AdapterRegistry.notifyProgress(player, "KILL_MOB", entityId, 1);
        if (!(entity instanceof Monster)) {
            AdapterRegistry.notifyProgress(player, "KILL_BOSS", entityId, 1);
        }
    }
}
