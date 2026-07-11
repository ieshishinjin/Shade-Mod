package io.github.shade.story.model;

/**
 * Quest 目标数据 — 对应设计文档 §2.3 Objective 扩展表
 *
 * 代表一个 Quest 中的单个目标。每个目标独立追踪进度。
 * type 字段使用设计文档中定义的 Objective 类型字符串。
 */
public class QuestObjectiveData {

    /** Objective 类型（如 OCCUPY_CAMP、COLLECT_ITEM、KILL_MOB 等） */
    private String type;

    /** 目标 ID（营地名称、物品 ID、实体 ID 等） */
    private String targetId;

    /** 所需数量 */
    private int count;

    /** 当前进度（运行时填充，JSON中不存储） */
    private transient int progress;

    /** 显示文本（如未提供则自动生成） */
    private String displayText;

    public QuestObjectiveData() {}

    public QuestObjectiveData(String type, String targetId, int count) {
        this.type = type;
        this.targetId = targetId;
        this.count = count;
        this.progress = 0;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }

    public String getDisplayText() { return displayText; }
    public void setDisplayText(String displayText) { this.displayText = displayText; }

    /** 是否已完成 */
    public boolean isCompleted() {
        return progress >= count;
    }
}
