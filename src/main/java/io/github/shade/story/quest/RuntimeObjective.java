package io.github.shade.story.quest;

import io.github.shade.story.adapter.AdapterRegistry;
import io.github.shade.story.model.QuestObjectiveData;
import net.minecraft.server.level.ServerPlayer;

/**
 * 运行时 Objective — 追踪单个目标的完成进度
 *
 * 每个 RuntimeObjective 对应一个 QuestObjectiveData，
 * 但在运行时持有可变进度值，并通过适配器系统获取实时状态。
 */
public class RuntimeObjective {

    private final String type;
    private final String targetId;
    private final int targetCount;
    private int progress;
    private final String displayText;

    public RuntimeObjective(String type, String targetId, int targetCount, String displayText) {
        this.type = type;
        this.targetId = targetId;
        this.targetCount = targetCount;
        this.progress = 0;
        this.displayText = displayText != null ? displayText : makeDefaultDisplay(type, targetId);
    }

    public RuntimeObjective(QuestObjectiveData data) {
        this(data.getType(), data.getTargetId(), data.getCount(), data.getDisplayText());
    }

    /**
     * 更新进度（由事件驱动或主动查询更新）
     *
     * @param delta 进度增量
     * @return 本次更新后是否完成了此 Objective
     */
    public boolean addProgress(int delta) {
        if (isCompleted()) return true;
        this.progress = Math.min(this.progress + delta, targetCount);
        return isCompleted();
    }

    /**
     * 直接设置进度
     */
    public void setProgress(int progress) {
        this.progress = Math.min(Math.max(progress, 0), targetCount);
    }

    /**
     * 通过适配器同步最新进度
     */
    public void syncFromAdapter(ServerPlayer player) {
        int adapterProgress = AdapterRegistry.getProgress(player, type, targetId);
        if (adapterProgress > this.progress) {
            this.progress = Math.min(adapterProgress, targetCount);
        }
    }

    public boolean isCompleted() {
        return progress >= targetCount;
    }

    public float getProgressPercent() {
        if (targetCount <= 0) return 1.0f;
        return Math.min(1.0f, (float) progress / targetCount);
    }

    public String getFormattedProgress() {
        if (isCompleted()) return "§a" + progress + "§7/§a" + targetCount;
        return "§e" + progress + "§7/§e" + targetCount;
    }

    public String getDisplayLine() {
        String status = isCompleted() ? "§a✔ " : "§7○ ";
        return status + "§f" + displayText + " §7[ " + getFormattedProgress() + " §7]";
    }

    // ==================== Getters ====================

    public String getType() { return type; }
    public String getTargetId() { return targetId; }
    public int getTargetCount() { return targetCount; }
    public int getProgress() { return progress; }
    public String getDisplayText() { return displayText; }

    // ==================== 内部方法 ====================

    private static String makeDefaultDisplay(String type, String targetId) {
        return switch (type) {
            case "OCCUPY_CAMP" -> "占领 " + targetId;
            case "ATTACK_CAMP" -> "攻击 " + targetId;
            case "DEFEND_CAMP" -> "防守 " + targetId;
            case "UPGRADE_CAMP" -> "升级 " + targetId;
            case "COLLECT_ITEM" -> "收集 " + targetId;
            case "CRAFT_ITEM" -> "合成 " + targetId;
            case "KILL_MOB" -> "击杀 " + targetId;
            case "KILL_BOSS" -> "击败 Boss: " + targetId;
            case "REACH_LOCATION" -> "到达 " + targetId;
            case "TRADE_VILLAGER" -> "与村民交易";
            case "BUILD_STRUCTURE" -> "建造 " + targetId;
            case "REACH_LEVEL" -> "达到等级 " + targetId;
            default -> type + ": " + targetId;
        };
    }
}
