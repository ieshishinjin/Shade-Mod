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
import io.github.shade.story.WorldEventManager;
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

    /** NPC AI 对话冷却（毫秒） */
    private static final long NPC_CHAT_COOLDOWN_MS = 10000; // 10秒
    private static final java.util.Map<java.util.UUID, Long> npcChatCooldowns = new java.util.HashMap<>();

    public static void register() {
        if (registered) return;
        registered = true;

        ShadeMod.LOGGER.debug("[story] 注册故事系统事件监听器");

        ServerLifecycleEvents.SERVER_STARTED.register(StoryEventHandler::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(StoryEventHandler::onServerStopping);

        // Quest + Trigger 每 tick 更新
        ServerTickEvents.END_SERVER_TICK.register(StoryEventHandler::onServerTick);

        // 玩家加入/离开
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.getPlayer();
            if (player != null && !player.isSpectator()) {
                server.execute(() -> handlePlayerJoin(player));
            }
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

        // NPC/村民交互 → 触发器检测 + Quest 进度 + AI 对话
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND) {
                String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();

                // 潜行+右键 → AI 对话（优先级最高，不触发触发器）
                if (player.isShiftKeyDown()) {
                    boolean isNamed = entity.hasCustomName();
                    boolean isVillager = entity instanceof net.minecraft.world.entity.npc.Villager;
                    if (isVillager || isNamed) {
                        handleAiNpcChat(serverPlayer, entity, entityId);
                        return InteractionResult.SUCCESS;
                    }
                }

                // 触发器检测（非 AI 对话时触发）
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
        ShadeMod.LOGGER.debug("[story] ===== 初始化故事系统 =====");

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

            // 世界事件检测（每 200 tick = 10秒）
            WorldEventManager.getInstance().tick(level, server.getTickCount());
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
        WorldEventManager.cleanup();
        io.github.shade.story.aigen.PlayerStoryProfile.cleanupAll();
        io.github.shade.story.aigen.StoryContextManager.cleanupAll();
    }

    // ==================== AI NPC 对话 ====================

    /**
     * 处理与 NPC 的 AI 对话
     */
    private static void handleAiNpcChat(ServerPlayer player, net.minecraft.world.entity.Entity entity, String entityId) {
        // 冷却检查
        long now = System.currentTimeMillis();
        Long lastChat = npcChatCooldowns.get(player.getUUID());
        if (lastChat != null && (now - lastChat) < NPC_CHAT_COOLDOWN_MS) {
            long remaining = (NPC_CHAT_COOLDOWN_MS - (now - lastChat)) / 1000;
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e✦ 请稍候 " + remaining + " 秒再对话"));
            return;
        }
        npcChatCooldowns.put(player.getUUID(), now);

        var world = player.serverLevel();
        var config = io.github.shade.story.aigen.AiConfig.getInstance(world);
        if (!config.isEnabled()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§cAI 未启用，请先使用 /story ai 配置"));
            return;
        }

        String npcName = entity.hasCustomName()
                ? entity.getCustomName().getString()
                : entity.getType().getDescription().getString();

        // 创建临时的 AI NPC 对话脚本
        var engine = io.github.shade.story.StoryEngine.getInstance(world);
        String scriptId = "ai_npc_" + player.getUUID().toString().substring(0, 6);

        // 结束当前剧情
        if (engine.isInStory(player)) {
            engine.endStory(player);
        }

        // 创建一个临时脚本供 AI 注入
        var script = new io.github.shade.story.model.StoryScript();
        script.setId(scriptId);
        script.setTitle("与" + npcName + "的对话");
        script.setStartNode("ai_start");
        script.setNodes(new java.util.LinkedHashMap<>());
        engine.registerScript(script);

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§e✦ 与 " + npcName + " 对话中..."));

        // 异步生成 NPC 对话
        var future = io.github.shade.story.aigen.StoryAiGenerator.getInstance()
                .generate(player, engine, config,
                        "玩家正在与NPC「" + npcName + "」交谈。"
                        + "请生成一段简短的对话场景（3-5句对白），NPC是" + npcName + "。"
                        + "对话要符合Minecraft世界观，自然生动。首句应是NPC主动打招呼。");

        io.github.shade.story.aigen.GenerationQueue.getInstance()
                .submit(player, future, "NPC对话: " + npcName);

        future.thenAccept(result -> {
            if (result.isSuccess()) {
                var injected = io.github.shade.story.aigen.StoryAiGenerator.getInstance()
                        .injectNodes(player, engine, result);
                if (injected != null) {
                    var startNode = engine.startScript(player, scriptId);
                    if (startNode != null) {
                        var displayNode = engine.resolveAndGetDisplayNode(player);
                        if (displayNode != null) {
                            io.github.shade.story.network.StoryPayloads.sendNodeToClient(
                                    player, engine, displayNode);
                        }
                    }
                }
            } else {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§c对话生成失败: " + result.getError()));
            }
        });
    }

    // ==================== 玩家引导教程 ====================

    /**
     * 新玩家加入时触发引导教程
     */
    private static void handlePlayerJoin(ServerPlayer player) {
        var world = player.serverLevel();
        var progress = io.github.shade.story.StoryManager.getInstance(world).getProgress(player);

        // 检查是否是首次加入（没有已完成脚本）
        if (!progress.getCompletedScripts().isEmpty()) return;
        if (progress.getFlags().containsKey("tutorial_shown")) return;

        // 标记已显示引导
        progress.getFlags().put("tutorial_shown", true);
        io.github.shade.story.StoryManager.getInstance(world).save(player);

        // 延迟 3 秒后发送引导消息（等待客户端加载完成）
        world.getServer().execute(() -> {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "\n§6✦ §l欢迎使用 Shade Mod!"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "  §7这是一个集成了 Galgame 剧情、AI 生成和据点战斗的模组。"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "  §7以下是一些快速入门指南：\n"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "  §eR §7- 打开剧情菜单"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "  §eU §7- AI 剧情控制面板"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "  §eL §7- 打开任务日志"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "  §eShift+右键 §7- 与 NPC 对话"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "  §e/camp list §7- 查看附近据点"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "  §e/story list §7- 查看所有剧本"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "  §e/story ai recommend §7- 配置免费 AI\n"));
        });
    }

    // ==================== 游戏事件 → Quest 进度 ====================

    private static void handleMobKill(ServerPlayer player, LivingEntity entity) {
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        AdapterRegistry.notifyProgress(player, "KILL_MOB", entityId, 1);

        // 检查是否是末影龙或凋灵（真正的 Boss）
        if (entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
                || entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss) {
            AdapterRegistry.notifyProgress(player, "KILL_BOSS", entityId, 1);
        }

        // 记录玩家行为（AI 联动）
        io.github.shade.story.aigen.PlayerStoryProfile.get(player.getUUID())
                .recordAction("kill");
    }
}
