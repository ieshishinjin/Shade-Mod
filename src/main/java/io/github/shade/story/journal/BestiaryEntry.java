package io.github.shade.story.journal;

/**
 * 图鉴条目 — 记录玩家发现/击杀的生物、收集的物品和挖掘的方块
 *
 * 类型：
 *   MOB   — 首次击杀某种生物
 *   ITEM  — 首次收集某种物品
 *   BLOCK — 首次挖掘某种方块
 */
public class BestiaryEntry {

    private String id;
    private String title;
    private String description;
    private String type;        // MOB / ITEM / BLOCK
    private String category;    // 分类标签（如 "hostile", "passive", "resource" 等）
    private String iconTexture; // 可选：自定义图标纹理路径

    public BestiaryEntry() {}

    public static BestiaryEntry mob(String id, String title, String description, String category) {
        BestiaryEntry e = new BestiaryEntry();
        e.id = id;
        e.title = title;
        e.description = description;
        e.type = "MOB";
        e.category = category;
        return e;
    }

    public static BestiaryEntry item(String id, String title, String description, String category) {
        BestiaryEntry e = new BestiaryEntry();
        e.id = id;
        e.title = title;
        e.description = description;
        e.type = "ITEM";
        e.category = category;
        return e;
    }

    public static BestiaryEntry block(String id, String title, String description, String category) {
        BestiaryEntry e = new BestiaryEntry();
        e.id = id;
        e.title = title;
        e.description = description;
        e.type = "BLOCK";
        e.category = category;
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
    public String getCategory() { return category; }
    public void setCategory(String cat) { this.category = cat; }
    public String getIconTexture() { return iconTexture; }
    public void setIconTexture(String path) { this.iconTexture = path; }

    public String getDisplayLine(boolean discovered) {
        String status = discovered ? "§a✔" : "§7?";
        String typeTag = switch (type) {
            case "MOB" -> "§c[生物]";
            case "ITEM" -> "§e[物品]";
            case "BLOCK" -> "§7[方块]";
            default -> "§7[未知]";
        };
        return String.format("  %s %s §f%s", status, typeTag, title);
    }

    public String getDetailLines(boolean discovered) {
        if (!discovered) {
            return "§7???";
        }
        return "§f" + title
                + (category != null && !category.isEmpty()
                    ? " §7(" + category + ")" : "")
                + "\n§7" + description;
    }
}
