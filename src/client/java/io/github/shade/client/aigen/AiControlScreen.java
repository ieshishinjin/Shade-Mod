package io.github.shade.client.aigen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AiControlScreen extends Screen {

    private String promptText = "";

    // ——— 配色 ———
    private static final int C_PANEL     = 0xE0181A2E;
    private static final int C_BORDER    = 0x302E3058;
    private static final int C_SHADOW    = 0x66000000;
    private static final int C_ACCENT    = 0xFFA89BFF;
    private static final int C_SUBTLE    = 0xFF3A3C6A;
    private static final int C_TEXT      = 0xFFE8E8F0;
    private static final int C_MUTED     = 0xFF8888AA;
    private static final int C_INPUT     = 0xFF1C1D38;
    private static final int C_GREEN     = 0xFF50E3A0;
    private static final int C_RED       = 0xFFFF6B6B;

    // 面板尺寸（动态）
    private int pw, ph, px, py, cx;

    public AiControlScreen() {
        super(Component.literal(""));
    }

    @Override
    protected void init() {
        super.init();
        cx = width / 2;
        pw = Math.min(340, Math.max(200, width - 40));
        ph = Math.min(340, height - 20);
        px = Math.max(2, cx - pw / 2);
        py = Math.max(2, (height - ph) / 2);

        int x = px + 14;

        // 所有 y 坐标统一
        int y1 = py + 83;     // Row 1: DeepSeek / Ollama / 启用 / 禁用
        int y2 = py + 102;    // Row 2: 智谱AI / 讯飞星火 / Groq / 更多
        int y3 = py + 144;    // Row 3: 温度 / 短 / 长
        int y4 = py + 188;    // ▶ 生成剧情
        int y5 = py + 226;    // 底部操作栏

        // Row 1
        addRenderableWidget(new ThemeButton(x, y1, 66, 16, "DeepSeek", C_ACCENT, C_TEXT, false, false,
                b -> runCmd("story ai provider deepseek")));
        addRenderableWidget(new ThemeButton(x + 69, y1, 60, 16, "Ollama", C_ACCENT, C_TEXT, false, false,
                b -> runCmd("story ai provider ollama")));
        addRenderableWidget(new ThemeButton(x + 132, y1, 60, 16, "Claude", 0xFFD4A574, C_TEXT, false, false,
                b -> runCmd("story ai provider claude")));
        addRenderableWidget(new ThemeButton(x + 195, y1, 40, 16, "启用", C_GREEN, C_GREEN, false, true,
                b -> runCmd("story ai enable")));
        addRenderableWidget(new ThemeButton(x + 238, y1, 40, 16, "禁用", C_RED, C_RED, false, false,
                b -> runCmd("story ai disable")));

        // Row 2
        addRenderableWidget(new ThemeButton(x, y2, 70, 16, "智谱AI(免费)", C_ACCENT, C_TEXT, false, false,
                b -> runCmd("story ai recommend zhipu")));
        addRenderableWidget(new ThemeButton(x + 73, y2, 80, 16, "讯飞星火(免费)", C_ACCENT, C_TEXT, false, false,
                b -> runCmd("story ai recommend xunfei")));
        addRenderableWidget(new ThemeButton(x + 156, y2, 40, 16, "Groq", C_ACCENT, C_TEXT, false, false,
                b -> runCmd("story ai recommend groq")));
        addRenderableWidget(new ThemeButton(x + 199, y2, 36, 16, "更多", C_ACCENT, C_TEXT, false, false,
                b -> runCmd("story ai recommend")));

        // Row 3: 温度 / 短 / 长
        addRenderableWidget(new ThemeButton(x, y3, 62, 16, "温度0.6", C_ACCENT, C_TEXT, false, false,
                b -> runCmd("story ai temperature 0.6")));
        addRenderableWidget(new ThemeButton(x + 65, y3, 62, 16, "温度0.8", C_ACCENT, C_TEXT, false, false,
                b -> runCmd("story ai temperature 0.8")));
        addRenderableWidget(new ThemeButton(x + 130, y3, 62, 16, "温度1.0", C_ACCENT, C_TEXT, false, false,
                b -> runCmd("story ai temperature 1.0")));
        addRenderableWidget(new ThemeButton(x + 195, y3, 30, 16, "短", C_ACCENT, C_TEXT, false, false,
                b -> runCmd("story ai maxtokens 256")));
        addRenderableWidget(new ThemeButton(x + 228, y3, 30, 16, "长", C_ACCENT, C_TEXT, false, false,
                b -> runCmd("story ai maxtokens 2048")));

        // ▶ 生成剧情
        addRenderableWidget(new ThemeButton(cx - 48, y4, 96, 24, "▶ 生成剧情", C_ACCENT, 0xFFFFFFFF, true, false,
                b -> {
                    if (!promptText.isEmpty()) runCmd("story ai generate " + promptText);
                }));

        // 底部操作栏
        int bw = Math.min(54, (pw - 42) / 4);
        addRenderableWidget(new ThemeButton(x, y5, bw, 16, "测试连接", C_ACCENT, C_TEXT, false, false,
                b -> runCmd("story ai test")));
        addRenderableWidget(new ThemeButton(x + bw + 4, y5, bw, 16, "状态", C_ACCENT, C_TEXT, false, false,
                b -> runCmd("story ai status")));
        addRenderableWidget(new ThemeButton(x + (bw + 4) * 2, y5, bw, 16, "设置", C_ACCENT, C_TEXT, false, false,
                b -> Minecraft.getInstance().setScreen(new AiSettingsScreen())));
        addRenderableWidget(new ThemeButton(px + pw - 14 - bw, y5, bw, 16, "关闭", C_ACCENT, C_TEXT, false, false,
                b -> onClose()));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float d) {
        // —— 阴影 ——
        g.fill(px + 4, py + 4, px + pw + 4, py + ph, C_SHADOW);

        // —— 面板 ——
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, C_BORDER);
        g.fill(px, py, px + pw, py + ph, C_PANEL);
        g.fill(px, py, px + 3, py + ph, C_ACCENT);

        int x = px + 14;

        // ═══ 标题 ═══
        drawText(g, "✦ AI 剧情生成", x, py + 14, C_ACCENT);
        g.fill(x, py + 34, x + 36, py + 35, C_ACCENT);

        // ═══ 剧情进度 ═══
        drawLabel(g, "剧情进度", x, py + 46, C_MUTED);
        drawText(g, "○ 第一章：苏醒  (进行中)", x + 2, py + 57, C_TEXT);
        sep(g, x, py + 72);

        // ═══ AI 引擎 ═══
        drawLabel(g, "AI 引擎", x, py + 73, C_MUTED);

        // ═══ 生成参数 ═══
        sep(g, x, py + 122);
        drawLabel(g, "生成参数", x, py + 132, C_MUTED);

        // ═══ 生成剧情 ═══
        sep(g, x, py + 166);
        drawLabel(g, "生成剧情", x, py + 176, C_MUTED);

        // 输入框
        int iy = py + 187;
        g.fill(x, iy, px + pw - 14, iy + 20, C_INPUT);
        g.fill(x, iy, px + pw - 14, iy + 1, C_ACCENT);
        String display = promptText.isEmpty() ? "输入剧情描述..." : promptText;
        drawText(g, display, x + 5, iy + 6, promptText.isEmpty() ? 0xFF5A5A7A : C_TEXT);

        // 底部
        sep(g, x, py + 220);
        drawText(g, "AI Studio v0.1", px + pw - 76, py + ph - 11, 0xFF4A4A6A);

        // 渲染 widget（按钮）
        super.render(g, mx, my, d);
    }

    private void sep(GuiGraphics g, int x, int y) {
        g.fill(x, y, px + pw - 14, y + 1, C_SUBTLE);
    }

    private void drawLabel(GuiGraphics g, String t, int x, int y, int c) {
        g.fill(x - 4, y + 3, x - 2, y + 5, C_ACCENT);
        g.drawString(font, t, x + 2, y, c, false);
    }

    private void drawText(GuiGraphics g, String t, int x, int y, int c) {
        g.drawString(font, t, x, y, c, false);
    }

    private void runCmd(String cmd) {
        onClose();
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.connection.sendCommand(cmd);
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (k == 256) { onClose(); return true; }
        if (k == 257 || k == 335) {
            if (!promptText.isEmpty())
                runCmd("story ai generate " + promptText);
            return true;
        }
        if (k == 259 && !promptText.isEmpty()) promptText = promptText.substring(0, promptText.length() - 1);
        char c = (k >= 32 && k <= 126) ? (char) k : 0;
        if (c != 0 && promptText.length() < 300) promptText += c;
        return super.keyPressed(k, s, m);
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {}
}
