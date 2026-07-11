package io.github.shade.story.model;

/**
 * 剧情节点类型 — 对应设计文档 §4.1
 */
public enum NodeType {
    /** 对话：显示角色对话，点击继续 */
    DIALOG,
    /** 选项：多个选项供玩家选择，不同选择走向不同分支 */
    CHOICE,
    /** 发布 Quest（可包含跨系统的复合 Objective） */
    QUEST_START,
    /** 更新 Quest 进度显示 */
    QUEST_UPDATE,
    /** 完成 Quest，发放奖励 */
    QUEST_COMPLETE,
    /** 条件判断：检查某个条件自动跳转 */
    CONDITION,
    /** 事件触发：传送、给予物品、切换BGM等 */
    EVENT,
    /** 结束：关闭对话框，结束当前章节 */
    END,
    /** 预留：AI 动态生成后续内容 */
    AI_GENERATE
}
