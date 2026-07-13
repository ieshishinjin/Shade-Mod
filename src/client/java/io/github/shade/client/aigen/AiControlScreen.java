package io.github.shade.client.aigen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AiControlScreen extends Screen {

    private int cx;
    private String promptText = "";

    // 浮动面板样式 — 无全屏遮罩，只有面板阴影
    private static final int C_PANEL   = 0xDD151530;
    private static final int C_LINE    = 0xFF44DDFF;
    private static final int C_ACCENT  = 0xFF44DDFF;
    private static final int C_TEXT    = 0xFFEEEEEE;
    private static final int C_MUTED   = 0xFF8888AA;
    private static final int C_BTN     = 0xFF2A2A50;
    private static final int C_BTN_H   = 0xFF444488;
    private static final int C_INPUT   = 0xFF1A1A3A;

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
        // 面板阴影 — 三层扩散阴影，制造浮动感
        int shadow1 = 0x22000000, shadow2 = 0x44000000, shadow3 = 0x66000000;
        g.fill(px + 4, py + 4, px + PW + 4, py + 334, shadow3);
        g.fill(px + 2, py + 3, px + PW + 2, py + 333, shadow2);
        g.fill(px + 1, py + 1, px + PW + 1, py + 331, shadow1);

        // 面板 — 半透明（能看到后面的游戏）
        g.fill(px, py, px + PW, py + 330, C_PANEL);
        // 顶边高光线
        g.fill(px, py, px + PW, py + 1, C_LINE);

        int x = px + 16;
        int y = py + 18;

        drawText(g, "§l✦ AI 剧情生成", x, y, C_ACCENT, 14);
        y += 26;

        drawText(g, "▎ 剧情进度", x, y, C_MUTED, 10);
        y += 14;
        drawText(g, "  ○ 第一章：苏醒  (进行中)", x, y, C_TEXT, 10);
        y += 22;

        drawText(g, "▎ AI 引擎", x, y, C_MUTED, 10);
        y += 14;
        y = drawBtnRow(g, mx, my, x, y, new String[]{"DeepSeek", "Ollama", "§a启用", "§c禁用"}, new int[]{90, 90, 50, 50});
        y += 2;
        y = drawBtnRow(g, mx, my, x, y, new String[]{"智谱AI(免费)", "讯飞星火(免费)", "Groq", "更多"}, new int[]{85, 95, 55, 45});
        y += 20;

        drawText(g, "▎ 生成参数", x, y, C_MUTED, 10);
        y += 14;
        y = drawBtnRow(g, mx, my, x, y, new String[]{"温度0.6", "温度0.8", "温度1.0", "短", "长"}, new int[]{65, 65, 65, 35, 35});
        y += 20;

        drawText(g, "▎ 生成剧情", x, y, C_MUTED, 10);
        y += 14;

        g.fill(x, y, px + PW - 16, y + 24, C_INPUT);
        g.fill(x, y, px + PW - 16, y + 1, 0xFF44DDFF);
        String display = promptText.isEmpty() ? "§7输入剧情描述..." : "§f" + promptText;
        drawText(g, display, x + 4, y + 6, promptText.isEmpty() ? 0xFF666688 : C_TEXT, 10);
        y += 30;

        drawBtn(g, "▶ 生成剧情", cx - 55, y, 110, 26, mx, my, true);
        y += 34;

        drawBtn(g, "测试连接", x, y, 70, 18, mx, my, false);
        drawBtn(g, "状态", x + 76, y, 50, 18, mx, my, false);
        drawBtn(g, "自动", x + 132, y, 50, 18, mx, my, false);
        drawBtn(g, "关闭", px + PW - 60, y, 44, 18, mx, my, false);

        drawText(g, "§7AI Studio v0.1", px + PW - 80, py + 318, 0xFF555566, 8);

        // 不调用 super.render() — 避免 Screen 默认的全屏模糊/暗色背景渲染
    }

    private int drawBtnRow(GuiGraphics g, int mx, int my, int x, int y, String[] labels, int[] w) {
        int curX = x;
        for (int i = 0; i < labels.length; i++) {
            boolean hover = mx >= curX && mx <= curX + w[i] && my >= y && my <= y + 18;
            g.fill(curX, y, curX + w[i], y + 18, hover ? C_BTN_H : C_BTN);
            g.fill(curX, y, curX + w[i], y + 1, 0xFF4488CC);
            drawText(g, labels[i], curX + (w[i] - font.width(labels[i])) / 2, y + 4, C_TEXT, 10);
            curX += w[i] + 4;
        }
        return y + 20;
    }

    private void drawBtn(GuiGraphics g, String t, int x, int y, int w, int h, int mx, int my, boolean p) {
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
        g.fill(x, y, x + w, y + h, hover ? C_BTN_H : C_BTN);
        g.fill(x, y, x + w, y + 1, p ? C_LINE : 0xFF4488CC);
        drawText(g, t, x + (w - font.width(t)) / 2, y + (h - font.lineHeight) / 2, C_TEXT, p ? 12 : 10);
    }

    private void drawText(GuiGraphics g, String t, int x, int y, int c, int s) {
        g.drawString(font, t, x, y, c, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);
        int x = px + 16, by = py + 80;
        if (hit(mx, my, x, by, 90, 18)) { runCmd("story ai provider deepseek"); return true; }
        if (hit(mx, my, x + 94, by, 90, 18)) { runCmd("story ai provider ollama"); return true; }
        if (hit(mx, my, x + 188, by, 50, 18)) { runCmd("story ai enable"); return true; }
        if (hit(mx, my, x + 242, by, 50, 18)) { runCmd("story ai disable"); return true; }
        by += 20;
        if (hit(mx, my, x, by, 85, 18)) { runCmd("story ai recommend zhipu"); return true; }
        if (hit(mx, my, x + 89, by, 95, 18)) { runCmd("story ai recommend xunfei"); return true; }
        if (hit(mx, my, x + 188, by, 55, 18)) { runCmd("story ai recommend groq"); return true; }
        if (hit(mx, my, x + 247, by, 45, 18)) { runCmd("story ai recommend"); return true; }
        by += 38;
        if (hit(mx, my, x, by, 65, 18)) { runCmd("story ai temperature 0.6"); return true; }
        if (hit(mx, my, x + 69, by, 65, 18)) { runCmd("story ai temperature 0.8"); return true; }
        if (hit(mx, my, x + 138, by, 65, 18)) { runCmd("story ai temperature 1.0"); return true; }
        if (hit(mx, my, x + 207, by, 35, 18)) { runCmd("story ai maxtokens 256"); return true; }
        if (hit(mx, my, x + 246, by, 35, 18)) { runCmd("story ai maxtokens 2048"); return true; }
        by += 38;
        if (hit(mx, my, cx - 55, by, 110, 26)) { doGenerate(); return true; }
        by += 34;
        if (hit(mx, my, x, by, 70, 18)) { testConnection(); return true; }
        if (hit(mx, my, x + 76, by, 50, 18)) { runCmd("story ai status"); return true; }
        if (hit(mx, my, x + 132, by, 50, 18)) { runCmd("story ai autogen"); return true; }
        if (hit(mx, my, px + PW - 60, by, 44, 18)) { onClose(); return true; }
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

    // 阻止 Screen 默认的全屏模糊 — 面板本身有阴影，背景完全可见
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 空实现，不渲染模糊背景
    }
}
