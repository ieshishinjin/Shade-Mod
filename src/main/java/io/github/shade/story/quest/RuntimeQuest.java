package io.github.shade.story.quest;

import io.github.shade.story.model.QuestData;
import io.github.shade.story.model.QuestObjectiveData;
import io.github.shade.story.model.QuestRewardData;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 运行时 Quest — 追踪进行中的 Quest
 *
 * 对应设计文档 §2.2 Quest 数据结构。
 * 在脚本加载时从 QuestData 创建，在运行时持有可变进度。
 */
public class RuntimeQuest {

    private final String questId;
    private final String questName;
    private final String questDescription;
    private final List<RuntimeObjective> objectives;
    private final QuestRewardData rewards;
    private final String onQuestComplete;
    private final String onQuestFail;

    private QuestState state;

    public enum QuestState {
        ACTIVE,
        COMPLETED,
        FAILED
    }

    public RuntimeQuest(QuestData data) {
        this.questId = data.getQuestId();
        this.questName = data.getQuestName();
        this.questDescription = data.getQuestDescription();
        this.rewards = data.getRewards();
        this.onQuestComplete = data.getOnQuestComplete();
        this.onQuestFail = data.getOnQuestFail();
        this.state = QuestState.ACTIVE;

        this.objectives = new ArrayList<>();
        if (data.getObjectives() != null) {
            for (QuestObjectiveData objData : data.getObjectives()) {
                this.objectives.add(new RuntimeObjective(objData));
            }
        }
    }

    // ==================== 进度管理 ====================

    /**
     * 更新匹配的 Objective 进度
     *
     * @param objectiveType Objective 类型
     * @param targetId      目标 ID
     * @param delta         进度增量
     * @return 是否有 Objective 被更新
     */
    public boolean updateProgress(String objectiveType, String targetId, int delta) {
        boolean updated = false;
        for (RuntimeObjective obj : objectives) {
            if (obj.getType().equals(objectiveType) &&
                    (obj.getTargetId().equals(targetId) || targetId == null)) {
                obj.addProgress(delta);
                updated = true;
            }
        }
        return updated;
    }

    /**
     * 从适配器同步进度
     */
    public void syncFromAdapters(ServerPlayer player) {
        for (RuntimeObjective obj : objectives) {
            obj.syncFromAdapter(player);
        }
    }

    /**
     * 是否已完成（所有 Objective 完成）
     */
    public boolean isCompleted() {
        if (objectives.isEmpty()) return false;
        return objectives.stream().allMatch(RuntimeObjective::isCompleted);
    }

    /**
     * 标记为已完成
     */
    public void complete() {
        this.state = QuestState.COMPLETED;
    }

    /**
     * 标记为失败
     */
    public void fail() {
        this.state = QuestState.FAILED;
    }

    // ==================== 显示 ====================

    /**
     * 获取 Quest 追踪 HUD 文本
     */
    public String[] getTrackingLines() {
        List<String> lines = new ArrayList<>();
        lines.add("§6✦ " + questName);
        for (RuntimeObjective obj : objectives) {
            lines.add(obj.getDisplayLine());
        }
        return lines.toArray(new String[0]);
    }

    // ==================== Getters ====================

    public String getQuestId() { return questId; }
    public String getQuestName() { return questName; }
    public String getQuestDescription() { return questDescription; }
    public List<RuntimeObjective> getObjectives() { return objectives; }
    public QuestRewardData getRewards() { return rewards; }
    public String getOnQuestComplete() { return onQuestComplete; }
    public String getOnQuestFail() { return onQuestFail; }
    public QuestState getState() { return state; }
}
