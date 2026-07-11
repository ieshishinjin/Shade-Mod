package io.github.shade.story.gallery;

/**
 * 画廊条目 — 对应一个可解锁的 CG 或结局
 *
 * 每个条目代表玩家在游戏中可以收集的一个"回忆"，
 * 可以是关键剧情的 CG 插图，也可以是某个分支结局。
 */
public class GalleryEntry {

    private String id;
    private String title;
    private String description;
    private String type;       // "CG" 或 "ENDING"
    private String texturePath; // CG 纹理路径（预留）
    private String scriptId;   // 关联的脚本 ID
    private String condition;  // 解锁条件描述

    public GalleryEntry() {}

    public static GalleryEntry cg(String id, String title, String description, String texturePath, String scriptId) {
        GalleryEntry e = new GalleryEntry();
        e.id = id;
        e.title = title;
        e.description = description;
        e.type = "CG";
        e.texturePath = texturePath;
        e.scriptId = scriptId;
        return e;
    }

    public static GalleryEntry ending(String id, String title, String description, String scriptId) {
        GalleryEntry e = new GalleryEntry();
        e.id = id;
        e.title = title;
        e.description = description;
        e.type = "ENDING";
        e.scriptId = scriptId;
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
    public String getTexturePath() { return texturePath; }
    public void setTexturePath(String path) { this.texturePath = path; }
    public String getScriptId() { return scriptId; }
    public void setScriptId(String sid) { this.scriptId = sid; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public String getDisplayLine(boolean unlocked) {
        String status = unlocked ? "§a✔" : "§7?";
        String typeTag = type.equals("CG") ? "§b[CG]" : "§d[结局]";
        return String.format("  %s %s §f%s", status, typeTag, title);
    }

    public String getDetailLines(boolean unlocked) {
        if (!unlocked) {
            return "§7??? " + (condition != null ? "§7(" + condition + ")" : "");
        }
        return "§f" + title + "\n§7" + description
                + (texturePath != null && !texturePath.isEmpty()
                    ? "\n§7纹理: §f" + texturePath : "");
    }
}
