package io.github.shade.client.story;

import io.github.shade.client.ShadeUI;
import io.github.shade.story.network.StoryPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class StoryMenuScreen extends Screen {

    private final List<StoryPayloads.StoryMenuPayload.ScriptInfo> scripts;
    private final String activeId;
    private final boolean hasActive;
    private int selectedIndex = -1;
    private Button actionButton, galleryButton, journalButton, triggerButton, closeButton;

    private static final int TOP = 50;
    private static final int BOTTOM_MARGIN = 80;
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
        int availH = height - TOP - BOTTOM_MARGIN;
        int itemH = 24;

        int listH = scripts.size() * itemH;
        int startY = TOP + Math.max(0, (availH - listH) / 2);

        for (int i = 0; i < scripts.size(); i++) {
            var s = scripts.get(i);
            int idx = i;
            String label = (s.completed() ? "√ " : (hasActive && s.id().equals(activeId) ? "▶ " : "○ ")) + s.title();
            addRenderableWidget(Button.builder(Component.literal(label), btn -> { selectedIndex = idx; updateActionButton(); })
                    .bounds(lx, startY + i * itemH, LEFT_W, 22).build());
        }

        int bottomY = height - BOTTOM_MARGIN;

        galleryButton = Button.builder(Component.literal("§6画廊"), btn -> {
                    onClose();
                    ClientPlayNetworking.send(new StoryPayloads.GalleryRequestPayload());
                })
                .bounds(lx, bottomY, 60, 20).build();
        addRenderableWidget(galleryButton);

        journalButton = Button.builder(Component.literal("§a日记"), btn -> {
                    onClose();
                    ClientPlayNetworking.send(new StoryPayloads.JournalRequestPayload());
                    ClientPlayNetworking.send(new StoryPayloads.BestiaryRequestPayload());
                })
                .bounds(lx + 65, bottomY, 60, 20).build();
        addRenderableWidget(journalButton);

        closeButton = Button.builder(Component.literal("§7关闭"), btn -> onClose())
                .bounds(lx + 130, bottomY, 65, 20).build();
        addRenderableWidget(closeButton);

        actionButton = Button.builder(Component.literal(""), btn -> doAction())
                .bounds(rx, bottomY, LEFT_W, 22).build();
        actionButton.visible = false;
        addRenderableWidget(actionButton);

        triggerButton = Button.builder(Component.literal("§c⚙ 触发器管理"), btn -> {
                    onClose();
                    ClientPlayNetworking.send(new StoryPayloads.TriggerListRequestPayload());
                })
                .bounds(rx, bottomY + 26, 100, 18).build();
        addRenderableWidget(triggerButton);
    }

    private void updateActionButton() {
        if (selectedIndex >= 0 && selectedIndex < scripts.size()) {
            var sel = scripts.get(selectedIndex);
            boolean isActive = hasActive && sel.id().equals(activeId);
            actionButton.setMessage(Component.literal(isActive ? "§l▶ 继续" : "§l▶ 开始"));
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
        int rx = cx + GAP / 2;
        int rw = LEFT_W;

        g.drawString(font, ShadeUI.titlePrefix("剧情选择"), cx - 38, 16, ShadeUI.GOLD, false);

        if (selectedIndex >= 0 && selectedIndex < scripts.size()) {
            var sel = scripts.get(selectedIndex);
            boolean isActive = hasActive && sel.id().equals(activeId);

            int availH = height - TOP - BOTTOM_MARGIN;
            int listH = scripts.size() * 24;
            int startY = TOP + Math.max(0, (availH - listH) / 2);

            int dy = startY;
            g.drawString(font, "§e" + sel.title(), rx, dy, ShadeUI.GOLD, false);
            dy += 18;
            g.drawString(font, sel.completed() ? "§a✦ 已完成" : (isActive ? "§b▶ 进行中" : "§7○ 未开始"), rx, dy, 0xFFAAAAAA, false);
            dy += 16;
            g.drawString(font, "§7ID: §f" + sel.id(), rx, dy, 0xFF888888, false);
            dy += 18;

            String desc = sel.description();
            if (desc != null && !desc.isEmpty()) {
                for (String line : wrapText(desc, rw)) {
                    g.drawString(font, "§7" + line, rx, dy, 0xFFAAAAAA, false);
                    dy += 10;
                }
            }
        }
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        for (String para : text.split("\n", -1)) {
            if (para.isEmpty()) { lines.add(""); continue; }
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < para.length(); i++) {
                String test = cur.toString() + para.charAt(i);
                if (font.width(test) > maxWidth && cur.length() > 0) {
                    lines.add(cur.toString());
                    cur = new StringBuilder(String.valueOf(para.charAt(i)));
                } else cur.append(para.charAt(i));
            }
            if (!cur.isEmpty()) lines.add(cur.toString());
        }
        return lines;
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
        if (k == 265) { selectedIndex = selectedIndex <= 0 ? scripts.size() - 1 : selectedIndex - 1; updateActionButton(); return true; }
        if (k == 264) { selectedIndex = selectedIndex >= scripts.size() - 1 ? 0 : selectedIndex + 1; updateActionButton(); return true; }
        if (k == 257 || k == 335) { doAction(); return true; }
        if (k == 71) { runCmd("story gallery"); return true; }
        if (k == 74) { onClose(); ClientPlayNetworking.send(new StoryPayloads.JournalRequestPayload()); ClientPlayNetworking.send(new StoryPayloads.BestiaryRequestPayload()); return true; }
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
