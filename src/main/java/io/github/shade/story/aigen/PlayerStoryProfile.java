package io.github.shade.story.aigen;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家剧情档案 — 记录玩家的游戏风格偏好和历史行为
 *
 * 用于指导 AI 生成个性化的剧情内容。
 * 如：战斗型玩家会得到更多战斗剧情，探索型玩家更多探索剧情。
 */
public class PlayerStoryProfile {

    private static final Map<UUID, PlayerStoryProfile> PROFILES = new ConcurrentHashMap<>();

    private final UUID playerUuid;

    /** 各类行为的累计次数 */
    private final Map<String, Integer> actionCounts = new ConcurrentHashMap<>();

    /** 已生成的剧情主题列表（防重复） */
    private final Set<String> usedThemes = ConcurrentHashMap.newKeySet();

    /** 上次剧情生成时间（游戏 tick） */
    private long lastGenerationTick = 0;

    /** 连续未触发生成的检测次数（用于"冷却后检测是否需要再次生成"） */
    private int dryChecks = 0;

    public PlayerStoryProfile(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public static PlayerStoryProfile get(UUID uuid) {
        return PROFILES.computeIfAbsent(uuid, PlayerStoryProfile::new);
    }

    // ==================== 行为记录 ====================

    /**
     * 记录一次玩家行为
     *
     * @param actionType 行为类型：kill, explore, collect, interact, build
     */
    public void recordAction(String actionType) {
        actionCounts.merge(actionType, 1, Integer::sum);
    }

    /**
     * 获取某种行为的总次数
     */
    public int getActionCount(String actionType) {
        return actionCounts.getOrDefault(actionType, 0);
    }

    /**
     * 获取玩家的主导游戏风格
     */
    public String getDominantPlayStyle() {
        String[] styles = {"kill", "explore", "collect", "interact", "build"};
        String dominant = "explore";
        int max = 0;
        for (String style : styles) {
            int count = actionCounts.getOrDefault(style, 0);
            if (count > max) {
                max = count;
                dominant = style;
            }
        }
        return dominant;
    }

    /**
     * 获取行为摘要（供 AI Prompt 使用）
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("游戏风格: ").append(getDominantPlayStyle()).append("\n");
        sb.append("行为统计:\n");
        for (var entry : actionCounts.entrySet()) {
            sb.append("  - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("次\n");
        }
        if (!usedThemes.isEmpty()) {
            sb.append("已使用主题: ").append(String.join(", ", usedThemes)).append("\n");
        }
        return sb.toString();
    }

    // ==================== 主题管理 ====================

    /**
     * 记录已使用的剧情主题
     */
    public void addUsedTheme(String theme) {
        usedThemes.add(theme);
    }

    /**
     * 检查主题是否已使用过
     */
    public boolean hasUsedTheme(String theme) {
        return usedThemes.contains(theme);
    }

    // ==================== 生成冷却 ====================

    public long getLastGenerationTick() { return lastGenerationTick; }
    public void setLastGenerationTick(long tick) { this.lastGenerationTick = tick; }

    public int getDryChecks() { return dryChecks; }
    public void incrementDryChecks() { this.dryChecks++; }
    public void resetDryChecks() { this.dryChecks = 0; }

    /**
     * 清理所有档案
     */
    public static void cleanupAll() {
        PROFILES.clear();
    }
}
