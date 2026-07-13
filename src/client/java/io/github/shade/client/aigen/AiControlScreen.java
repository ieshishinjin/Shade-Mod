package io.github.shade.client.aigen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * AI 控制中心 — 面板化布局
 */
public class AiControlScreen extends Screen {

    private EditBox promptInput;

    // 布局常量
    private static final int PANEL_W = 320;
    private static final int PANEL_X = 0; // 动态计算
    private static final int COL_LEFT = 0;
    private static final int COL_RIGHT = 0;
    private static final int BTN_W1 = 100, BTN_W2 = 80, BTN_W3 = 60;

    private int cx;

    public AiControlScreen() {
        super(Component.literal("AI 控制中心"));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float d) {
        renderBackground(g, mx, my, d);
        super.render(g, mx, my, d);

        int px = cx - PANEL_W / 2;
        int textX = px + 12;

        // 标题行
        g.drawString(font, "§l§d✦ AI 剧情生成", textX, 18, 0xFFFF88FF, false);
        g.drawString(font, "§a● 已启用", px + PANEL_W - 60, 20, 0xFF55FF55, false);

        drawSection(g, "当前配置", px, 40, PANEL_W, 58);
        g.drawString(font, "§7引擎: §fDeepSeek   §7模型: §fdeepseek-chat", textX, 52, 0xFFEEEEEE, false);
        g.drawString(font, "§7温度: §f0.8        §7最大长度: §f1024", textX, 66, 0xFFEEEEEE, false);
        // 测试按钮在面板内右对齐
        g.drawString(font, "§7[测试连接]", px + PANEL_W - 55, 66, 0xFF888888, false);

        drawSection(g, "AI 引擎", px, 108, PANEL_W, 72);
        // 引擎按钮在面板内
        drawBtn(g, "DeepSeek", textX, 122, 80);       // col1
        drawBtn(g, "Ollama", textX + 85, 122, 80);     // col2
        drawBtn(g, "§a启用", textX + 180, 122, 50);    // col3
        drawBtn(g, "§c禁用", textX + 235, 122, 50);    // col4
        // 免费标签
        g.drawString(font, "§7免费推荐:", textX, 148, 0xFF888888, false);
        drawBtn(g, "智谱AI", textX + 65, 146, 60);
        drawBtn(g, "讯飞星火", textX + 130, 146, 65);
        drawBtn(g, "Groq", textX + 200, 146, 55);

        drawSection(g, "生成设置", px, 190, PANEL_W, 42);
        g.drawString(font, "§7温度:", textX, 204, 0xFF888888, false);
        drawBtn(g, "0.6", textX + 40, 202, 35);
        drawBtn(g, "0.8", textX + 78, 202, 35);
        drawBtn(g, "1.0", textX + 116, 202, 35);
        g.drawString(font, "§7长度:", textX + 165, 204, 0xFF888888, false);
        drawBtn(g, "短", textX + 200, 202, 35);
        drawBtn(g, "长", textX + 238, 202, 35);

        // 生成区域
        drawSection(g, "生成剧情", px, 242, PANEL_W, 78);
        // 输入框区域
        g.fill(textX, 260, px + PANEL_W - 12, 280, 0xCC222244);
        g.fill(textX, 260, px + PANEL_W - 12, 261, 0xFFFFD700);
        // 输入框内文（init 前 render 可能为空）
        String inputText = promptInput != null ? promptInput.getValue() : "";
        if (inputText.isEmpty()) {
            g.drawString(font, "§7输入剧情提示词...", textX + 4, 268, 0xFF666677, false);
        } else {
            g.drawString(font, "§f" + inputText, textX + 4, 268, 0xFFEEEEEE, false);
        }
        // 生成按钮
        drawBtn(g, "§l▶ 生成剧情", textX + 50, 284, 170);

        // 底栏
        g.fill(px, 332, px + PANEL_W, 334, 0x33FFD700);
        drawBtn(g, "§7推荐列表", textX, 338, 65);
        drawBtn(g, "§7查看状态", textX + 70, 338, 65);
        drawBtn(g, "§7自动生成", textX + 140, 338, 65);
        drawBtn(g, "§7关闭", px + PANEL_W - 55, 338, 40);
    }

    private void drawSection(GuiGraphics g, String title, int x, int y, int w, int h) {
        // 面板背景
        g.fill(x, y, x + w, y + h, 0xCC151530);
        // 顶边装饰线
        g.fill(x, y, x + w, y + 1, 0x44FFD700);
        // 标题标签
        g.drawString(font, "§7" + title, x + 8, y + 4, 0xFFFFD700, false);
    }

    private void drawBtn(GuiGraphics g, String text, int x, int y, int w) {
        boolean hover = false; // 简化实现
        int bg = hover ? 0xCC444466 : 0xCC222244;
        g.fill(x, y, x + w, y + 18, bg);
        g.fill(x, y, x + w, y + 1, 0x885555AA);
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + 4, 0xFFEEEEEE, false);
    }

    @Override
    protected void init() {
        super.init();
        cx = width / 2;
        int px = cx - PANEL_W / 2;
        int textX = px + 12;

        // 引擎选择
        addRenderableWidget(Button.builder(Component.literal("DeepSeek"), btn -> runCmd("story ai provider deepseek"))
                .bounds(textX, 122, 80, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Ollama"), btn -> runCmd("story ai provider ollama"))
                .bounds(textX + 85, 122, 80, 18).build());
        addRenderableWidget(Button.builder(Component.literal("§a启用"), btn -> runCmd("story ai enable"))
                .bounds(textX + 180, 122, 50, 18).build());
        addRenderableWidget(Button.builder(Component.literal("§c禁用"), btn -> runCmd("story ai disable"))
                .bounds(textX + 235, 122, 50, 18).build());

        // 免费服务商
        addRenderableWidget(Button.builder(Component.literal("智谱AI"), btn -> runCmd("story ai recommend zhipu"))
                .bounds(textX + 65, 146, 60, 18).build());
        addRenderableWidget(Button.builder(Component.literal("讯飞星火"), btn -> runCmd("story ai recommend xunfei"))
                .bounds(textX + 130, 146, 65, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Groq"), btn -> runCmd("story ai recommend groq"))
                .bounds(textX + 200, 146, 55, 18).build());

        // 参数
        addRenderableWidget(Button.builder(Component.literal("0.6"), btn -> runCmd("story ai temperature 0.6"))
                .bounds(textX + 40, 202, 35, 16).build());
        addRenderableWidget(Button.builder(Component.literal("0.8"), btn -> runCmd("story ai temperature 0.8"))
                .bounds(textX + 78, 202, 35, 16).build());
        addRenderableWidget(Button.builder(Component.literal("1.0"), btn -> runCmd("story ai temperature 1.0"))
                .bounds(textX + 116, 202, 35, 16).build());
        addRenderableWidget(Button.builder(Component.literal("短"), btn -> runCmd("story ai maxtokens 256"))
                .bounds(textX + 200, 202, 35, 16).build());
        addRenderableWidget(Button.builder(Component.literal("长"), btn -> runCmd("story ai maxtokens 2048"))
                .bounds(textX + 238, 202, 35, 16).build());

        // 行内按钮（测试、生成、底部）
        addRenderableWidget(Button.builder(Component.literal("§7测试连接"), btn -> testConnection())
                .bounds(px + PANEL_W - 60, 120, 50, 16).build());
        addRenderableWidget(Button.builder(Component.literal("§l▶ 生成剧情"), btn -> doGenerate())
                .bounds(textX + 50, 284, 170, 22).build());

        // 底栏
        addRenderableWidget(Button.builder(Component.literal("§7推荐列表"), btn -> runCmd("story ai recommend"))
                .bounds(textX, 338, 65, 16).build());
        addRenderableWidget(Button.builder(Component.literal("§7状态"), btn -> runCmd("story ai status"))
                .bounds(textX + 70, 338, 65, 16).build());
        addRenderableWidget(Button.builder(Component.literal("§7自动"), btn -> runCmd("story ai autogen"))
                .bounds(textX + 140, 338, 65, 16).build());
        addRenderableWidget(Button.builder(Component.literal("§7关闭"), btn -> onClose())
                .bounds(px + PANEL_W - 55, 338, 40, 16).build());
    }

    private void doGenerate() {
        String prompt = promptInput.getValue().trim();
        if (prompt.isEmpty()) return;
        Minecraft.getInstance().setScreen(null);
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.connection.sendCommand("story ai generate " + prompt);
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

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (k == 256) { onClose(); return true; }
        if (k == 257 || k == 335) { doGenerate(); return true; }
        return super.keyPressed(k, s, m);
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public boolean isPauseScreen() { return false; }
}
