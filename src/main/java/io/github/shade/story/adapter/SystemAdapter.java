package io.github.shade.story.adapter;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;

/**
 * 游戏系统适配器接口 — 对应设计文档 §三 "游戏系统适配层"
 *
 * 每个具体的游戏系统（Camp、采集、战斗等）实现此接口，
 * 向剧情系统和 Quest 系统暴露其能力和状态。
 *
 * 适配器是剧情框架与具体游戏系统之间的桥梁。
 * 剧情框架通过适配器读取系统状态、执行操作、检查条件，
 * 而不需要知道具体系统的内部实现。
 */
public interface SystemAdapter {

    /**
     * 系统唯一标识（如 "camp", "inventory", "combat"）
     */
    String getSystemId();

    /**
     * 支持的 Objective 类型列表
     * 对应设计文档 §2.3 Objective 类型扩展表
     */
    List<String> getSupportedObjectiveTypes();

    /**
     * 获取某个 Objective 的当前进度
     *
     * @param player        目标玩家
     * @param objectiveType Objective 类型（如 "OCCUPY_CAMP"）
     * @param targetId      目标 ID（如营地名称、物品 ID）
     * @return 当前进度值（0 表示未开始，最大值表示完成）
     */
    int getProgress(ServerPlayer player, String objectiveType, String targetId);

    /**
     * 获取 Objective 的最大进度值（目标值）
     *
     * @param objectiveType Objective 类型
     * @param targetId      目标 ID
     * @return 最大进度值（1 表示布尔式完成，>1 表示计数式）
     */
    int getMaxProgress(String objectiveType, String targetId);

    /**
     * 检查某个条件是否满足
     *
     * @param player    目标玩家
     * @param condition 条件类型（如 "CAMP_STATUS"）
     * @param targetId  目标 ID
     * @param value     期望值
     * @param operator  运算符（">=", "==" 等）
     * @return 条件是否满足
     */
    boolean checkCondition(ServerPlayer player, String condition, String targetId, int value, String operator);

    /**
     * 执行系统操作（由 EVENT 节点触发）
     *
     * @param world    游戏世界
     * @param action   操作类型
     * @param targetId 目标 ID
     * @param params   扩展参数
     * @return 操作是否成功
     */
    boolean executeAction(ServerLevel world, String action, String targetId, Map<String, Object> params);

    /**
     * 获取系统状态摘要（供 AI 生成器使用）
     *
     * @param world 游戏世界
     * @return 人类可读的状态描述
     */
    String getStatusSummary(ServerLevel world);
}
