package io.github.shade.story.model;

import java.util.Map;

/**
 * 条件数据 — 对应设计文档 §4.3
 *
 * 用于 CONDITION 类型节点，根据条件结果自动跳转不同分支。
 */
public class ConditionData {

    /** 条件类型 */
    private String type;

    /** 目标 ID（营地名称、物品 ID、Flag 名等） */
    private String targetId;

    /** 对比值（数量、等级等） */
    private int value;

    /** 运算符："==", ">=", "<=", ">", "<" */
    private String operator;

    /** 条件为真时跳转的节点 ID */
    private String nextIfTrue;

    /** 条件为假时跳转的节点 ID */
    private String nextIfFalse;

    /** 扩展参数（按条件类型自定义） */
    private Map<String, Object> params;

    public ConditionData() {}

    // === 条件类型常量 ===
    public static final String TYPE_CAMP_STATUS = "CAMP_STATUS";
    public static final String TYPE_ITEM_COUNT = "ITEM_COUNT";
    public static final String TYPE_KILL_COUNT = "KILL_COUNT";
    public static final String TYPE_PLAYER_LEVEL = "PLAYER_LEVEL";
    public static final String TYPE_FLAG_CHECK = "FLAG_CHECK";
    public static final String TYPE_LOCATION = "LOCATION";

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getNextIfTrue() { return nextIfTrue; }
    public void setNextIfTrue(String nextIfTrue) { this.nextIfTrue = nextIfTrue; }

    public String getNextIfFalse() { return nextIfFalse; }
    public void setNextIfFalse(String nextIfFalse) { this.nextIfFalse = nextIfFalse; }

    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
}
