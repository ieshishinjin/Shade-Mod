package io.github.shade.story.model;

import java.util.List;

/**
 * 剧情节点 — 对应设计文档 §4.2 节点字段说明
 *
 * 一个通用节点类，通过 type 区分节点行为。
 * 不同节点类型使用不同的字段组合，未使用的字段为 null。
 */
public class StoryNode {

    /** 节点唯一标识 */
    private String id;

    /** 节点类型 */
    private NodeType type;

    /** 说话人名称（DIALOG, CHOICE） */
    private String speaker;

    /** 立绘资源路径（DIALOG, CHOICE） */
    private String portrait;

    /** 对话文本内容 */
    private String text;

    /** 选项列表（CHOICE） */
    private List<StoryChoice> options;

    /** Quest 数据（QUEST_START, QUEST_COMPLETE） */
    private QuestData quest;

    /** 条件判断（CONDITION） */
    private ConditionData condition;

    /** 事件数据（EVENT） */
    private EventData event;

    /** 默认跳转的下一个节点 ID */
    private String next;

    /** Quest 完成后的跳转节点（仅 QUEST_START） */
    private String onQuestComplete;

    /** Quest 失败后的跳转节点（仅 QUEST_START） */
    private String onQuestFail;

    public StoryNode() {}

    // ==================== 工厂方法 ====================

    public static StoryNode dialog(String id, String speaker, String text, String next) {
        StoryNode node = new StoryNode();
        node.id = id;
        node.type = NodeType.DIALOG;
        node.speaker = speaker;
        node.text = text;
        node.next = next;
        return node;
    }

    public static StoryNode end(String id) {
        StoryNode node = new StoryNode();
        node.id = id;
        node.type = NodeType.END;
        return node;
    }

    // ==================== Getters & Setters ====================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public NodeType getType() { return type; }
    public void setType(NodeType type) { this.type = type; }

    public String getSpeaker() { return speaker; }
    public void setSpeaker(String speaker) { this.speaker = speaker; }

    public String getPortrait() { return portrait; }
    public void setPortrait(String portrait) { this.portrait = portrait; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public List<StoryChoice> getOptions() { return options; }
    public void setOptions(List<StoryChoice> options) { this.options = options; }

    public QuestData getQuest() { return quest; }
    public void setQuest(QuestData quest) { this.quest = quest; }

    public ConditionData getCondition() { return condition; }
    public void setCondition(ConditionData condition) { this.condition = condition; }

    public EventData getEvent() { return event; }
    public void setEvent(EventData event) { this.event = event; }

    public String getNext() { return next; }
    public void setNext(String next) { this.next = next; }

    public String getOnQuestComplete() { return onQuestComplete; }
    public void setOnQuestComplete(String onQuestComplete) { this.onQuestComplete = onQuestComplete; }

    public String getOnQuestFail() { return onQuestFail; }
    public void setOnQuestFail(String onQuestFail) { this.onQuestFail = onQuestFail; }
}
