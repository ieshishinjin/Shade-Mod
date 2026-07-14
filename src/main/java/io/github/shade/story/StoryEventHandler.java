package io.github.shade.story;

import io.github.shade.ShadeMod;
import io.github.shade.story.adapter.AdapterRegistry;
import io.github.shade.story.aigen.AiConfig;
import io.github.shade.story.aigen.AutoStoryGenerator;
import io.github.shade.story.aigen.GenerationQueue;
import io.github.shade.story.gallery.GalleryManager;
import io.github.shade.story.quest.QuestManager;
import io.github.shade.story.event.PlayerEvents;
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

        // NPC/村民交互 → 触发器检测 + Quest 进度
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND) {
                String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();

                // 触发器检测
                TriggerManager.getInstance(serverPlayer.serverLevel())
                        .checkNpcInteract(serverPlayer, entityId);

                // 村民交互 → TRADE_VILLAGER Quest 进度
                if (entity instanceof net.minecraft.world.entity.npc.Villager) {
                    AdapterRegistry.notifyProgress(serverPlayer, "TRADE_VILLAGER",
                            entityId, 1);
                }
            }
            return InteractionResult.PASS;
        });

        // 合成事件 → CRAFT_ITEM Quest 进度
        PlayerEvents.CRAFTED.register((player, recipe, craftedItems) -> {
            if (craftedItems != null) {
                for (var stack : craftedItems) {
                    if (!stack.isEmpty()) {
                        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        AdapterRegistry.notifyProgress(player, "CRAFT_ITEM", itemId, stack.getCount());
                    }
                }
            }
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

            // 每 10 tick（0.5 秒）检查玩家物品变化 → COLLECT_ITEM 进度
            if (server.getTickCount() % 10 == 0) {
                InventoryTracker inventoryTracker = InventoryTracker.getInstance();
                for (ServerPlayer player : level.players()) {
                    if (!player.isSpectator()) {
                        inventoryTracker.scan(player);
                    }
                }
            }

            // 每 20 tick（1 秒）检查玩家位置 → REACH_LOCATION 进度
            if (server.getTickCount() % 20 == 0) {
                ZoneTracker zoneTracker = ZoneTracker.getInstance();
                for (ServerPlayer player : level.players()) {
                    if (!player.isSpectator()) {
                        zoneTracker.checkPlayerPosition(player);
                    }
                }
            }

            // 自动剧情生成检测（每 100 tick = 5秒）
            AutoStoryGenerator.getInstance().tick(level, server.getTickCount());
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
        GenerationQueue.cleanup();
        AutoStoryGenerator.cleanup();
        io.github.shade.story.aigen.PlayerStoryProfile.cleanupAll();
        io.github.shade.story.aigen.StoryContextManager.cleanupAll();
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
