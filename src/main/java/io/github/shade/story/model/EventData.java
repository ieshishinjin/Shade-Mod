package io.github.shade.story.model;

import java.util.Map;

/**
 * 事件数据 — 对应设计文档 EVENT 节点
 *
 * 用于触发游戏内事件：传送、给予物品、切换 BGM、召唤怪物等。
 * 事件类型可扩展，通过适配器层注册新的事件处理器。
 */
public class EventData {

    /** 事件类型 */
    private String type;

    /** 事件参数值（按类型解释，如坐标、物品ID、声音ID等） */
    private String value;

    /** 扩展参数 */
    private Map<String, Object> params;

    public EventData() {}

    // === 内置事件类型常量 ===
    /** 执行命令 */
    public static final String TYPE_COMMAND = "COMMAND";
    /** 传送玩家到指定位置 */
    public static final String TYPE_TELEPORT = "TELEPORT";
    /** 给予物品 */
    public static final String TYPE_GIVE_ITEM = "GIVE_ITEM";
    /** 设置剧情 Flag */
    public static final String TYPE_SET_FLAG = "SET_FLAG";
    /** 播放音效 */
    public static final String TYPE_PLAY_SOUND = "PLAY_SOUND";
    /** 切换 BGM */
    public static final String TYPE_BGM = "BGM";
    /** 召唤怪物（配合 Camp 系统） */
    public static final String TYPE_SUMMON_MOB = "SUMMON_MOB";
    /** 触发 Camp 据点战斗 */
    public static final String TYPE_TRIGGER_CAMP = "TRIGGER_CAMP";
    /** 展示 CG 插画 */
    public static final String TYPE_SHOW_CG = "SHOW_CG";

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
}
