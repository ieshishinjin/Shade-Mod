package io.github.shade.story.model;

import java.util.List;
import java.util.Map;

/**
 * Quest 奖励数据 — 嵌入在 QuestData 中
 */
public class QuestRewardData {

    /** 奖励经验值 */
    private int exp;

    /** 奖励物品 ID 列表 */
    private List<String> items;

    /** 奖励剧情 Flag（flag名 → flag值） */
    private Map<String, String> flags;

    public QuestRewardData() {}

    public int getExp() { return exp; }
    public void setExp(int exp) { this.exp = exp; }

    public List<String> getItems() { return items; }
    public void setItems(List<String> items) { this.items = items; }

    public Map<String, String> getFlags() { return flags; }
    public void setFlags(Map<String, String> flags) { this.flags = flags; }
}
