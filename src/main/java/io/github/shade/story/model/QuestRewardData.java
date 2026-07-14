package io.github.shade.story.model;

import java.util.List;
import java.util.Map;
import net.minecraft.util.RandomSource;

/**
 * Quest 奖励数据 — 嵌入在 QuestData 中
 *
 * 支持多种奖励方式：
 * - items: 直接奖励物品列表
 * - rewardPool: 随机奖励池，从中抽取 poolSize 个
 * - choiceRewards: 多选一奖励
 */
public class QuestRewardData {

    /** 奖励经验值 */
    private int exp;

    /** 奖励物品 ID 列表（直接发放） */
    private List<String> items;

    /** 随机奖励池（从 pool 中随机选） */
    private List<RewardPoolEntry> rewardPool;

    /** 从池中抽取数量 */
    private int poolSize = 1;

    /** 多选一奖励选项（key=显示名, value=物品列表） */
    private Map<String, List<String>> choiceRewards;

    /** 奖励剧情 Flag（flag名 → flag值） */
    private Map<String, String> flags;

    public QuestRewardData() {}

    /**
     * 获取实际发放的物品列表（处理随机池）
     */
    public List<String> getDeliveredItems(RandomSource random) {
        if (items != null && !items.isEmpty()) return items;

        // 从随机池中抽取
        if (rewardPool != null && !rewardPool.isEmpty()) {
            int totalWeight = rewardPool.stream().mapToInt(RewardPoolEntry::getWeight).sum();
            List<String> result = new java.util.ArrayList<>();
            int count = Math.min(poolSize, rewardPool.size());
            for (int i = 0; i < count; i++) {
                int roll = random.nextInt(totalWeight);
                int cumulative = 0;
                for (RewardPoolEntry entry : rewardPool) {
                    cumulative += entry.getWeight();
                    if (roll < cumulative) {
                        if (entry.items != null) result.addAll(entry.items);
                        if (entry.exp > 0) this.exp += entry.exp;
                        break;
                    }
                }
            }
            return result.isEmpty() ? null : result;
        }

        return null;
    }

    public int getExp() { return exp; }
    public void setExp(int exp) { this.exp = exp; }

    public List<String> getItems() { return items; }
    public void setItems(List<String> items) { this.items = items; }

    public List<RewardPoolEntry> getRewardPool() { return rewardPool; }
    public void setRewardPool(List<RewardPoolEntry> rewardPool) { this.rewardPool = rewardPool; }

    public int getPoolSize() { return poolSize; }
    public void setPoolSize(int poolSize) { this.poolSize = poolSize; }

    public Map<String, List<String>> getChoiceRewards() { return choiceRewards; }
    public void setChoiceRewards(Map<String, List<String>> choiceRewards) { this.choiceRewards = choiceRewards; }

    public Map<String, String> getFlags() { return flags; }
    public void setFlags(Map<String, String> flags) { this.flags = flags; }

    /**
     * 奖励池条目
     */
    public static class RewardPoolEntry {
        private List<String> items;
        private int weight = 10;
        private int exp;

        public List<String> getItems() { return items; }
        public void setItems(List<String> items) { this.items = items; }
        public int getWeight() { return weight; }
        public void setWeight(int weight) { this.weight = weight; }
        public int getExp() { return exp; }
        public void setExp(int exp) { this.exp = exp; }
    }
}
