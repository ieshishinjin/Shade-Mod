package io.github.shade.story.adapter;

import io.github.shade.camp.Camp;
import io.github.shade.camp.CampManager;
import io.github.shade.camp.CampRewardHandler;
import io.github.shade.camp.CampWorldGenerator;
import io.github.shade.worldlevel.WorldLevel;
import io.github.shade.ShadeMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;

/**
 * Camp 系统适配器 — 将野外据点系统接入剧情框架
 *
 * 对应设计文档 §七 "与现有 Camp 系统的对接方式"
 *
 * 提供 Objective 类型：OCCUPY_CAMP, ATTACK_CAMP, DEFEND_CAMP, UPGRADE_CAMP
 * 提供条件类型：CAMP_STATUS
 * 提供事件类型：TRIGGER_CAMP
 */
public class CampAdapter implements SystemAdapter {

    @Override
    public String getSystemId() {
        return "camp";
    }

    @Override
    public List<String> getSupportedObjectiveTypes() {
        return List.of(
                "OCCUPY_CAMP",
                "ATTACK_CAMP",
                "DEFEND_CAMP",
                "UPGRADE_CAMP"
        );
    }

    @Override
    public int getProgress(ServerPlayer player, String objectiveType, String targetId) {
        CampManager manager = CampManager.getInstance(player.serverLevel());
        Camp camp = manager.getCamp(targetId);
        if (camp == null) return 0;

        return switch (objectiveType) {
            case "OCCUPY_CAMP" ->
                    camp.getStatus() == Camp.Status.CLEARED ? 1 : 0;
            case "ATTACK_CAMP" -> {
                // 已击杀数 = 总怪物数 - 当前存活数
                int total = camp.getTotalMobCount();
                int alive = camp.getActiveMobIds().size();
                yield Math.max(0, total - alive);
            }
            case "DEFEND_CAMP" ->
                    camp.getStatus() == Camp.Status.CLEARED ? 1 : 0;
            case "UPGRADE_CAMP" ->
                    0; // 升级功能预留，目前 Camp 系统无等级概念
            default -> 0;
        };
    }

    @Override
    public int getMaxProgress(String objectiveType, String targetId) {
        return switch (objectiveType) {
            case "OCCUPY_CAMP", "DEFEND_CAMP" -> 1;
            case "ATTACK_CAMP" -> {
                // 需要查 CampManager 获取总怪物数，但 getMaxProgress 接口未提供 world/player 上下文
                // TODO: 将 SystemAdapter.getMaxProgress 签名改为 getMaxProgress(ServerPlayer, String, String)
                //       以便查询 CampManager.getInstance(player.serverLevel())
                yield 1;
            }
            default -> 1;
        };
    }

    @Override
    public boolean checkCondition(ServerPlayer player, String condition, String targetId, int value, String operator) {
        if (!"CAMP_STATUS".equals(condition)) return false;

        CampManager manager = CampManager.getInstance(player.serverLevel());
        Camp camp = manager.getCamp(targetId);
        if (camp == null) return false;

        int statusCode = switch (camp.getStatus()) {
            case IDLE -> 0;
            case FIGHTING -> 1;
            case CLEARED -> 2;
        };

        return compareValues(statusCode, value, operator);
    }

    @Override
    public boolean executeAction(ServerLevel world, String action, String targetId, Map<String, Object> params) {
        if (!"TRIGGER_CAMP".equals(action)) return false;

        CampManager manager = CampManager.getInstance(world);
        Camp camp = manager.getCamp(targetId);
        if (camp == null) return false;

        // 强制激活营地（将 IDLE 状态切换到 FIGHTING）
        if (camp.getStatus() == Camp.Status.IDLE) {
            if (!camp.getActiveMobIds().isEmpty()) {
                // 有预生成怪物 → 激活
                camp.setStatus(Camp.Status.FIGHTING);
                ShadeMod.LOGGER.info("[adapter] 剧情触发据点激活: {}", targetId);
                return true;
            }
            // 需要重新生成怪物并激活
            manager.resetCamp(targetId);
            camp.setStatus(Camp.Status.FIGHTING);
            ShadeMod.LOGGER.info("[adapter] 剧情触发据点重置+激活: {}", targetId);
            return true;
        }
        return false;
    }

    @Override
    public String getStatusSummary(ServerLevel world) {
        CampManager manager = CampManager.getInstance(world);
        List<Camp> camps = manager.getAllCamps();

        if (camps.isEmpty()) return "No camps";

        int idle = 0, fighting = 0, cleared = 0;
        for (Camp c : camps) {
            switch (c.getStatus()) {
                case IDLE -> idle++;
                case FIGHTING -> fighting++;
                case CLEARED -> cleared++;
            }
        }

        return String.format("%d camps (IDLE:%d, FIGHTING:%d, CLEARED:%d)",
                camps.size(), idle, fighting, cleared);
    }

    // ==================== 工具方法 ====================

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
