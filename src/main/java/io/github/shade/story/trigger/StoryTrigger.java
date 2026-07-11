package io.github.shade.story.trigger;

/**
 * 剧情触发器 — 定义触发剧情脚本的条件
 *
 * 三种触发类型：
 *   ZONE_ENTER    — 玩家进入指定矩形区域
 *   ITEM_PICKUP   — 玩家拾取指定物品
 *   NPC_INTERACT  — 玩家右键点击指定实体
 *
 * 每个触发器绑定一个故事脚本 ID。
 * 触发器持久化存储在 world 数据目录。
 */
public class StoryTrigger {

    /** 触发器唯一标识 */
    private String id;

    /** 触发器类型 */
    private String type;

    /** 绑定的脚本 ID */
    private String scriptId;

    // === 区域触发参数 ===
    private int x1, z1, x2, z2;

    // === 物品/实体触发参数 ===
    private String targetId;   // 物品 ID 或实体 ID

    /** 是否只触发一次（默认 true） */
    private boolean oneTime = true;

    public StoryTrigger() {}

    // ==================== 工厂方法 ====================

    public static StoryTrigger zoneTrigger(String id, String scriptId, int x1, int z1, int x2, int z2) {
        StoryTrigger t = new StoryTrigger();
        t.id = id;
        t.type = "ZONE_ENTER";
        t.scriptId = scriptId;
        t.x1 = Math.min(x1, x2); t.z1 = Math.min(z1, z2);
        t.x2 = Math.max(x1, x2); t.z2 = Math.max(z1, z2);
        return t;
    }

    public static StoryTrigger itemTrigger(String id, String scriptId, String itemId) {
        StoryTrigger t = new StoryTrigger();
        t.id = id;
        t.type = "ITEM_PICKUP";
        t.scriptId = scriptId;
        t.targetId = itemId;
        return t;
    }

    public static StoryTrigger npcTrigger(String id, String scriptId, String entityId) {
        StoryTrigger t = new StoryTrigger();
        t.id = id;
        t.type = "NPC_INTERACT";
        t.scriptId = scriptId;
        t.targetId = entityId;
        return t;
    }

    // ==================== 检查方法 ====================

    /**
     * 检查玩家位置是否在区域内
     */
    public boolean isInZone(int px, int pz) {
        return px >= x1 && px <= x2 && pz >= z1 && pz <= z2;
    }

    /**
     * 检查物品 ID 是否匹配
     */
    public boolean matchesItem(String itemId) {
        return targetId != null && targetId.equals(itemId);
    }

    /**
     * 检查实体 ID 是否匹配
     */
    public boolean matchesEntity(String entityId) {
        return targetId != null && targetId.equals(entityId);
    }

    // ==================== Getters & Setters ====================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getScriptId() { return scriptId; }
    public void setScriptId(String scriptId) { this.scriptId = scriptId; }

    public int getX1() { return x1; }
    public void setX1(int x1) { this.x1 = x1; }
    public int getZ1() { return z1; }
    public void setZ1(int z1) { this.z1 = z1; }
    public int getX2() { return x2; }
    public void setX2(int x2) { this.x2 = x2; }
    public int getZ2() { return z2; }
    public void setZ2(int z2) { this.z2 = z2; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public boolean isOneTime() { return oneTime; }
    public void setOneTime(boolean oneTime) { this.oneTime = oneTime; }

    @Override
    public String toString() {
        return switch (type) {
            case "ZONE_ENTER" -> String.format("§6[区域] §f%s §7(%d,%d→%d,%d) → §e%s",
                    id, x1, z1, x2, z2, scriptId);
            case "ITEM_PICKUP" -> String.format("§6[物品] §f%s §7(%s) → §e%s",
                    id, targetId, scriptId);
            case "NPC_INTERACT" -> String.format("§6[NPC] §f%s §7(%s) → §e%s",
                    id, targetId, scriptId);
            default -> String.format("§6[%s] §f%s → §e%s", type, id, scriptId);
        };
    }
}
