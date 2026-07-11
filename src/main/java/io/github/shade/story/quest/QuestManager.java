package io.github.shade.story.quest;

import io.github.shade.ShadeMod;
import io.github.shade.story.StoryEngine;
import io.github.shade.story.adapter.AdapterRegistry;
import io.github.shade.story.model.QuestData;
import io.github.shade.story.model.QuestRewardData;
import io.github.shade.story.network.StoryPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quest 管理器 — 对应设计文档 §三 "Quest 系统"
 *
 * 职责：
 * 1. 管理和追踪所有玩家的活跃 Quest
 * 2. 监听适配器事件，更新 Objective 进度
 * 3. 检测 Quest 完成，发放奖励
 * 4. 与剧情引擎联动（Quest 完成 → 剧情跳转）
 * 5. 提供 Quest 日志数据
 */
public class QuestManager {

    private static final Map<ServerLevel, QuestManager> INSTANCES = new ConcurrentHashMap<>();

    /** 每个玩家的活跃 Quest 列表 */
    private final Map<UUID, List<RuntimeQuest>> activeQuests = new ConcurrentHashMap<>();
    /** 每个玩家的已完成 Quest 历史 */
    private final Map<UUID, Set<String>> completedQuestIds = new ConcurrentHashMap<>();

    private final ServerLevel world;
    private int tickCounter = 0;

    // ==================== 单例 ====================

    private QuestManager(ServerLevel world) {
        this.world = world;
    }

    public static QuestManager getInstance(ServerLevel world) {
        return INSTANCES.computeIfAbsent(world, k -> {
            QuestManager mgr = new QuestManager(world);
            mgr.registerListeners();
            return mgr;
        });
    }

    public static void cleanup(ServerLevel world) {
        INSTANCES.remove(world);
    }

    public static void cleanupAll() {
        INSTANCES.clear();
    }

    // ==================== 初始化 ====================

    /**
     * 注册适配器事件监听器
     */
    private void registerListeners() {
        AdapterRegistry.registerProgressListener((player, event) -> {
            // 收到适配器进度事件 → 更新匹配的 Quest
            onObjectiveProgress(player, event.objectiveType(), event.targetId(), event.delta());
        });
        ShadeMod.LOGGER.info("[quest] QuestManager 已启动并注册事件监听");
    }

    // ==================== Quest 生命周期 ====================

    /**
     * 开始一个新的 Quest（从剧情脚本的 QUEST_START 节点触发）
     *
     * @param player 目标玩家
     * @param data   Quest 数据
     * @return 创建的 RuntimeQuest
     */
    public RuntimeQuest startQuest(ServerPlayer player, QuestData data) {
        if (data == null || data.getQuestId() == null) return null;

        // 检查是否已完成过（防止重复接取）
        Set<String> completed = completedQuestIds.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet());
        if (completed.contains(data.getQuestId())) {
            ShadeMod.LOGGER.debug("[quest] 玩家 {} 已完成过 Quest: {}", player.getName().getString(), data.getQuestId());
            return null;
        }

        // 检查是否已有活跃的同 ID Quest
        List<RuntimeQuest> playerQuests = activeQuests.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        for (RuntimeQuest q : playerQuests) {
            if (q.getQuestId().equals(data.getQuestId())) {
                return q; // 已在活跃中
            }
        }

        RuntimeQuest quest = new RuntimeQuest(data);
        playerQuests.add(quest);

        // 从适配器同步初始进度
        quest.syncFromAdapters(player);

        ShadeMod.LOGGER.info("[quest] 玩家 {} 开始 Quest: {} ({})",
                player.getName().getString(), quest.getQuestName(), quest.getQuestId());
        return quest;
    }

    /**
     * 每 tick 更新（检查进度、完成检测、适配器同步）
     */
    public void tick() {
        tickCounter++;

        // 每 20 tick（1 秒）检查一次进度
        if (tickCounter % 20 != 0) return;

        for (Map.Entry<UUID, List<RuntimeQuest>> entry : activeQuests.entrySet()) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;

            List<RuntimeQuest> quests = entry.getValue();
            Iterator<RuntimeQuest> it = quests.iterator();

            while (it.hasNext()) {
                RuntimeQuest quest = it.next();
                if (quest.getState() != RuntimeQuest.QuestState.ACTIVE) {
                    it.remove();
                    continue;
                }

                // 从适配器同步进度
                quest.syncFromAdapters(player);

                // 检测完成
                if (quest.isCompleted()) {
                    completeQuest(player, quest);
                    it.remove();
                }
            }
        }
    }

    /**
     * 处理 Objective 进度更新事件
     */
    public void onObjectiveProgress(ServerPlayer player, String objectiveType, String targetId, int delta) {
        List<RuntimeQuest> quests = activeQuests.get(player.getUUID());
        if (quests == null || quests.isEmpty()) return;

        for (RuntimeQuest quest : quests) {
            if (quest.getState() != RuntimeQuest.QuestState.ACTIVE) continue;

            boolean updated = quest.updateProgress(objectiveType, targetId, delta);

            // 进度有更新 && 完成 → 完成 Quest
            if (updated && quest.isCompleted()) {
                completeQuest(player, quest);
            }
        }
    }

    // ==================== Quest 完成 ====================

    /**
     * 完成 Quest — 发放奖励，通知剧情引擎
     */
    private void completeQuest(ServerPlayer player, RuntimeQuest quest) {
        quest.complete();

        // 记录已完成
        completedQuestIds.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet())
                .add(quest.getQuestId());

        ShadeMod.LOGGER.info("[quest] 玩家 {} 完成 Quest: {}",
                player.getName().getString(), quest.getQuestName());

        // 发放奖励
        deliverRewards(player, quest);

        // 通知剧情引擎（如果有关联剧情节点）
        notifyStoryEngine(player, quest);

        // 发送追踪更新到客户端
        sendQuestClearToClient(player, quest);
    }

    /**
     * 发放 Quest 奖励
     */
    private void deliverRewards(ServerPlayer player, RuntimeQuest quest) {
        QuestRewardData rewards = quest.getRewards();
        if (rewards == null) return;

        // 经验奖励
        if (rewards.getExp() > 0) {
            player.giveExperiencePoints(rewards.getExp());
            ShadeMod.LOGGER.debug("[quest]  +{} 经验值", rewards.getExp());
        }

        // 物品奖励
        if (rewards.getItems() != null) {
            for (String itemEntry : rewards.getItems()) {
                String[] parts = itemEntry.split(":");
                String itemId;
                int count = 1;

                if (parts.length >= 3) {
                    // namespace:path:count format
                    itemId = parts[0] + ":" + parts[1];
                    try { count = Integer.parseInt(parts[2]); } catch (NumberFormatException ignored) {}
                } else if (parts.length == 2) {
                    itemId = itemEntry.contains(":") ? itemEntry : "minecraft:" + itemEntry;
                } else {
                    itemId = "minecraft:" + itemEntry;
                }

                ResourceLocation id = ResourceLocation.parse(itemId);
                var item = BuiltInRegistries.ITEM.get(id);
                if (item != null) {
                    ItemStack stack = new ItemStack(item, count);
                    if (!player.getInventory().add(stack)) {
                        // 背包满 → 掉落在玩家位置
                        world.addFreshEntity(new ItemEntity(
                                world, player.getX(), player.getY(), player.getZ(), stack));
                    }
                }
            }
        }

        // Flag 奖励
        if (rewards.getFlags() != null) {
            var progress = io.github.shade.story.StoryManager.getInstance(world).getProgress(player);
            progress.getFlags().putAll(rewards.getFlags());
            io.github.shade.story.StoryManager.getInstance(world).save(player);
        }
    }

    /**
     * 通知剧情引擎 Quest 完成
     */
    private void notifyStoryEngine(ServerPlayer player, RuntimeQuest quest) {
        String nextNode = quest.getOnQuestComplete();
        if (nextNode == null || nextNode.isEmpty()) return;

        StoryEngine engine = StoryEngine.getInstance(world);
        if (!engine.isInStory(player)) return;

        // 如果玩家在剧情中，跳转到 Quest 完成节点
        ShadeMod.LOGGER.debug("[quest] 剧情跳转: {} → {}", quest.getQuestName(), nextNode);
        // 通过 StoryEngine 的节点跳转机制
        // 玩家当前所在节点应包含 onQuestComplete 指向的节点 ID
    }

    /**
     * 发送 Quest 完成通知给客户端（清除 HUD 追踪）
     */
    private void sendQuestClearToClient(ServerPlayer player, RuntimeQuest quest) {
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§a✦ §l任务完成: §r§f" + quest.getQuestName()));
    }

    // ==================== 查询方法 ====================

    /**
     * 获取玩家的活跃 Quest 列表
     */
    public List<RuntimeQuest> getActiveQuests(ServerPlayer player) {
        return activeQuests.getOrDefault(player.getUUID(), Collections.emptyList());
    }

    /**
     * 获取玩家的首个活跃 Quest（用于 HUD 显示）
     */
    public RuntimeQuest getPrimaryQuest(ServerPlayer player) {
        List<RuntimeQuest> quests = activeQuests.get(player.getUUID());
        if (quests == null || quests.isEmpty()) return null;
        return quests.stream()
                .filter(q -> q.getState() == RuntimeQuest.QuestState.ACTIVE)
                .findFirst().orElse(null);
    }

    /**
     * 检查 Quest 是否已完成过
     */
    public boolean isQuestCompleted(ServerPlayer player, String questId) {
        Set<String> completed = completedQuestIds.get(player.getUUID());
        return completed != null && completed.contains(questId);
    }

    /**
     * 获取 Quest 追踪 HUD 数据
     */
    public String[] getTrackingData(ServerPlayer player) {
        RuntimeQuest quest = getPrimaryQuest(player);
        if (quest == null) return new String[0];
        return quest.getTrackingLines();
    }
}
