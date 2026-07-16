package io.github.shade.story.adapter;

import io.github.shade.story.InventoryTracker;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * 物品适配器 — 处理 COLLECT_ITEM / CRAFT_ITEM Objective
 *
 * 从 InventoryTracker 的快照数据中查询玩家背包物品数量。
 * CRAFT_ITEM 在轮询模式下同样检查物品数量（与 COLLECT_ITEM 相同），
 * 精确区分需要事件驱动。
 */
public class InventoryAdapter implements SystemAdapter {

    @Override
    public String getSystemId() { return "inventory"; }

    @Override
    public List<String> getSupportedObjectiveTypes() {
        return List.of("COLLECT_ITEM", "CRAFT_ITEM");
    }

    @Override
    public int getProgress(ServerPlayer player, String objectiveType, String targetId) {
        if (targetId == null || targetId.isEmpty()) return 0;
        // 从 InventoryTracker 获取当前物品数量
        return InventoryTracker.getInstance().getItemCount(player, targetId);
    }

    @Override
    public int getMaxProgress(String objectiveType, String targetId) {
        return Integer.MAX_VALUE; // 由 Quest JSON 中的 count 决定实际上限
    }

    @Override
    public boolean checkCondition(ServerPlayer player, String condition, String targetId, int value, String operator) {
        if (!"ITEM_COUNT".equals(condition)) return false;
        int count = InventoryTracker.getInstance().getItemCount(player, targetId);
        return compareValues(count, value, operator);
    }

    @Override
    public boolean executeAction(ServerLevel world, String action, String targetId, Map<String, Object> params) {
        return false; // 物品适配器不执行动作
    }

    @Override
    public String getStatusSummary(ServerLevel world) {
        return "Inventory tracking active";
    }

    private boolean compareValues(int actual, int expected, String operator) {
        if (operator == null) return actual == expected;
        return switch (operator) {
            case "==" -> actual == expected;
            case "!=" -> actual != expected;
            case ">" -> actual > expected;
            case "<" -> actual < expected;
            case ">=" -> actual >= expected;
            case "<=" -> actual <= expected;
            default -> actual == expected;
        };
    }
}
