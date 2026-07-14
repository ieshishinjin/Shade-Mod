package io.github.shade.story.aigen;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 剧情上下文管理器 — 维护玩家剧情的连贯性
 *
 * 记录：
 * - 已遇到的角色及其关系
 * - 已完成的事件
 * - 未解决的事件伏笔
 * - 玩家的关键选择
 */
public class StoryContextManager {

    private static final Map<UUID, StoryContextManager> INSTANCES = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY = 50;

    private final UUID playerUuid;

    /** 剧情事件历史 */
    private final List<String> eventHistory = new ArrayList<>();

    /** 活跃的剧情伏笔（未解决的） */
    private final Map<String, Object> pendingForeshadowing = new LinkedHashMap<>();

    /** 角色关系：角色名 → 好感度/关系类型 */
    private final Map<String, String> characterRelations = new LinkedHashMap<>();

    /** 最近一次剧情节点的摘要 */
    private String lastNodeSummary = "";

    /** 当前主题 */
    private String currentTheme = "";

    private StoryContextManager(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public static StoryContextManager get(UUID uuid) {
        return INSTANCES.computeIfAbsent(uuid, StoryContextManager::new);
    }

    // ==================== 事件记录 ====================

    /**
     * 记录一个剧情事件
     */
    public void recordEvent(String event) {
        eventHistory.add(event);
        if (eventHistory.size() > MAX_HISTORY) {
            eventHistory.remove(0);
        }
    }

    /**
     * 获取剧情事件历史
     */
    public List<String> getEventHistory() {
        return Collections.unmodifiableList(eventHistory);
    }

    /**
     * 获取精简版历史摘要（供 AI Prompt 使用）
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();

        if (!currentTheme.isEmpty()) {
            sb.append("当前剧情主题: ").append(currentTheme).append("\n");
        }

        if (!characterRelations.isEmpty()) {
            sb.append("角色关系:\n");
            for (var entry : characterRelations.entrySet()) {
                sb.append("  - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }

        if (!eventHistory.isEmpty()) {
            sb.append("最近事件:\n");
            int start = Math.max(0, eventHistory.size() - 5);
            for (int i = start; i < eventHistory.size(); i++) {
                sb.append("  - ").append(eventHistory.get(i)).append("\n");
            }
        }

        if (!pendingForeshadowing.isEmpty()) {
            sb.append("未解决伏笔:\n");
            for (var entry : pendingForeshadowing.entrySet()) {
                sb.append("  - ").append(entry.getKey()).append("\n");
            }
        }

        if (!lastNodeSummary.isEmpty()) {
            sb.append("最后节点: ").append(lastNodeSummary).append("\n");
        }

        return sb.length() > 0 ? sb.toString() : "（无历史记录，新玩家）\n";
    }

    // ==================== 伏笔管理 ====================

    public void addForeshadowing(String key, Object value) {
        pendingForeshadowing.put(key, value);
    }

    public void resolveForeshadowing(String key) {
        pendingForeshadowing.remove(key);
    }

    public boolean hasForeshadowing(String key) {
        return pendingForeshadowing.containsKey(key);
    }

    // ==================== 角色关系管理 ====================

    public void setRelation(String character, String relation) {
        characterRelations.put(character, relation);
    }

    public String getRelation(String character) {
        return characterRelations.getOrDefault(character, "未知");
    }

    // ==================== 其他 ====================

    public void setLastNodeSummary(String summary) { this.lastNodeSummary = summary; }
    public String getLastNodeSummary() { return lastNodeSummary; }

    public String getCurrentTheme() { return currentTheme; }
    public void setCurrentTheme(String theme) { this.currentTheme = theme; }

    /**
     * 清理所有上下文
     */
    public static void cleanupAll() {
        INSTANCES.clear();
    }
}
