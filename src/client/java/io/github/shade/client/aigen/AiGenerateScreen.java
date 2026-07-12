package io.github.shade.client.aigen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AiGenerateScreen extends Screen {

    private EditBox promptInput;
    private String statusText = "";
    private boolean generating = false;

    public AiGenerateScreen() {
        super(Component.literal("AI 剧情生成"));
    }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;

        promptInput = new EditBox(font, cx - 140, height / 2 - 30, 280, 20, Component.literal("输入剧情提示词"));
        promptInput.setMaxLength(200);
        promptInput.setHint(Component.literal("例如：生成一段在营地的日常对话"));
        addRenderableWidget(promptInput);

        addRenderableWidget(Button.builder(Component.literal("§l▶ 生成剧情"), btn -> doGenerate())
                .bounds(cx - 60, height / 2 + 5, 120, 24).build());

        addRenderableWidget(Button.builder(Component.literal("§7测试连接"), btn -> testConnection())
                .bounds(cx - 100, height / 2 + 40, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§7关闭"), btn -> onClose())
                .bounds(cx + 10, height / 2 + 40, 60, 20).build());

        setFocused(promptInput);
        promptInput.setFocused(true);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float d) {
        renderBackground(g, mx, my, d);
        super.render(g, mx, my, d);

        int cx = width / 2;
        g.drawString(font, "§l§6✦ AI 剧情生成", cx - 50, 20, 0xFFFFD700, false);
        g.drawString(font, "§7输入提示词后按 Enter 或点击生成", cx - 100, height / 2 - 55, 0xFFAAAAAA, false);

        if (generating) {
            g.drawString(font, "§e⟳ AI 正在生成剧情...", cx - 70, height / 2 + 75, 0xFFFFFF55, false);
        } else if (!statusText.isEmpty()) {
            g.drawString(font, statusText, cx - 70, height / 2 + 75, 0xFFAAAAAA, false);
        }
    }

    private void doGenerate() {
        String prompt = promptInput.getValue().trim();
        if (prompt.isEmpty()) {
            statusText = "§c请输入剧情提示词";
            return;
        }
        generating = true;
        statusText = "";
        Minecraft.getInstance().setScreen(null);
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.connection.sendCommand("story ai generate " + prompt);
    }

    private void testConnection() {
        statusText = "§e正在测试...";
        Minecraft.getInstance().setScreen(null);
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.connection.sendCommand("story ai test");
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
