package io.github.shade.client.story;

import io.github.shade.client.ShadeUI;
import io.github.shade.story.network.StoryPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Set;

/**
 * 画廊浏览界面 — CG 和结局条目卡片网格展示。
 *
 * 统一风格参照 ShadeUI。
 */
public class GalleryBrowserScreen extends Screen {

    private final List<StoryPayloads.GalleryPayload.GalleryEntryData> entries;
    private final Set<String> unlockedIds;
    private String selectedTab = "CG";
    private int scrollOffset = 0;

    private static final int TOP = 70;
    private static final int ITEM_H = 40;

    public GalleryBrowserScreen(List<StoryPayloads.GalleryPayload.GalleryEntryData> entries, List<String> unlockedIds) {
        super(Component.literal(""));
        this.entries = entries;
        this.unlockedIds = Set.copyOf(unlockedIds);
    }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        addRenderableWidget(Button.builder(
                Component.literal(selectedTab.equals("CG") ? "§l[C] CG" : "[C] CG"),
                b -> { selectedTab = "CG"; scrollOffset = 0; refresh(); })
                .bounds(cx - 90, 35, 80, 20).build());
        addRenderableWidget(Button.builder(
                Component.literal(selectedTab.equals("ENDING") ? "§l[E] 结局" : "[E] 结局"),
                b -> { selectedTab = "ENDING"; scrollOffset = 0; refresh(); })
                .bounds(cx - 5, 35, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§7关闭"), b -> onClose())
                .bounds(cx + 100, 35, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§7← 剧情菜单"),
                b -> Minecraft.getInstance().setScreen(null)).bounds(10, 10, 100, 20).build());
    }

    private void refresh() { init(); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float d) {
        renderBackground(g, mx, my, d);
        super.render(g, mx, my, d);

        int cx = width / 2;
        g.drawString(font, ShadeUI.titlePrefix("画廊"), cx - 25, 15, ShadeUI.GOLD, false);

        var filtered = entries.stream().filter(e -> e.type().equals(selectedTab)).toList();
        int done = (int) filtered.stream().filter(e -> unlockedIds.contains(e.id())).count();
        boolean all = !filtered.isEmpty() && done == filtered.size();
        g.drawString(font, "§7" + done + "/" + filtered.size() + (all ? " §a✦ 全收集!" : ""),
                cx + 35, 19, ShadeUI.TEXT_MUTED, false);

        if (filtered.isEmpty()) {
            g.drawString(font, "§7暂无条目", cx - 25, height / 2, ShadeUI.TEXT_MUTED, false);
            return;
        }

        int cols = Math.max(1, (width - 40) / 160);
        int cellW = Math.min(160, (width - 40) / cols);
        int y = TOP;

        for (int i = scrollOffset; i < filtered.size(); i++) {
            int col = (i - scrollOffset) % cols;
            int row = (i - scrollOffset) / cols;
            y = TOP + row * (ITEM_H + 4);
            if (y + ITEM_H > height - 20) break;

            var e = filtered.get(i);
            boolean unlocked = unlockedIds.contains(e.id());
            int x = 20 + col * (cellW + 8);

            g.fill(x, y, x + cellW, y + ITEM_H, unlocked ? ShadeUI.BG_CARD : ShadeUI.BG_LOCKED);
            g.fill(x, y, x + 3, y + ITEM_H, unlocked ? ShadeUI.ACCENT : ShadeUI.BG_LOCKED);
            g.drawString(font, (unlocked ? "§a✔" : "§7?") + " §f" + truncate(e.title(), cellW - 20),
                    x + 8, y + 4, unlocked ? ShadeUI.TEXT_MAIN : ShadeUI.TEXT_MUTED, false);
            g.drawString(font, (e.type().equals("CG") ? "§b[CG]" : "§d[结局]") + (unlocked ? "" : " §7(未解锁)"),
                    x + 8, y + 20, ShadeUI.TEXT_MUTED, false);
        }
    }

    private static String truncate(String s, int maxW) {
        var f = Minecraft.getInstance().font;
        if (f.width(s) <= maxW) return s;
        return f.plainSubstrByWidth(s, maxW - 6) + "…";
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        var filtered = entries.stream().filter(e -> e.type().equals(selectedTab)).toList();
        int cols = Math.max(1, (width - 40) / 160);
        int rows = (filtered.size() + cols - 1) / cols;
        int maxRow = Math.max(0, rows - (height - 90) / 44);
        if (dy < 0) scrollOffset = Math.min(scrollOffset + cols, filtered.size() - 1);
        else scrollOffset = Math.max(0, scrollOffset - cols);
        return true;
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (k == 256) { onClose(); return true; }
        return super.keyPressed(k, s, m);
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public boolean isPauseScreen() { return false; }
}
