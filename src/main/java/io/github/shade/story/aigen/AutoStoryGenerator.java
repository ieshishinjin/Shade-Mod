package io.github.shade.story.aigen;

import io.github.shade.ShadeMod;
import io.github.shade.story.StoryEngine;
import io.github.shade.story.adapter.AdapterRegistry;
import io.github.shade.story.model.StoryNode;
import io.github.shade.story.quest.QuestManager;
import io.github.shade.story.network.StoryPayloads;
import io.github.shade.worldlevel.WorldLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自动剧情生成器 — 监听游戏事件，自动触发 AI 剧情生成
 *
 * 触发条件（全部结合）：
 * 1. 首次进入游戏 → 生成开场剧情
 * 2. 完成剧情/任务 → 生成后续剧情
 * 3. 世界等级提升 → 根据新难度生成对应挑战
 * 4. 游戏时间 → 定期生成日常/随机事件
 * 5. 玩家行为累积 → 击杀数/探索距离达到阈值
 *
 * 优先级：世界等级提升 > 完成剧情 > 首次进入 > 行为累积 > 定时触发
 *
 * 防刷机制：每次生成后至少冷却 30 分钟（真实时间）
 */
public class AutoStoryGenerator {

    private static AutoStoryGenerator INSTANCE;

    /** 冷却时间（毫秒） */
    private static final long COOLDOWN_MS = 30 * 60 * 1000L; // 30分钟

    /** 每位玩家的最后生成时间 */
    private final Map<UUID, Long> lastGenerationTime = new ConcurrentHashMap<>();

    /** 每位玩家的最后世界等级（用于检测变化） */
    private final Map<UUID, Integer> perPlayerWorldLevel = new ConcurrentHashMap<>();

    /** 上次检查的游戏时间（用于定时触发） */
    private long lastGameTimeCheck = 0;

    private AutoStoryGenerator() {}

    public static AutoStoryGenerator getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AutoStoryGenerator();
        }
        return INSTANCE;
    }

    // ==================== tick 调用（由 StoryEventHandler 每 tick 调用） ====================

    /**
     * 每 tick 检测并触发自动生成
     */
    public void tick(ServerLevel level, int serverTickCount) {
        // 每 100 tick（5秒）检测一次
        if (serverTickCount % 100 != 0) return;

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;

            PlayerStoryProfile profile = PlayerStoryProfile.get(player.getUUID());

            // 检查是否有进行中的请求
            if (GenerationQueue.getInstance().hasPending(player.getUUID())) continue;

            // 检查冷却
            if (isOnCooldown(player)) continue;

            // 检查是否是第一次进入游戏
            if (checkFirstJoin(player)) return;

            // 检测世界等级变化
            if (checkWorldLevelChange(level, player)) return;

            // 检测行为累积阈值
            if (checkBehaviorThreshold(player, profile)) return;

            // 定时触发检查（每 2000 tick ≈ 100秒检查一次，模拟游戏天数）
            if (checkTimedTrigger(level, player, serverTickCount)) return;
        }
    }

    // ==================== 事件驱动触发 ====================

    /**
     * 玩家完成剧情后调用 — 生成后续剧情
     */
    public void onStoryCompleted(ServerPlayer player) {
        if (isOnCooldown(player)) return;

        AiConfig config = AiConfig.getInstance(player.serverLevel());
        if (!config.isEnabled() || !config.isAutoGenerate()) return;

        triggerAutoGenerate(player, "剧情完成，生成后续");
    }

    /**
     * 世界等级变化时调用
     */
    public void onWorldLevelChanged(ServerPlayer player, int newLevel) {
        // 等级变化由 tick 检测处理
    }

    /**
     * 玩家完成 Quest 后调用
     */
    public void onQuestCompleted(ServerPlayer player) {
        if (isOnCooldown(player)) return;

        AiConfig config = AiConfig.getInstance(player.serverLevel());
        if (!config.isEnabled() || !config.isAutoGenerate()) return;

        triggerAutoGenerate(player, "任务完成，生成新剧情");
    }

    // ==================== 内部检测方法 ====================

    /**
     * 检查玩家首次进入游戏
     */
    private boolean checkFirstJoin(ServerPlayer player) {
        StoryEngine engine = StoryEngine.getInstance(player.serverLevel());
        if (engine.isInStory(player)) return false;

        // 检查是否已完成过任意脚本
        var progress = io.github.shade.story.StoryManager.getInstance(player.serverLevel()).getProgress(player);
        if (progress.getCompletedScripts().isEmpty()) {
            // 从未完成过任何剧情
            AiConfig config = AiConfig.getInstance(player.serverLevel());
            if (config.isEnabled() && config.isAutoGenerate()) {
                triggerAutoGenerate(player, "首次进入游戏，生成开场剧情");
                return true;
            }
        }
        return false;
    }

    /**
     * 检查世界等级变化（每位玩家独立追踪）
     */
    private boolean checkWorldLevelChange(ServerLevel level, ServerPlayer player) {
        try {
            int currentLevel = WorldLevel.getLevel(level);
            UUID uuid = player.getUUID();
            Integer lastLevel = perPlayerWorldLevel.get(uuid);

            if (lastLevel != null && currentLevel > lastLevel) {
                // 该玩家的世界等级提升了
                perPlayerWorldLevel.put(uuid, currentLevel);
                triggerAutoGenerate(player, "世界等级提升至 " + currentLevel + "，生成新挑战");
                return true;
            }
            if (lastLevel == null) {
                perPlayerWorldLevel.put(uuid, currentLevel);
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * 检查玩家行为累积阈值
     */
    private boolean checkBehaviorThreshold(ServerPlayer player, PlayerStoryProfile profile) {
        // 每检查 10 次（大约 50秒 一次）才检查阈值，防过度生成
        profile.incrementDryChecks();
        if (profile.getDryChecks() < 10) return false;
        profile.resetDryChecks();

        // 检查击杀累积
        int killCount = profile.getActionCount("kill");
        if (killCount > 0 && killCount % 20 == 0) {
            // 每击杀 20 个触发一次
            triggerAutoGenerate(player, "击杀累积 " + killCount + "，生成战斗相关剧情");
            return true;
        }

        return false;
    }

    /**
     * 检查定时触发
     */
    private boolean checkTimedTrigger(ServerLevel level, ServerPlayer player, int tickCount) {
        // 每 4000 tick（约3.3分钟）检查一次，模拟每游戏日检查
        if (tickCount - lastGameTimeCheck < 4000) return false;
        lastGameTimeCheck = tickCount;

        // 10% 概率触发（避免每次都在同一天触发）
        if (level.random.nextFloat() < 0.1f) {
            triggerAutoGenerate(player, "日常随机事件");
            return true;
        }

        return false;
    }

    // ==================== 执行生成 ====================

    /**
     * 触发自动 AI 生成
     */
    private void triggerAutoGenerate(ServerPlayer player, String reason) {
        ServerLevel level = player.serverLevel();
        StoryEngine engine = StoryEngine.getInstance(level);
        AiConfig config = AiConfig.getInstance(level);

        // 记录冷却
        lastGenerationTime.put(player.getUUID(), System.currentTimeMillis());
        PlayerStoryProfile.get(player.getUUID()).setLastGenerationTick(level.getGameTime());

        ShadeMod.LOGGER.debug("[auto-gen] 触发自动生成: 玩家={}, 原因={}",
                player.getName().getString(), reason);

        // 向玩家发送提示
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§e✦ AI 正在为你生成独特的剧情..."));

        // 构建生成任务
        CompletableFuture<StoryAiGenerator.GenerateResult> future = new CompletableFuture<>();
        StoryAiGenerator.getInstance().generate(player, engine, config, reason)
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        // 注入生成的节点
                        StoryNode startNode = StoryAiGenerator.getInstance()
                                .injectNodes(player, engine, result);

                        if (startNode != null) {
                            // 如果玩家当前不在剧情中，自动开始
                            if (!engine.isInStory(player)) {
                                engine.startScript(player, engine.getActiveScriptId(player));
                            }

                            // 发送给客户端显示
                            StoryNode displayNode = engine.resolveAndGetDisplayNode(player);
                            if (displayNode != null) {
                                StoryPayloads.sendNodeToClient(player, engine, displayNode);
                            }
                        }

                        ShadeMod.LOGGER.info("[auto-gen] 自动生成完成: 原因={}", reason);
                    } else {
                        ShadeMod.LOGGER.warn("[auto-gen] 自动生成失败: {}", result.getError());
                    }
                })
                .exceptionally(e -> {
                    ShadeMod.LOGGER.error("[auto-gen] 自动生成异常", e);
                    return null;
                });

        GenerationQueue.getInstance().submit(player, future, reason);
    }

    // ==================== 辅助方法 ====================

    /**
     * 检查玩家是否在冷却中
     */
    private boolean isOnCooldown(ServerPlayer player) {
        Long lastTime = lastGenerationTime.get(player.getUUID());
        if (lastTime == null) return false;
        return (System.currentTimeMillis() - lastTime) < COOLDOWN_MS;
    }

    public static void cleanup() {
        INSTANCE = null;
    }
}
