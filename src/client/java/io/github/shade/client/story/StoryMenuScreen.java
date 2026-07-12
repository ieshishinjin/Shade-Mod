package io.github.shade.client.story;

import io.github.shade.story.network.StoryPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class StoryMenuScreen extends Screen {

    private final List<StoryPayloads.StoryMenuPayload.ScriptInfo> scripts;
    private final String activeId;
    private final boolean hasActive;
    private int selectedIndex = -1;
    private Button actionButton, galleryButton, closeButton;

    private static final int TOP = 50;
    private static final int BOTTOM_MARGIN = 50;
    private static final int LEFT_W = 200;
    private static final int GAP = 12;

    public StoryMenuScreen(StoryPayloads.StoryMenuPayload payload) {
        super(Component.literal(""));
        this.hasActive = payload.hasActiveStory();
        this.activeId = payload.hasActiveStory() ? payload.activeScriptId() : null;
        this.scripts = payload.scripts();
    }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        int lx = cx - (LEFT_W + GAP / 2);
        int rx = cx + GAP / 2;
        int rw = LEFT_W;
        int availH = height - TOP - BOTTOM_MARGIN;
        int itemH = 24;

        // 左栏：剧情列表（始终显示）
        int listH = scripts.size() * itemH;
        int startY = TOP + Math.max(0, (availH - listH) / 2);

        for (int i = 0; i < scripts.size(); i++) {
            var s = scripts.get(i);
            int idx = i;
            String label = (s.completed() ? "√ " : (hasActive && s.id().equals(activeId) ? "▶ " : "○ ")) + s.title();
            int finalI = i;
            addRenderableWidget(Button.builder(Component.literal(label), btn -> {
                selectedIndex = finalI;
                updateActionButton();
            }).bounds(lx, startY + i * itemH, LEFT_W, 22).build());
        }

        // 左栏底部：画廊 / 关闭
        int bottomY = height - BOTTOM_MARGIN;
        galleryButton = Button.builder(Component.literal("§6画廊"), btn -> runCmd("story gallery"))
                .bounds(lx, bottomY, 95, 20).build();
        addRenderableWidget(galleryButton);
        closeButton = Button.builder(Component.literal("§7关闭"), btn -> onClose())
                .bounds(lx + 100, bottomY, 95, 20).build();
        addRenderableWidget(closeButton);

        // 右栏底部：操作按钮（选中后才显示）
        actionButton = Button.builder(Component.literal(""), btn -> doAction())
                .bounds(rx, bottomY, LEFT_W, 22).build();
        actionButton.visible = false;
        addRenderableWidget(actionButton);
    }

    private void updateActionButton() {
        if (selectedIndex >= 0 && selectedIndex < scripts.size()) {
            var sel = scripts.get(selectedIndex);
            boolean isActive = hasActive && sel.id().equals(activeId);
            if (isActive) actionButton.setMessage(Component.literal("§l▶ 继续"));
            else actionButton.setMessage(Component.literal("§l▶ 开始"));
            actionButton.visible = true;
        } else {
            actionButton.visible = false;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float d) {
        renderBackground(g, mx, my, d);
        super.render(g, mx, my, d);

        int cx = width / 2;
        int lx = cx - (LEFT_W + GAP / 2);
        int rx = cx + GAP / 2;
        int rw = LEFT_W;

        // 标题
        g.drawString(font, "§l§6✦ 剧情选择", cx - 38, 16, 0xFFFFD700, false);

        // 右栏详情（仅选中时显示）
        if (selectedIndex >= 0 && selectedIndex < scripts.size()) {
            var sel = scripts.get(selectedIndex);
            boolean isActive = hasActive && sel.id().equals(activeId);

            // 右栏顶部对齐左栏第一行
            int availH = height - TOP - BOTTOM_MARGIN;
            int itemH = 24;
            int listH = scripts.size() * itemH;
            int startY = TOP + Math.max(0, (availH - listH) / 2);

            int dy = startY;
            g.drawString(font, "§e" + sel.title(), rx, dy, 0xFFFFD700, false);
            dy += 18;
            g.drawString(font, sel.completed() ? "§a✦ 已完成" : (isActive ? "§b▶ 进行中" : "§7○ 未开始"), rx, dy, 0xFFAAAAAA, false);
            dy += 16;
            g.drawString(font, "§7ID: §f" + sel.id(), rx, dy, 0xFF888888, false);
        }
    }

    private void doAction() {
        if (selectedIndex >= 0 && selectedIndex < scripts.size()) {
            var sel = scripts.get(selectedIndex);
            if (hasActive && sel.id().equals(activeId)) runCmd("story advance");
            else runCmd("story start " + sel.id());
        }
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (k == 265) {
            selectedIndex = selectedIndex <= 0 ? scripts.size() - 1 : selectedIndex - 1;
            updateActionButton();
            return true;
        }
        if (k == 264) {
            selectedIndex = selectedIndex >= scripts.size() - 1 ? 0 : selectedIndex + 1;
            updateActionButton();
            return true;
        }
        if (k == 257 || k == 335) { doAction(); return true; }
        if (k == 71) { runCmd("story gallery"); return true; }
        if (k == 256) { onClose(); return true; }
        return super.keyPressed(k, s, m);
    }

    private void runCmd(String cmd) {
        Minecraft.getInstance().setScreen(null);
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.connection.sendCommand(cmd);
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public boolean isPauseScreen() { return false; }
}
