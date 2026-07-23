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
 * 左栏滚动列表（条目 + 色条），右栏详情面板。
 * 统一风格参照 ShadeUI。
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

    private static final int LIST_W = 170;
    private static final int ITEM_H = 22;
    private static final int TOP = 65;

    public JournalScreen() { super(Component.literal("")); }

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

    // ==================== 初始化 ====================

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        int ly = 35;
        addRenderableWidget(Button.builder(
                Component.literal(selectedTab == 0 ? "§l✦ 日记" : "日记"),
                b -> { selectedTab = 0; selectedIndex = 0; scrollOffset = 0; refresh(); })
                .bounds(cx - 130, ly, 80, 20).build());
        addRenderableWidget(Button.builder(
                Component.literal(selectedTab == 1 ? "§l✦ 图鉴" : "图鉴"),
                b -> { selectedTab = 1; selectedIndex = 0; scrollOffset = 0; refresh(); })
                .bounds(cx - 40, ly, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§7关闭"), b -> onClose())
                .bounds(cx + 100, ly, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§7← 剧情菜单"),
                b -> Minecraft.getInstance().setScreen(null)).bounds(10, 10, 100, 20).build());
    }

    private void refresh() { init(); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float d) {
        renderBackground(g, mx, my, d);
        super.render(g, mx, my, d);
        if (selectedTab == 0) renderJournal(g);
        else renderBestiary(g);
    }

    private static String truncate(String s, int maxW) {
        var f = Minecraft.getInstance().font;
        if (f == null || s == null || f.width(s) <= maxW) return s;
        return f.plainSubstrByWidth(s, maxW - 6) + "…";
    }

    // ==================== 日记 ====================

    private void renderJournal(GuiGraphics g) {
        int cx = width / 2;
        g.drawString(font, ShadeUI.titlePrefix("日记"), cx - 25, 15, ShadeUI.GOLD, false);
        if (!journalLoaded) {
            g.drawString(font, "§7加载中...", cx - 25, height / 2, ShadeUI.TEXT_MUTED, false);
            return;
        }
        int total = journalEntries.size();
        int done = (int) journalEntries.stream().filter(e -> journalUnlockedIds.contains(e.id())).count();
        g.drawString(font, "§7" + done + "/" + total + (total > 0 && done == total ? " §a✦ 全部解锁!" : ""),
                cx + 35, 19, ShadeUI.TEXT_MUTED, false);

        int leftX = 20, rightX = leftX + LIST_W, detailX = rightX + 10, detailW = width - detailX - 20;
        for (int i = scrollOffset; i < journalEntries.size(); i++) {
            int y = TOP + (i - scrollOffset) * ITEM_H;
            if (y + ITEM_H > height - 10) break;
            var e = journalEntries.get(i);
            boolean unlocked = journalUnlockedIds.contains(e.id());
            boolean sel = (i == selectedIndex);
            if (sel) g.fill(leftX, y, rightX, y + ITEM_H, ShadeUI.BG_SELECTED);
            g.fill(leftX, y, leftX + 3, y + ITEM_H, unlocked ? ShadeUI.ACCENT : ShadeUI.BG_LOCKED);
            g.drawString(font, (unlocked ? "§a✔" : "§7?") + " " + ShadeUI.typeTag(e.type()) + " §f" + truncate(e.title(), LIST_W - 50),
                    leftX + 8, y + 4, unlocked ? ShadeUI.TEXT_MAIN : ShadeUI.TEXT_MUTED, false);
        }
        if (journalEntries.isEmpty()) {
            g.drawString(font, "§7暂无日记条目", cx - 45, height / 2, ShadeUI.TEXT_MUTED, false);
            return;
        }
        drawJournalDetail(g, detailX, detailW);
    }

    private void drawJournalDetail(GuiGraphics g, int dx, int dw) {
        if (selectedIndex < 0 || selectedIndex >= journalEntries.size()) return;
        var e = journalEntries.get(selectedIndex);
        boolean unlocked = journalUnlockedIds.contains(e.id());
        g.fill(dx, TOP, dx + dw, height - 20, ShadeUI.BG_DETAIL);
        if (!unlocked) {
            g.drawString(font, "§7???", dx + 10, TOP + 10, ShadeUI.TEXT_MUTED, false);
            return;
        }
        int dy = TOP + 10;
        g.drawString(font, "§l§6" + e.title(), dx + 10, dy, ShadeUI.GOLD, false); dy += 14;
        g.drawString(font, "§7" + ShadeUI.typeTag(e.type()).replaceAll("§.", "") + "记录", dx + 10, dy, ShadeUI.TEXT_MUTED, false); dy += 14;
        g.fill(dx + 10, dy, dx + dw - 10, dy + 1, ShadeUI.DIVIDER); dy += 6;
        if (e.description() != null) {
            for (String line : wordWrap(e.description(), dw - 20)) {
                if (dy > height - 30) break;
                g.drawString(font, "§7" + line, dx + 12, dy, ShadeUI.TEXT_MUTED, false);
                dy += 10;
            }
        }
    }

    // ==================== 图鉴 ====================

    private void renderBestiary(GuiGraphics g) {
        int cx = width / 2;
        g.drawString(font, ShadeUI.titlePrefix("图鉴"), cx - 25, 15, ShadeUI.GOLD, false);
        if (!bestiaryLoaded) {
            g.drawString(font, "§7加载中...", cx - 25, height / 2, ShadeUI.TEXT_MUTED, false);
            return;
        }
        int total = bestiaryEntries.size();
        int done = (int) bestiaryEntries.stream().filter(e -> bestiaryDiscoveredIds.contains(e.id())).count();
        g.drawString(font, "§7已发现 " + done + "/" + total + (total > 0 && done == total ? " §a✦ 全收集!" : ""),
                cx + 35, 19, ShadeUI.TEXT_MUTED, false);

        int leftX = 20, rightX = leftX + LIST_W, detailX = rightX + 10, detailW = width - detailX - 20;
        for (int i = scrollOffset; i < bestiaryEntries.size(); i++) {
            int y = TOP + (i - scrollOffset) * ITEM_H;
            if (y + ITEM_H > height - 10) break;
            var e = bestiaryEntries.get(i);
            boolean discovered = bestiaryDiscoveredIds.contains(e.id());
            boolean sel = (i == selectedIndex);
            if (sel) g.fill(leftX, y, rightX, y + ITEM_H, ShadeUI.BG_SELECTED);
            g.fill(leftX, y, leftX + 3, y + ITEM_H, discovered ? ShadeUI.GREEN : ShadeUI.BG_LOCKED);
            g.drawString(font, (discovered ? "§a✔" : "§7?") + " " + ShadeUI.typeTag(e.type()) + " §f" + truncate(e.title(), LIST_W - 50),
                    leftX + 8, y + 4, discovered ? ShadeUI.TEXT_MAIN : ShadeUI.TEXT_MUTED, false);
        }
        if (bestiaryEntries.isEmpty()) {
            g.drawString(font, "§7暂无图鉴条目", cx - 45, height / 2, ShadeUI.TEXT_MUTED, false);
            return;
        }
        drawBestiaryDetail(g, detailX, detailW);
    }

    private void drawBestiaryDetail(GuiGraphics g, int dx, int dw) {
        if (selectedIndex < 0 || selectedIndex >= bestiaryEntries.size()) return;
        var e = bestiaryEntries.get(selectedIndex);
        boolean discovered = bestiaryDiscoveredIds.contains(e.id());
        g.fill(dx, TOP, dx + dw, height - 20, ShadeUI.BG_DETAIL);
        if (!discovered) {
            g.drawString(font, "§7???", dx + 10, TOP + 10, ShadeUI.TEXT_MUTED, false);
            return;
        }
        int dy = TOP + 10;
        g.drawString(font, "§l§6" + e.title(), dx + 10, dy, ShadeUI.GOLD, false); dy += 14;
        String cat = e.category() != null && !e.category().isEmpty() ? " §7(" + e.category() + ")" : "";
        g.drawString(font, "§7图鉴" + cat, dx + 10, dy, ShadeUI.TEXT_MUTED, false); dy += 14;
        g.fill(dx + 10, dy, dx + dw - 10, dy + 1, ShadeUI.DIVIDER); dy += 6;
        if (e.description() != null) {
            for (String line : wordWrap(e.description(), dw - 20)) {
                if (dy > height - 30) break;
                g.drawString(font, "§7" + line, dx + 12, dy, ShadeUI.TEXT_MUTED, false);
                dy += 10;
            }
        }
    }

    // ==================== 工具 ====================

    private List<String> wordWrap(String text, int maxW) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        for (String p : text.split("\n", -1)) {
            if (p.isEmpty()) { lines.add(""); continue; }
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < p.length(); i++) {
                String test = cur.toString() + p.charAt(i);
                if (font.width(test) > maxW && cur.length() > 0) {
                    lines.add(cur.toString());
                    cur = new StringBuilder(String.valueOf(p.charAt(i)));
                } else cur.append(p.charAt(i));
            }
            if (!cur.isEmpty()) lines.add(cur.toString());
        }
        return lines;
    }

    // ==================== 输入 ====================

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int size = selectedTab == 0 ? journalEntries.size() : bestiaryEntries.size();
        if (dy < 0) scrollOffset = Math.min(scrollOffset + 1, Math.max(0, size - 5));
        else scrollOffset = Math.max(0, scrollOffset - 1);
        return true;
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (k == 256) { onClose(); return true; }
        if (k == 258) { selectedTab = 1 - selectedTab; selectedIndex = 0; scrollOffset = 0; refresh(); return true; }
        if (k == 264 || k == 265) {
            int size = selectedTab == 0 ? journalEntries.size() : bestiaryEntries.size();
            if (size == 0) return true;
            if (k == 264) selectedIndex = Math.min(selectedIndex + 1, size - 1);
            else selectedIndex = Math.max(selectedIndex - 1, 0);
            int vis = (height - 80) / ITEM_H;
            if (selectedIndex < scrollOffset) scrollOffset = selectedIndex;
            if (selectedIndex >= scrollOffset + vis) scrollOffset = selectedIndex - vis + 1;
            return true;
        }
        return super.keyPressed(k, s, m);
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public boolean isPauseScreen() { return false; }
}
