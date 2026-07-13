package io.github.shade.client.aigen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AiControlScreen extends Screen {

    private int cx;
    private String promptText = "";

    // ——— 配色方案：深色主题 + 紫罗兰品牌色 ———
    private static final int C_PANEL     = 0xE0181A2E; // 面板背景
    private static final int C_BORDER    = 0x302E3058; // 面板边框
    private static final int C_SHADOW    = 0x66000000; // 阴影
    private static final int C_ACCENT    = 0xFFA89BFF; // 淡紫罗兰主色
    private static final int C_ACCENT2   = 0xFF8B7BE8; // 淡紫罗兰深色
    private static final int C_SUBTLE    = 0xFF3A3C6A; // 分割线/装饰
    private static final int C_TEXT      = 0xFFE8E8F0;
    private static final int C_MUTED     = 0xFF8888AA;
    private static final int C_BTN       = 0xFF252645; // 按钮底
    private static final int C_BTN_H     = 0xFF36386A; // 按钮悬浮
    private static final int C_BTN_ON    = 0xFF2A2B5A; // 启用态
    private static final int C_INPUT     = 0xFF1C1D38; // 输入框
    private static final int C_GREEN     = 0xFF50E3A0; // 绿色（启用）
    private static final int C_RED       = 0xFFFF6B6B; // 红色（禁用）

    private static final int PW = 340;
    private int px, py;

    public AiControlScreen() {
        super(Component.literal(""));
    }

    @Override
    protected void init() {
        super.init();
        cx = width / 2;
        px = cx - PW / 2;
        py = (height - 340) / 2;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float d) {
        // —— 阴影 ——
        g.fill(px + 4, py + 4, px + PW + 4, py + 334, C_SHADOW);

        // —— 面板（带边框） ——
        g.fill(px - 1, py - 1, px + PW + 1, py + 331, C_BORDER);
        g.fill(px, py, px + PW, py + 330, C_PANEL);

        // —— 顶部装饰条 ——
        g.fill(px, py, px + 4, py + 330, C_ACCENT);

        int x = px + 18;
        int y = py + 16;

        // ═══════ 标题 ═══════
        drawText(g, "✦ AI 剧情生成", x, y, C_ACCENT);
        y += 22;
        g.fill(x, y, x + 40, y + 1, C_ACCENT);
        y += 14;

        // ═══════ 剧情进度 ═══════
        drawLabel(g, "剧情进度", x, y, C_MUTED);
        y += 13;
        drawText(g, "○ 第一章：苏醒  (进行中)", x, y, C_TEXT);
        y += 22;
        // 分割线
        g.fill(x, y, px + PW - 18, y + 1, C_SUBTLE);
        y += 12;

        // ═══════ AI 引擎 ═══════
        drawLabel(g, "AI 引擎", x, y, C_MUTED);
        y += 13;
        y = drawBtnRow(g, mx, my, x, y, new String[]{"DeepSeek", "Ollama", "§a启用", "§c禁用"},
                new int[]{85, 80, 50, 50},
                new int[]{C_ACCENT, C_ACCENT, C_GREEN, C_RED}, false);
        y += 1;
        y = drawBtnRow(g, mx, my, x, y, new String[]{"智谱AI(免费)", "讯飞星火(免费)", "Groq", "更多"},
                new int[]{90, 100, 50, 45},
                new int[]{C_ACCENT, C_ACCENT, C_ACCENT, C_ACCENT}, false);
        y += 16;
        g.fill(x, y, px + PW - 18, y + 1, C_SUBTLE);
        y += 12;

        // ═══════ 生成参数 ═══════
        drawLabel(g, "生成参数", x, y, C_MUTED);
        y += 13;
        y = drawBtnRow(g, mx, my, x, y, new String[]{"温度0.6", "温度0.8", "温度1.0", "短", "长"},
                new int[]{70, 70, 70, 35, 35},
                new int[]{C_ACCENT, C_ACCENT, C_ACCENT, C_ACCENT, C_ACCENT}, false);
        y += 16;
        g.fill(x, y, px + PW - 18, y + 1, C_SUBTLE);
        y += 12;

        // ═══════ 生成剧情 ═══════
        drawLabel(g, "生成剧情", x, y, C_MUTED);
        y += 13;

        // 输入框
        g.fill(x, y, px + PW - 18, y + 24, C_INPUT);
        g.fill(x, y, px + PW - 18, y + 1, C_ACCENT);
        String display = promptText.isEmpty() ? "输入剧情描述..." : promptText;
        drawText(g, display, x + 6, y + 7, promptText.isEmpty() ? 0xFF5A5A7A : C_TEXT);
        y += 30;

        // 生成按钮（主按钮样式）
        drawBtn(g, "▶ 生成剧情", cx - 54, y, 108, 28, mx, my, true);
        y += 36;

        // ═══════ 底部操作栏 ═══════
        g.fill(x, y, px + PW - 18, y + 1, C_SUBTLE);
        y += 8;

        drawBtn(g, "测试连接", x, y, 65, 18, mx, my, false);
        drawBtn(g, "状态", x + 69, y, 45, 18, mx, my, false);
        drawBtn(g, "自动", x + 118, y, 45, 18, mx, my, false);

        // 关闭按钮（右对齐）
        drawBtn(g, "关闭", px + PW - 56, y, 42, 18, mx, my, false);

        // 版本号
        drawText(g, "AI Studio v0.1", px + PW - 82, py + 320, 0xFF4A4A6A);
    }

    // —— 绘制一行按钮（每个按钮左侧带彩色条） ——
    private int drawBtnRow(GuiGraphics g, int mx, int my, int x, int y,
                           String[] labels, int[] w, int[] accentColors, boolean primary) {
        int curX = x;
        for (int i = 0; i < labels.length; i++) {
            boolean hover = mx >= curX && mx <= curX + w[i] && my >= y && my <= y + 18;
            int bg = hover ? C_BTN_H : C_BTN;
            // 如果是启用/禁用，用不同底色
            boolean isOn = labels[i].contains("启用");
            boolean isOff = labels[i].contains("禁用");
            if (isOn) bg = C_BTN_ON;
            if (isOff) bg = C_BTN;

            g.fill(curX, y, curX + w[i], y + 18, bg);
            // 左侧小色条（2px）
            int ac = i < accentColors.length ? accentColors[i] : C_ACCENT;
            g.fill(curX, y, curX + 2, y + 18, ac);
            // 底边微光
            g.fill(curX, y + 17, curX + w[i], y + 18, 0x22000000);

            // 渲染文字（支持 § 颜色代码）
            int tColor = C_TEXT;
            if (labels[i].startsWith("§a")) tColor = C_GREEN;
            else if (labels[i].startsWith("§c")) tColor = C_RED;
            String label = labels[i].replaceAll("§.", "");
            g.drawString(font, label, curX + 6, y + 4, tColor, false);
            curX += w[i] + 4;
        }
        return y + 20;
    }

    // —— 绘制单个按钮 ——
    private void drawBtn(GuiGraphics g, String t, int x, int y, int w, int h,
                         int mx, int my, boolean primary) {
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
        int bg = hover ? (primary ? C_ACCENT2 : C_BTN_H) : (primary ? C_ACCENT : C_BTN);
        g.fill(x, y, x + w, y + h, bg);
        if (!primary) {
            // 非主按钮：左侧小色条
            g.fill(x, y, x + 2, y + h, C_ACCENT);
        }
        // 底边微光
        g.fill(x, y + h - 1, x + w, y + h, 0x22000000);
        int tColor = primary ? 0xFFFFFFFF : C_TEXT;
        g.drawString(font, t, x + (w - font.width(t)) / 2, y + (h - font.lineHeight) / 2, tColor, false);
    }

    // —— 带小圆点的标签 ——
    private void drawLabel(GuiGraphics g, String t, int x, int y, int c) {
        g.fill(x - 4, y + 4, x - 2, y + 6, C_ACCENT); // 小圆点
        g.drawString(font, t, x + 2, y, c, false);
    }

    // —— 普通文字 ——
    private void drawText(GuiGraphics g, String t, int x, int y, int c) {
        g.drawString(font, t, x, y, c, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);
        int x = px + 18, by;

        // Row 1: DeepSeek / Ollama / 启用 / 禁用 (y=py+74)
        by = py + 74;
        if (hit(mx, my, x, by, 85, 18)) { runCmd("story ai provider deepseek"); return true; }
        if (hit(mx, my, x + 89, by, 80, 18)) { runCmd("story ai provider ollama"); return true; }
        if (hit(mx, my, x + 173, by, 50, 18)) { runCmd("story ai enable"); return true; }
        if (hit(mx, my, x + 227, by, 50, 18)) { runCmd("story ai disable"); return true; }

        // Row 2: 智谱AI / 讯飞星火 / Groq / 更多 (y=py+95)
        by = py + 95;
        if (hit(mx, my, x, by, 90, 18)) { runCmd("story ai recommend zhipu"); return true; }
        if (hit(mx, my, x + 94, by, 100, 18)) { runCmd("story ai recommend xunfei"); return true; }
        if (hit(mx, my, x + 198, by, 50, 18)) { runCmd("story ai recommend groq"); return true; }
        if (hit(mx, my, x + 252, by, 45, 18)) { runCmd("story ai recommend"); return true; }

        // Row 3: 温度 / 短 / 长 (y=py+137)
        by = py + 137;
        if (hit(mx, my, x, by, 70, 18)) { runCmd("story ai temperature 0.6"); return true; }
        if (hit(mx, my, x + 74, by, 70, 18)) { runCmd("story ai temperature 0.8"); return true; }
        if (hit(mx, my, x + 148, by, 70, 18)) { runCmd("story ai temperature 1.0"); return true; }
        if (hit(mx, my, x + 222, by, 35, 18)) { runCmd("story ai maxtokens 256"); return true; }
        if (hit(mx, my, x + 261, by, 35, 18)) { runCmd("story ai maxtokens 2048"); return true; }

        // ▶ 生成剧情 (y=py+206)
        by = py + 206;
        if (hit(mx, my, cx - 54, by, 108, 28)) { doGenerate(); return true; }

        // Bottom row: 测试连接 / 状态 / 自动 / 关闭 (y=py+251)
        by = py + 251;
        if (hit(mx, my, x, by, 65, 18)) { testConnection(); return true; }
        if (hit(mx, my, x + 69, by, 45, 18)) { runCmd("story ai status"); return true; }
        if (hit(mx, my, x + 118, by, 45, 18)) { runCmd("story ai autogen"); return true; }
        if (hit(mx, my, px + PW - 56, by, 42, 18)) { onClose(); return true; }

        return super.mouseClicked(mx, my, btn);
    }

    private boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (k == 256) { onClose(); return true; }
        if (k == 257 || k == 335) { doGenerate(); return true; }
        if (k == 259 && !promptText.isEmpty()) promptText = promptText.substring(0, promptText.length() - 1);
        char c = (k >= 32 && k <= 126) ? (char) k : 0;
        if (c != 0 && promptText.length() < 300) promptText += c;
        return super.keyPressed(k, s, m);
    }

    private void doGenerate() {
        if (promptText.isEmpty()) return;
        Minecraft.getInstance().setScreen(null);
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.connection.sendCommand("story ai generate " + promptText);
    }

    private void testConnection() {
        Minecraft.getInstance().setScreen(null);
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.connection.sendCommand("story ai test");
    }

    private void runCmd(String cmd) {
        Minecraft.getInstance().setScreen(null);
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.connection.sendCommand(cmd);
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 空实现，不渲染模糊背景
    }
}
