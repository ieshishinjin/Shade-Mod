package io.github.shade.story.adapter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;

import java.util.List;
import java.util.Map;

/**
 * 战斗适配器 — 处理 KILL_MOB / KILL_BOSS Objective
 *
 * 通过 Minecraft 原版统计数据查询玩家的击杀数。
 * 对 KILL_MOB 查询 entity_killed 统计，
 * 对 KILL_BOSS 检查末影龙/凋灵计数。
 */
public class CombatAdapter implements SystemAdapter {

    @Override
    public String getSystemId() { return "combat"; }

    @Override
    public List<String> getSupportedObjectiveTypes() {
        return List.of("KILL_MOB", "KILL_BOSS");
    }

    @Override
    public int getProgress(ServerPlayer player, String objectiveType, String targetId) {
        if (targetId == null || targetId.isEmpty()) return 0;

        if ("KILL_BOSS".equals(objectiveType)) {
            // 检查末影龙和凋灵的击杀数
            if (targetId.contains("ender_dragon")) {
                return player.getStats().getValue(Stats.ENTITY_KILLED.get(EntityType.ENDER_DRAGON));
            }
            if (targetId.contains("wither")) {
                return player.getStats().getValue(Stats.ENTITY_KILLED.get(EntityType.WITHER));
            }
            return 0;
        }

        // KILL_MOB: 查询指定实体的击杀统计
        try {
            var entityType = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(targetId));
            if (entityType != null) {
                return player.getStats().getValue(Stats.ENTITY_KILLED.get(entityType));
            }
        } catch (Exception e) {
            // 实体类型不存在，返回事件驱动的进度
        }
        return 0;
    }

    @Override
    public int getMaxProgress(String objectiveType, String targetId) {
        return Integer.MAX_VALUE; // 由 Quest JSON 中的 count 决定
    }

    @Override
    public boolean checkCondition(ServerPlayer player, String condition, String targetId, int value, String operator) {
        if (!"KILL_COUNT".equals(condition)) return false;
        return false; // KILL_COUNT 已由 ConditionEvaluator 的默认检查器处理
    }

    @Override
    public boolean executeAction(ServerLevel world, String action, String targetId, Map<String, Object> params) {
        return false;
    }

    @Override
    public String getStatusSummary(ServerLevel world) {
        return "Combat tracking active (via vanilla stats)";
    }
}
