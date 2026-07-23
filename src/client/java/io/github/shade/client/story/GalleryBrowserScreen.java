package io.github.shade.client.story;

import io.github.shade.client.ShadeUI;
import io.github.shade.story.network.StoryPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 画廊浏览界面 — CG 和结局条目。
 *
 * 统一配色方案见 ShadeUI。
 */
public class GalleryBrowserScreen extends Screen {

    private List<StoryPayloads.GalleryPayload.GalleryEntryData> entries;
    private Set<String> unlockedIds;
    private String selectedTab = "CG";
    private int scrollOffset = 0;

    public GalleryBrowserScreen(List<StoryPayloads.GalleryPayload.GalleryEntryData> entries, List<String> unlockedIds) {
        super(Component.literal(""));
        this.entries = entries;
        this.unlockedIds = Set.copyOf(unlockedIds);
    }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        int y = 40;

        // 标签切换
        addRenderableWidget(Button.builder(Component.literal(selectedTab.equals("CG") ? "§l[C] CG" : "[C] CG"),
                b -> { selectedTab = "CG"; scrollOffset = 0; init(); }).bounds(cx - 90, y, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal(selectedTab.equals("ENDING") ? "§l[E] 结局" : "[E] 结局"),
                b -> { selectedTab = "ENDING"; scrollOffset = 0; init(); }).bounds(cx + 10, y, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§7关闭"), b -> onClose())
                .bounds(cx + 100, y, 50, 20).build());

        // 返回按钮
        addRenderableWidget(Button.builder(Component.literal("§7← 返回剧情菜单"),
                b -> Minecraft.getInstance().setScreen(null)).bounds(10, 10, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float d) {
        renderBackground(g, mx, my, d);
        super.render(g, mx, my, d);

        int cx = width / 2;
        int top = 70;
        g.drawString(font, ShadeUI.titlePrefix("画廊"), cx - 30, 20, ShadeUI.GOLD, false);

        var filtered = entries.stream()
                .filter(e -> e.type().equals(selectedTab)).toList();
        boolean allUnlocked = filtered.stream().allMatch(e -> unlockedIds.contains(e.id()));
        int unlockedCount = (int) filtered.stream().filter(e -> unlockedIds.contains(e.id())).count();
        g.drawString(font, "§7" + unlockedCount + "/" + filtered.size()
                + (allUnlocked && !filtered.isEmpty() ? " §a✦ 全收集!" : ""), cx + 40, 24, ShadeUI.TEXT_MUTED, false);

        int y = top;
        int itemH = 40;
        int col = 0;
        int maxPerRow = Math.max(1, (width - 40) / 160);
        int cellW = Math.min(160, (width - 40) / maxPerRow);

        for (int i = scrollOffset; i < filtered.size() && y < height - 20; i++) {
            var entry = filtered.get(i);
            boolean unlocked = unlockedIds.contains(entry.id());
            int cellX = 20 + col * (cellW + 8);
            int cellY = y;

            // 卡片背景
            g.fill(cellX, cellY, cellX + cellW, cellY + itemH, unlocked ? ShadeUI.BG_CARD : ShadeUI.BG_LOCKED);

            // 左侧色条
            g.fill(cellX, cellY, cellX + 3, cellY + itemH, unlocked ? ShadeUI.ACCENT : 0xFF555577);

            // 状态标记 + 标题
            String marker = unlocked ? "§a✔" : "§7?";
            g.drawString(font, marker + " §f" + entry.title(), cellX + 8, cellY + 6, unlocked ? ShadeUI.TEXT_MAIN : ShadeUI.TEXT_MUTED, false);

            // 类型标签 + 解锁状态
            String typeTag = entry.type().equals("CG") ? "§b[CG]" : "§d[结局]";
            g.drawString(font, typeTag + (unlocked ? "" : " §7(未解锁)"), cellX + 8, cellY + 22, ShadeUI.TEXT_MUTED, false);

            col++;
            if (col >= maxPerRow) { col = 0; y += itemH + 4; }
        }

        // 如果没条目
        if (filtered.isEmpty()) {
            g.drawString(font, "§7暂无条目", cx - 20, height / 2, ShadeUI.TEXT_MUTED, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double deltaX, double deltaY) {
        var filtered = entries.stream().filter(e -> e.type().equals(selectedTab)).toList();
        int maxPerRow = Math.max(1, (width - 40) / 160);
        int rows = (filtered.size() + maxPerRow - 1) / maxPerRow;
        int maxScroll = Math.max(0, rows - (height - 90) / 44);
        if (deltaY < 0) scrollOffset = Math.min(scrollOffset + maxPerRow, filtered.size() - 1);
        else scrollOffset = Math.max(0, scrollOffset - maxPerRow);
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
