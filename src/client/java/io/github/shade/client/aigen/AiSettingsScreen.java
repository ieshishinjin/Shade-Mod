package io.github.shade.client.aigen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * AI 设置二级页面 — 选择模型 → 配置密钥 / 自定义端点
 */
public class AiSettingsScreen extends Screen {

    private EditBox apiKeyInput;
    private EditBox endpointInput;
    private EditBox modelInput;
    private String currentProvider = "deepseek";

    private static final int C_PANEL     = 0xE0181A2E;
    private static final int C_BORDER    = 0x302E3058;
    private static final int C_SHADOW    = 0x66000000;
    private static final int C_ACCENT    = 0xFFA89BFF;
    private static final int C_TEXT      = 0xFFE8E8F0;
    private static final int C_MUTED     = 0xFF8888AA;
    private static final int C_GREEN     = 0xFF50E3A0;
    private static final int C_WHITE     = 0xFFFFFFFF;

    private int pw, ph, px, py, cx;

    public AiSettingsScreen() {
        super(Component.literal(""));
    }

    @Override
    protected void init() {
        super.init();
        cx = width / 2;
        pw = Math.min(340, Math.max(200, width - 40));
        ph = Math.min(270, height - 20);
        px = Math.max(2, cx - pw / 2);
        py = Math.max(2, (height - ph) / 2);

        int x = px + 14;
        int iw = pw - 28;

        // — 1. 选择模型 —
        int y1 = py + 50;
        boolean isDeepseek = currentProvider.equals("deepseek");
        boolean isOllama = currentProvider.equals("ollama");
        boolean isCustom = currentProvider.equals("custom");

        addRenderableWidget(new ThemeButton(x, y1, 90, 18, "DeepSeek", C_ACCENT, C_TEXT, false, isDeepseek,
                b -> { currentProvider = "deepseek"; rebuild(); }));
        addRenderableWidget(new ThemeButton(x + 94, y1, 80, 18, "Ollama", C_ACCENT, C_TEXT, false, isOllama,
                b -> { currentProvider = "ollama"; rebuild(); }));
        addRenderableWidget(new ThemeButton(x + 178, y1, 68, 18, "自定义", 0xFFFFAA44, C_TEXT, false, isCustom,
                b -> { currentProvider = "custom"; rebuild(); }));

        // — 2. API 端点（仅自定义时显示） —
        int y2 = py + 78;
        if (isCustom) {
            endpointInput = new EditBox(font, x, y2, iw - 72, 18, Component.literal("API 端点"));
            endpointInput.setMaxLength(256);
            endpointInput.setValue("https://api.openai.com/v1/chat/completions");
            addRenderableWidget(endpointInput);
        }

        // — 3. 模型名（仅自定义时显示） —
        int y3 = py + 110;
        if (isCustom) {
            modelInput = new EditBox(font, x, y3, iw - 72, 18, Component.literal("模型名"));
            modelInput.setMaxLength(64);
            modelInput.setValue("gpt-4o-mini");
            addRenderableWidget(modelInput);
        }

        // — 4. API 密钥 —
        int y4 = isCustom ? py + 142 : py + 78;
        apiKeyInput = new EditBox(font, x, y4, iw - 66, 18, Component.literal("API 密钥"));
        apiKeyInput.setMaxLength(128);
        if (isDeepseek) apiKeyInput.setHint(Component.literal("sk-..."));
        else if (isOllama) apiKeyInput.setHint(Component.literal("留空即可，Ollama 本地不需要密钥"));
        else apiKeyInput.setHint(Component.literal("输入 API Key"));
        addRenderableWidget(apiKeyInput);

        // 保存按钮
        addRenderableWidget(new ThemeButton(x + iw - 62, y4, 62, 18, "保存", C_GREEN, C_WHITE, false, false,
                b -> saveConfig()));

        // — 5. 返回 —
        int y5 = (isCustom ? py + 178 : py + 120);
        addRenderableWidget(new ThemeButton(cx - 50, y5, 100, 18, "← 返回主菜单", C_ACCENT, C_TEXT, false, false,
                b -> Minecraft.getInstance().setScreen(new AiControlScreen())));

        // 关闭
        addRenderableWidget(new ThemeButton(px + pw - 52, py + 12, 38, 16, "关闭", 0xFF666688, C_MUTED, false, false,
                b -> onClose()));
    }

    private void rebuild() {
        String key = apiKeyInput != null ? apiKeyInput.getValue() : "";
        String ep = endpointInput != null ? endpointInput.getValue() : "";
        String mdl = modelInput != null ? modelInput.getValue() : "";
        init();
        if (apiKeyInput != null) apiKeyInput.setValue(key);
        if (endpointInput != null) endpointInput.setValue(ep);
        if (modelInput != null) modelInput.setValue(mdl);
    }

    private void saveConfig() {
        String key = apiKeyInput.getValue().trim();

        if (currentProvider.equals("custom")) {
            String ep = endpointInput != null ? endpointInput.getValue().trim() : "";
            String mdl = modelInput != null ? modelInput.getValue().trim() : "";
            if (ep.isEmpty() || mdl.isEmpty() || key.isEmpty()) return;
            // 自定义：设提供者 → 端点 → 模型 → 密钥
            runCmd("story ai provider custom");
            runCmd("story ai endpoint " + ep);
            runCmd("story ai model " + mdl);
            runCmd("story ai key " + key);
        } else if (currentProvider.equals("ollama")) {
            // Ollama：设提供者，密钥可选
            runCmd("story ai provider ollama");
            if (!key.isEmpty()) runCmd("story ai key " + key);
        } else {
            // DeepSeek
            if (key.isEmpty()) return;
            runCmd("story ai provider deepseek");
            runCmd("story ai key " + key);
        }
    }

    private void runCmd(String cmd) {
        onClose();
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.connection.sendCommand(cmd);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float d) {
        g.fill(px + 4, py + 4, px + pw + 4, py + ph, C_SHADOW);
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, C_BORDER);
        g.fill(px, py, px + pw, py + ph, C_PANEL);
        g.fill(px, py, px + 3, py + ph, C_ACCENT);

        int x = px + 14;
        boolean isCustom = currentProvider.equals("custom");

        // 标题
        g.drawString(font, "AI 设置", x, py + 15, C_ACCENT, false);
        g.fill(x, py + 27, x + 24, py + 28, C_ACCENT);

        // 选择模型
        g.fill(x - 4, py + 40, x - 2, py + 42, C_ACCENT);
        g.drawString(font, "选择模型", x + 2, py + 40, C_MUTED, false);

        if (isCustom) {
            // API 端点
            g.fill(x - 4, py + 68, x - 2, py + 70, C_ACCENT);
            g.drawString(font, "API 端点", x + 2, py + 68, C_MUTED, false);

            // 模型名
            g.fill(x - 4, py + 100, x - 2, py + 102, C_ACCENT);
            g.drawString(font, "模型名", x + 2, py + 100, C_MUTED, false);

            // API 密钥
            g.fill(x - 4, py + 132, x - 2, py + 134, C_ACCENT);
            g.drawString(font, "API 密钥", x + 2, py + 132, C_MUTED, false);

            g.drawString(font, "当前: 自定义", x, py + 170, 0xFF5A5A7A, false);
        } else {
            // API 密钥（非自定义）
            g.fill(x - 4, py + 68, x - 2, py + 70, C_ACCENT);
            g.drawString(font, "API 密钥", x + 2, py + 68, C_MUTED, false);

            String status = currentProvider.equals("deepseek") ? "当前: DeepSeek" : "当前: Ollama (本地)";
            g.drawString(font, status, x, py + 114, 0xFF5A5A7A, false);
        }

        g.drawString(font, "AI Studio v0.1", px + pw - 76, py + ph - 11, 0xFF4A4A6A, false);

        super.render(g, mx, my, d);
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (apiKeyInput.isFocused()) {
            if (k == 257 || k == 335) { saveConfig(); return true; }
            return apiKeyInput.keyPressed(k, s, m);
        }
        if (endpointInput != null && endpointInput.isFocused()) {
            if (k == 257 || k == 335) { endpointInput.setFocused(false); apiKeyInput.setFocused(true); return true; }
            return endpointInput.keyPressed(k, s, m);
        }
        if (modelInput != null && modelInput.isFocused()) {
            if (k == 257 || k == 335) { modelInput.setFocused(false); apiKeyInput.setFocused(true); return true; }
            return modelInput.keyPressed(k, s, m);
        }
        if (k == 256) { onClose(); return true; }
        return super.keyPressed(k, s, m);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        boolean handled = super.mouseClicked(mx, my, btn);
        if (!handled && btn == 0) {
            if (apiKeyInput != null) apiKeyInput.setFocused(false);
            if (endpointInput != null) endpointInput.setFocused(false);
            if (modelInput != null) modelInput.setFocused(false);
        }
        return handled;
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {}
}
