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
 * 日记/图鉴界面 — 双标签页。
 *
 * 统一配色方案见 ShadeUI。
 */
public class JournalScreen extends Screen {

    private List<StoryPayloads.JournalPayload.JournalEntryData> journalEntries = List.of();
    private Set<String> journalUnlockedIds = Set.of();

    private List<StoryPayloads.BestiaryPayload.BestiaryEntryData> bestiaryEntries = List.of();
    private Set<String> bestiaryDiscoveredIds = Set.of();

    private boolean journalLoaded = false;
    private boolean bestiaryLoaded = false;

    private int selectedTab = 0;
    private int selectedIndex = 0;
    private int scrollOffset = 0;

    public JournalScreen() {
        super(Component.literal(""));
    }

    public void updateJournal(StoryPayloads.JournalPayload payload) {
        this.journalEntries = payload.entries();
        this.journalUnlockedIds = Set.copyOf(payload.unlockedIds());
        this.journalLoaded = true;
    }

    public void updateBestiary(StoryPayloads.BestiaryPayload payload) {
        this.bestiaryEntries = payload.entries();
        this.bestiaryDiscoveredIds = Set.copyOf(payload.discoveredIds());
        this.bestiaryLoaded = true;
    }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;

        addRenderableWidget(Button.builder(
                Component.literal(selectedTab == 0 ? "§l✦ 日记" : "日记"),
                b -> { selectedTab = 0; selectedIndex = 0; scrollOffset = 0; init(); })
                .bounds(cx - 130, 35, 80, 20).build());
        addRenderableWidget(Button.builder(
                Component.literal(selectedTab == 1 ? "§l✦ 图鉴" : "图鉴"),
                b -> { selectedTab = 1; selectedIndex = 0; scrollOffset = 0; init(); })
                .bounds(cx - 40, 35, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§7关闭"), b -> onClose())
                .bounds(cx + 100, 35, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§7← 剧情菜单"),
                b -> Minecraft.getInstance().setScreen(null)).bounds(10, 10, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float d) {
        renderBackground(g, mx, my, d);
        super.render(g, mx, my, d);
        int cx = width / 2;
        if (selectedTab == 0) renderJournalTab(g, cx);
        else renderBestiaryTab(g, cx);
    }

    // ==================== 日记标签页 ====================

    private void renderJournalTab(GuiGraphics g, int cx) {
        g.drawString(font, ShadeUI.titlePrefix("日记"), cx - 20, 15, ShadeUI.GOLD, false);
        if (!journalLoaded) {
            g.drawString(font, "§7加载中...", cx - 20, height / 2, ShadeUI.TEXT_MUTED, false);
            return;
        }
        int total = journalEntries.size();
        int unlockedCount = (int) journalEntries.stream()
                .filter(e -> journalUnlockedIds.contains(e.id())).count();
        g.drawString(font, "§7" + unlockedCount + "/" + total
                + (total > 0 && unlockedCount == total ? " §a✦ 全部解锁!" : ""),
                cx + 30, 19, ShadeUI.TEXT_MUTED, false);

        int leftX = 20, rightX = cx + 80, detailX = rightX + 10, detailWidth = width - detailX - 20;
        int topY = 65, itemH = 22;

        List<StoryPayloads.JournalPayload.JournalEntryData> displayList = journalEntries;

        for (int i = scrollOffset; i < displayList.size(); i++) {
            int y = topY + (i - scrollOffset) * itemH;
            if (y + itemH > height - 10) break;
            var entry = displayList.get(i);
            boolean isUnlocked = journalUnlockedIds.contains(entry.id());
            boolean isSelected = (i == selectedIndex);
            if (isSelected) g.fill(leftX, y, rightX, y + itemH, 0x44252545);
            g.fill(leftX, y, leftX + 3, y + itemH, isUnlocked ? ShadeUI.ACCENT : ShadeUI.BG_LOCKED);
            String marker = isUnlocked ? "§a✔" : "§7?";
            g.drawString(font, marker + " §f" + entry.title(), leftX + 8, y + 3, isUnlocked ? ShadeUI.TEXT_MAIN : ShadeUI.TEXT_MUTED, false);
        }

        if (displayList.isEmpty()) {
            g.drawString(font, "§7暂无日记条目", cx - 40, height / 2, ShadeUI.TEXT_MUTED, false);
            return;
        }

        if (selectedIndex >= 0 && selectedIndex < displayList.size()) {
            var selEntry = displayList.get(selectedIndex);
            boolean isUnlocked = journalUnlockedIds.contains(selEntry.id());
            g.fill(detailX, topY, detailX + detailWidth, height - 20, 0x33181A2E);
            if (!isUnlocked) {
                g.drawString(font, "§7???", detailX + 10, topY + 10, ShadeUI.TEXT_MUTED, false);
            } else {
                int dy = topY + 10;
                g.drawString(font, "§l§6" + selEntry.title(), detailX + 10, dy, ShadeUI.GOLD, false);
                dy += 14;
                g.drawString(font, "§7日记", detailX + 10, dy, ShadeUI.TEXT_MUTED, false);
                dy += 14;
                g.fill(detailX + 10, dy, detailX + detailWidth - 10, dy + 1, ShadeUI.DIVIDER);
                dy += 6;
                String desc = selEntry.description();
                if (desc != null && !desc.isEmpty()) {
                    for (String line : wordWrap(desc, detailWidth - 20)) {
                        if (dy > height - 30) break;
                        g.drawString(font, "§7" + line, detailX + 12, dy, ShadeUI.TEXT_MUTED, false);
                        dy += 10;
                    }
                }
            }
        }
    }

    // ==================== 图鉴标签页 ====================

    private void renderBestiaryTab(GuiGraphics g, int cx) {
        g.drawString(font, "§l§6✦ 图鉴", cx - 20, 15, ShadeUI.GOLD, false);
        if (!bestiaryLoaded) {
            g.drawString(font, "§7加载中...", cx - 20, height / 2, ShadeUI.TEXT_MUTED, false);
            return;
        }
        int total = bestiaryEntries.size();
        int discoveredCount = (int) bestiaryEntries.stream()
                .filter(e -> bestiaryDiscoveredIds.contains(e.id())).count();
        g.drawString(font, "§7已发现 " + discoveredCount + "/" + total
                + (total > 0 && discoveredCount == total ? " §a✦ 全收集!" : ""),
                cx + 30, 19, ShadeUI.TEXT_MUTED, false);

        int leftX = 20, rightX = cx + 80, detailX = rightX + 10, detailWidth = width - detailX - 20;
        int topY = 65, itemH = 22;

        List<StoryPayloads.BestiaryPayload.BestiaryEntryData> displayList = bestiaryEntries;

        for (int i = scrollOffset; i < displayList.size(); i++) {
            int y = topY + (i - scrollOffset) * itemH;
            if (y + itemH > height - 10) break;
            var entry = displayList.get(i);
            boolean isDiscovered = bestiaryDiscoveredIds.contains(entry.id());
            boolean isSelected = (i == selectedIndex);
            if (isSelected) g.fill(leftX, y, rightX, y + itemH, 0x44252545);
            g.fill(leftX, y, leftX + 3, y + itemH, isDiscovered ? ShadeUI.GREEN : ShadeUI.BG_LOCKED);
            String marker = isDiscovered ? "§a✔" : "§7?";
            g.drawString(font, marker + " §f" + entry.title(), leftX + 8, y + 3, isDiscovered ? ShadeUI.TEXT_MAIN : ShadeUI.TEXT_MUTED, false);
        }

        if (displayList.isEmpty()) {
            g.drawString(font, "§7暂无图鉴条目", cx - 40, height / 2, ShadeUI.TEXT_MUTED, false);
            return;
        }

        if (selectedIndex >= 0 && selectedIndex < displayList.size()) {
            var selEntry = displayList.get(selectedIndex);
            boolean isDiscovered = bestiaryDiscoveredIds.contains(selEntry.id());
            g.fill(detailX, topY, detailX + detailWidth, height - 20, 0x33181A2E);
            if (!isDiscovered) {
                g.drawString(font, "§7???", detailX + 10, topY + 10, ShadeUI.TEXT_MUTED, false);
            } else {
                int dy = topY + 10;
                g.drawString(font, "§l§6" + selEntry.title(), detailX + 10, dy, ShadeUI.GOLD, false);
                dy += 14;
                String cat = selEntry.category() != null && !selEntry.category().isEmpty()
                        ? " §7(" + selEntry.category() + ")" : "";
                g.drawString(font, "§7图鉴" + cat, detailX + 10, dy, ShadeUI.TEXT_MUTED, false);
                dy += 14;
                g.fill(detailX + 10, dy, detailX + detailWidth - 10, dy + 1, ShadeUI.DIVIDER);
                dy += 6;
                String desc = selEntry.description();
                if (desc != null && !desc.isEmpty()) {
                    for (String line : wordWrap(desc, detailWidth - 20)) {
                        if (dy > height - 30) break;
                        g.drawString(font, "§7" + line, detailX + 12, dy, ShadeUI.TEXT_MUTED, false);
                        dy += 10;
                    }
                }
            }
        }
    }

    // ==================== 文本工具 ====================

    /** 手动文本换行（基于 font.width 测量） */
    private List<String> wordWrap(String text, int maxWidth) {
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

    // ==================== 输入处理 ====================

    @Override
    public boolean mouseScrolled(double mx, double my, double deltaX, double deltaY) {
        if (deltaY < 0) scrollOffset = Math.min(scrollOffset + 1, getListSize() - 5);
        else scrollOffset = Math.max(0, scrollOffset - 1);
        return true;
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (k == 256) { onClose(); return true; }
        if (k == 258) {
            selectedTab = 1 - selectedTab;
            selectedIndex = 0; scrollOffset = 0; init();
            return true;
        }
        if (k == 264 || k == 265) {
            int size = getListSize();
            if (size == 0) return true;
            if (k == 264) selectedIndex = Math.min(selectedIndex + 1, size - 1);
            else selectedIndex = Math.max(selectedIndex - 1, 0);
            int visible = (height - 80) / 22;
            if (selectedIndex < scrollOffset) scrollOffset = selectedIndex;
            if (selectedIndex >= scrollOffset + visible) scrollOffset = selectedIndex - visible + 1;
            return true;
        }
        return super.keyPressed(k, s, m);
    }

    private int getListSize() {
        if (!journalLoaded && !bestiaryLoaded) return 0;
        return selectedTab == 0 ? journalEntries.size() : bestiaryEntries.size();
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public boolean isPauseScreen() { return false; }
}
