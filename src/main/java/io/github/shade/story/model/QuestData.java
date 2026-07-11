package io.github.shade.story.model;

import java.util.List;

/**
 * Quest 嵌入数据 — 对应设计文档 §2.2 Quest 数据结构
 *
 * 用于 QUEST_START / QUEST_COMPLETE 类型节点中嵌入的 Quest 定义。
 * 在脚本 JSON 中直接嵌入，运行时会被转换为 RuntimeQuest 实例。
 */
public class QuestData {

    /** Quest 唯一标识 */
    private String questId;

    /** Quest 显示名称 */
    private String questName;

    /** Quest 描述文本 */
    private String questDescription;

    /** Objective 列表（可跨多个系统） */
    private List<QuestObjectiveData> objectives;

    /** Quest 奖励 */
    private QuestRewardData rewards;

    /** Quest 完成后跳转的节点 ID */
    private String onQuestComplete;

    /** Quest 失败后跳转的节点 ID */
    private String onQuestFail;

    public QuestData() {}

    public String getQuestId() { return questId; }
    public void setQuestId(String questId) { this.questId = questId; }

    public String getQuestName() { return questName; }
    public void setQuestName(String questName) { this.questName = questName; }

    public String getQuestDescription() { return questDescription; }
    public void setQuestDescription(String questDescription) { this.questDescription = questDescription; }

    public List<QuestObjectiveData> getObjectives() { return objectives; }
    public void setObjectives(List<QuestObjectiveData> objectives) { this.objectives = objectives; }

    public QuestRewardData getRewards() { return rewards; }
    public void setRewards(QuestRewardData rewards) { this.rewards = rewards; }

    public String getOnQuestComplete() { return onQuestComplete; }
    public void setOnQuestComplete(String onQuestComplete) { this.onQuestComplete = onQuestComplete; }

    public String getOnQuestFail() { return onQuestFail; }
    public void setOnQuestFail(String onQuestFail) { this.onQuestFail = onQuestFail; }
}
