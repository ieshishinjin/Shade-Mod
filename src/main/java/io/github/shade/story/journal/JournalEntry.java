package io.github.shade.story.journal;

/**
 * 日记条目 — 记录剧情中的重要信息
 *
 * 类型：
 *   SCRIPT — 完成某个剧情章节
 *   FLAG   — 触发了关键剧情 Flag
 *   NPC    — 首次遇到某个重要 NPC
 *   LOCATION — 发现重要地点
 */
public class JournalEntry {

    private String id;
    private String title;
    private String description;
    private String type;       // SCRIPT / FLAG / NPC / LOCATION
    private String scriptId;   // 关联的脚本 ID（可选）
    private String condition;  // 解锁条件描述（例如 "完成 flag_xxx"）

    public JournalEntry() {}

    public static JournalEntry script(String id, String title, String description, String scriptId) {
        JournalEntry e = new JournalEntry();
        e.id = id;
        e.title = title;
        e.description = description;
        e.type = "SCRIPT";
        e.scriptId = scriptId;
        return e;
    }

    public static JournalEntry flag(String id, String title, String description, String condition) {
        JournalEntry e = new JournalEntry();
        e.id = id;
        e.title = title;
        e.description = description;
        e.type = "FLAG";
        e.condition = condition;
        return e;
    }

    public static JournalEntry npc(String id, String title, String description, String entityId) {
        JournalEntry e = new JournalEntry();
        e.id = id;
        e.title = title;
        e.description = description;
        e.type = "NPC";
        e.scriptId = entityId;
        return e;
    }

    public static JournalEntry location(String id, String title, String description) {
        JournalEntry e = new JournalEntry();
        e.id = id;
        e.title = title;
        e.description = description;
        e.type = "LOCATION";
        return e;
    }

    // ==================== Getters & Setters ====================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String desc) { this.description = desc; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getScriptId() { return scriptId; }
    public void setScriptId(String sid) { this.scriptId = sid; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public String getDisplayLine(boolean unlocked) {
        String status = unlocked ? "§a✔" : "§7?";
        String typeTag = switch (type) {
            case "SCRIPT" -> "§6[剧情]";
            case "FLAG" -> "§e[事件]";
            case "NPC" -> "§b[人物]";
            case "LOCATION" -> "§a[地点]";
            default -> "§7[未知]";
        };
        return String.format("  %s %s §f%s", status, typeTag, title);
    }

    public String getDetailLines(boolean unlocked) {
        if (!unlocked) {
            return "§7??? " + (condition != null ? "§7(" + condition + ")" : "");
        }
        return "§f" + title + "\n§7" + description
                + (scriptId != null && !scriptId.isEmpty()
                    ? "\n§7关联: §f" + scriptId : "");
    }
}
