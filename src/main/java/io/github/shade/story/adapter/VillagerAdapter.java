package io.github.shade.story.adapter;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

import java.util.List;
import java.util.Map;

/**
 * 村民交易适配器 — 处理 TRADE_VILLAGER Objective
 *
 * 通过 Minecraft 原版统计查询玩家的交易和交谈记录。
 */
public class VillagerAdapter implements SystemAdapter {

    @Override
    public String getSystemId() { return "villager"; }

    @Override
    public List<String> getSupportedObjectiveTypes() {
        return List.of("TRADE_VILLAGER");
    }

    @Override
    public int getProgress(ServerPlayer player, String objectiveType, String targetId) {
        // 使用原版统计：与村民交易次数
        return player.getStats().getValue(Stats.CUSTOM.get(Stats.TRADED_WITH_VILLAGER));
    }

    @Override
    public int getMaxProgress(String objectiveType, String targetId) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean checkCondition(ServerPlayer player, String condition, String targetId, int value, String operator) {
        return false;
    }

    @Override
    public boolean executeAction(ServerLevel world, String action, String targetId, Map<String, Object> params) {
        return false;
    }

    @Override
    public String getStatusSummary(ServerLevel world) {
        return "Villager trade tracking active (via vanilla stats)";
    }
}
