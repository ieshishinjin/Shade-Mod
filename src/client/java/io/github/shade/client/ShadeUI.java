package io.github.shade.client;

/**
 * Shade Mod UI 统一配色和布局常量
 *
 * 所有 GUI 界面共享此配色方案，确保风格统一。
 */
public class ShadeUI {

    private ShadeUI() {}

    // ==================== 主色调 ====================

    /** 面板/背景底色 */
    public static final int BG_PANEL     = 0xE0181A2E;
    /** 卡片/列表项背景 */
    public static final int BG_CARD      = 0xCC252645;
    /** 选中高亮背景 */
    public static final int BG_SELECTED  = 0x44252545;
    /** 详情面板背景（半透明深色） */
    public static final int BG_DETAIL    = 0x33181A2E;
    /** 锁定/未解锁背景 */
    public static final int BG_LOCKED    = 0xFF3A3C6A;
    /** 按钮默认底色 */
    public static final int BTN_NORMAL   = 0xFF252645;
    /** 按钮悬浮底色 */
    public static final int BTN_HOVER    = 0xFF36386A;
    /** 输入框底色 */
    public static final int BG_INPUT     = 0xFF1C1D38;

    /** 窗口阴影 */
    public static final int SHADOW       = 0x66000000;
    /** 边框 */
    public static final int BORDER       = 0x302E3058;

    // ==================== 强调色 ====================

    /** 主强调色（紫色） */
    public static final int ACCENT       = 0xFFA89BFF;
    /** 强调色深色变体 */
    public static final int ACCENT_DARK  = 0xFF8B7BE8;
    /** 金色（标题/高亮） */
    public static final int GOLD         = 0xFFFFD700;
    /** 分割线/装饰 */
    public static final int DIVIDER      = 0xFF3A3C6A;

    // ==================== 文字色 ====================

    /** 主文字色（亮白） */
    public static final int TEXT_MAIN    = 0xFFE8E8F0;
    /** 次级文字色（灰紫） */
    public static final int TEXT_MUTED   = 0xFF8888AA;
    /** 暗文字（底栏/水印） */
    public static final int TEXT_DIM     = 0xFF4A4A6A;
    /** 白色 */
    public static final int WHITE        = 0xFFFFFFFF;

    // ==================== 功能色 ====================

    /** 绿色（完成/启用） */
    public static final int GREEN        = 0xFF50E3A0;
    /** 红色（危险/禁用） */
    public static final int RED          = 0xFFFF6B6B;
    /** 金色 §6 */
    public static final int GOLD_MUTED   = 0xFF5A5A7A;

    // ==================== 对话框专用 ====================

    /** 对话框底色（略透明） */
    public static final int DLG_BG       = 0xDD1A1A2E;
    /** 对话框文字 */
    public static final int DLG_TEXT     = 0xFFEEEEEE;
    /** 对话框旁白 */
    public static final int DLG_NARR     = 0xFFAAAAAA;
    /** 对话框继续提示 */
    public static final int DLG_CONTINUE = 0x88AAAAAA;

    // ==================== 布局常量 ====================

    /** 列表项高度 */
    public static final int ITEM_H       = 22;
    /** 左栏色条宽度 */
    public static final int BAR_W        = 3;
    /** 标签按钮高度 */
    public static final int TAB_H        = 20;
    /** 按钮圆角高度（预留） */
    public static final int BTN_H        = 20;

    // ==================== 工具方法 ====================

    /** 参数分段色条颜色（基于类型名） */
    public static int barColor(String type) {
        return switch (type) {
            case "SCRIPT", "ZONE_ENTER" -> GREEN;
            case "NPC", "NPC_INTERACT" -> GOLD;
            case "FLAG", "ITEM_PICKUP", "ITEM" -> ACCENT;
            case "LOCATION" -> GREEN;
            case "MOB" -> RED;
            case "BLOCK" -> DIVIDER;
            default -> BG_LOCKED;
        };
    }

    /** 条目类型标签文本 */
    public static String typeTag(String type) {
        return switch (type) {
            case "SCRIPT" -> "§6[剧情]";
            case "FLAG" -> "§e[事件]";
            case "NPC" -> "§b[人物]";
            case "LOCATION" -> "§a[地点]";
            case "MOB" -> "§c[生物]";
            case "ITEM" -> "§e[物品]";
            case "BLOCK" -> "§7[方块]";
            case "ZONE_ENTER" -> "§a[区域]";
            case "ITEM_PICKUP" -> "§d[物品]";
            case "NPC_INTERACT" -> "§e[NPC]";
            default -> "§7[未知]";
        };
    }

    /** 标题前缀（金色粗体✦） */
    public static String titlePrefix(String title) {
        return "§l§6✦ " + title;
    }
}
