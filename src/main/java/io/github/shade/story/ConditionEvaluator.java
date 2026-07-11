package io.github.shade.story;

import io.github.shade.story.model.ConditionData;
import io.github.shade.story.model.StoryNode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * 条件求值器 — 对应设计文档 §4.3 条件系统
 *
 * 评估剧情 CONDITION 节点的条件表达式，返回 true/false。
 * 条件和适配器系统联动（Phase 6），目前提供基础实现 + 扩展点。
 */
public class ConditionEvaluator {

    /** 注册的条件检查器（类型 → 检查函数） */
    private static final Map<String, BiPredicate<ServerPlayer, ConditionData>> CHECKERS =
            new java.util.concurrent.ConcurrentHashMap<>();

    static {
        // 注册内置条件类型
        registerChecker(ConditionData.TYPE_FLAG_CHECK, ConditionEvaluator::checkFlag);
        registerChecker(ConditionData.TYPE_PLAYER_LEVEL, ConditionEvaluator::checkPlayerLevel);
        registerChecker(ConditionData.TYPE_LOCATION, ConditionEvaluator::checkLocation);
        // 以下条件类型需要适配器系统提供数据（Phase 6），目前为桩实现
        registerChecker(ConditionData.TYPE_CAMP_STATUS, ConditionEvaluator::checkCampStatusStub);
        registerChecker(ConditionData.TYPE_ITEM_COUNT, ConditionEvaluator::checkItemCountStub);
        registerChecker(ConditionData.TYPE_KILL_COUNT, ConditionEvaluator::checkKillCountStub);
    }

    /**
     * 注册自定义条件检查器（供适配器层调用）
     */
    public static void registerChecker(String type, BiPredicate<ServerPlayer, ConditionData> checker) {
        CHECKERS.put(type, checker);
    }

    /**
     * 评估条件节点，返回应该跳转的节点 ID
     *
     * @param player 目标玩家
     * @param node   条件节点
     * @return 跳转的节点 ID
     */
    public static String evaluate(ServerPlayer player, StoryNode node) {
        if (node.getCondition() == null) return node.getNext();

        ConditionData condition = node.getCondition();
        BiPredicate<ServerPlayer, ConditionData> checker = CHECKERS.get(condition.getType());

        boolean result = (checker != null) && checker.test(player, condition);

        return result ? condition.getNextIfTrue() : condition.getNextIfFalse();
    }

    // ==================== 内置条件实现 ====================

    /**
     * FLAG_CHECK：检查玩家剧情 Flag
     */
    private static boolean checkFlag(ServerPlayer player, ConditionData condition) {
        StoryManager manager = StoryManager.getInstance(player.serverLevel());
        StoryManager.PlayerProgress progress = manager.getProgress(player);
        if (progress == null) return false;

        Object value = progress.getFlags().get(condition.getTargetId());
        if (value == null) return false;

        return compareValues(value, condition.getValue(), condition.getOperator());
    }

    /**
     * PLAYER_LEVEL：检查玩家经验等级
     */
    private static boolean checkPlayerLevel(ServerPlayer player, ConditionData condition) {
        return compareValues(player.experienceLevel, condition.getValue(), condition.getOperator());
    }

    /**
     * LOCATION：检查玩家是否在指定区域
     */
    private static boolean checkLocation(ServerPlayer player, ConditionData condition) {
        Map<String, Object> params = condition.getParams();
        if (params == null) return false;

        try {
            int targetX = params.containsKey("x") ? ((Number) params.get("x")).intValue() : 0;
            int targetZ = params.containsKey("z") ? ((Number) params.get("z")).intValue() : 0;
            int radius = params.containsKey("radius") ? ((Number) params.get("radius")).intValue() : 10;

            BlockPos playerPos = player.blockPosition();
            double dist = Math.sqrt(
                    Math.pow(playerPos.getX() - targetX, 2) +
                    Math.pow(playerPos.getZ() - targetZ, 2)
            );
            return dist <= radius;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 桩实现（Phase 6 替换） ====================

    /**
     * CAMP_STATUS 桩实现 — 检查营地状态
     * TODO: Phase 6 接入 Camp 适配器后替换
     */
    private static boolean checkCampStatusStub(ServerPlayer player, ConditionData condition) {
        // 桩实现：默认返回 false
        // 实际实现将通过 CampAdapter 查询营地状态
        return false;
    }

    /**
     * ITEM_COUNT 桩实现 — 检查玩家背包物品数量
     * TODO: Phase 6 接入采集适配器后替换
     */
    private static boolean checkItemCountStub(ServerPlayer player, ConditionData condition) {
        // 桩实现：默认返回 false
        // 实际实现将检查 player.getInventory() 中指定物品数量
        return false;
    }

    /**
     * KILL_COUNT 桩实现 — 检查玩家击杀数量
     * TODO: Phase 6 接入战斗适配器后替换
     */
    private static boolean checkKillCountStub(ServerPlayer player, ConditionData condition) {
        // 桩实现：默认返回 false
        // 实际实现将通过玩家统计信息检查击杀数
        return false;
    }

    // ==================== 工具方法 ====================

    /**
     * 通用比较工具
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean compareValues(Object actual, int expected, String operator) {
        if (operator == null) return actual != null;

        int actualInt;
        if (actual instanceof Number) {
            actualInt = ((Number) actual).intValue();
        } else if (actual instanceof Boolean) {
            actualInt = (Boolean) actual ? 1 : 0;
        } else {
            try {
                actualInt = Integer.parseInt(actual.toString());
            } catch (NumberFormatException e) {
                actualInt = 0;
            }
        }

        return switch (operator) {
            case "==" -> actualInt == expected;
            case "!=" -> actualInt != expected;
            case ">" -> actualInt > expected;
            case "<" -> actualInt < expected;
            case ">=" -> actualInt >= expected;
            case "<=" -> actualInt <= expected;
            default -> actualInt == expected;
        };
    }
}
