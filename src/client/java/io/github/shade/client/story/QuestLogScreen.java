package io.github.shade.client.story;

import io.github.shade.client.ShadeUI;
import io.github.shade.story.network.StoryPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 任务日志界面 — 按 L 键打开。
 *
 * 左栏任务列表，右栏详情+Objective 进度。
 * 统一风格参照 ShadeUI。
 */
public class QuestLogScreen extends Screen {

    private List<StoryPayloads.QuestLogPayload.QuestLogEntry> activeQuests;
    private List<String> completedQuestIds;
    private int selectedIndex = 0;
    private int scrollOffset = 0;

    private static final int ITEM_H = 14;

    public QuestLogScreen() { super(Component.literal("")); }

    @Override
    protected void init() {
        super.init();
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPlayNetworking.send(new StoryPayloads.QuestLogRequestPayload());
        }
    }

    public void updateData(List<StoryPayloads.QuestLogPayload.QuestLogEntry> active, List<String> completed) {
        this.activeQuests = active;
        this.completedQuestIds = completed;
        this.selectedIndex = 0;
        this.scrollOffset = 0;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float d) {
        renderBackground(g, mx, my, d);

        int cx = width / 2;
        g.drawString(font, ShadeUI.titlePrefix("任务日志"), cx - 35, 15, ShadeUI.GOLD, false);

        // 主面板背景
        int pw = Math.min(480, width - 40);
        int ph = Math.min(340, height - 60);
        int px = (width - pw) / 2, py = (height - ph) / 2;

        g.fill(px, py, px + pw, py + ph, ShadeUI.BG_PANEL);
        g.fill(px, py, px + pw, py + 2, ShadeUI.ACCENT);

        if (activeQuests == null) {
            g.drawString(font, "§7加载中...", cx - 25, py + ph / 2, ShadeUI.TEXT_MUTED, false);
            super.render(g, mx, my, d);
            return;
        }

        int lx = px + 12, ly = py + 16;
        int lw = pw / 2 - 16;
        int dx = px + pw / 2 + 4, dw = pw / 2 - 16;

        // 左侧：任务列表
        g.fill(lx - 2, ly, lx + lw + 2, py + ph - 14, ShadeUI.BG_SELECTED);
        g.drawString(font, "§7— 进行中 —", lx, ly, ShadeUI.TEXT_MUTED, false);
        int curY = ly + 14;

        if (activeQuests == null || activeQuests.isEmpty()) {
            g.drawString(font, "§7暂无活跃任务", lx + 4, curY, ShadeUI.TEXT_MUTED, false);
        } else {
            for (int i = 0; i < activeQuests.size(); i++) {
                var q = activeQuests.get(i);
                boolean sel = (i == selectedIndex);
                if (sel) g.fill(lx - 2, curY - 2, lx + lw + 2, curY + ITEM_H + 2, ShadeUI.BG_SELECTED);
                String label = (i == 0 ? "§6▶ " : "§7○ ") + q.questName();
                g.drawString(font, label, lx + 4, curY, sel ? ShadeUI.ACCENT : ShadeUI.TEXT_MAIN, false);
                curY += ITEM_H + 2;
            }
        }

        // 右侧：详情
        if (activeQuests != null && !activeQuests.isEmpty() && selectedIndex < activeQuests.size()) {
            var q = activeQuests.get(selectedIndex);
            int dy = ly;
            g.drawString(font, "§6✦ " + q.questName(), dx, dy, ShadeUI.TEXT_MAIN, false); dy += 14;
            g.drawString(font, "§7" + q.questDescription(), dx, dy, ShadeUI.TEXT_MUTED, false); dy += 16;
            g.fill(dx - 2, dy - 2, dx + dw + 2, dy - 1, ShadeUI.DIVIDER);

            for (int i = 0; i < q.objectiveTexts().length; i++) {
                boolean done = q.progress()[i] >= q.maxProgress()[i];
                String status = done ? "§a✔" : "§7○";
                String prog = done
                        ? "§a" + q.progress()[i] + "§7/§a" + q.maxProgress()[i]
                        : "§e" + q.progress()[i] + "§7/§e" + q.maxProgress()[i];
                g.drawString(font, status + " §f" + q.objectiveTexts()[i] + " §7[ " + prog + " §7]",
                        dx, dy + 4, done ? ShadeUI.GREEN : ShadeUI.TEXT_MAIN, false);
                dy += 12;
            }
        }

        // 底部
        int cnt = completedQuestIds != null ? completedQuestIds.size() : 0;
        g.drawString(font, "§7已完成任务: §e" + cnt, px + 12, py + ph - 16, ShadeUI.TEXT_MUTED, false);
        g.drawString(font, "§7↑↓ 选择  §eESC §7关闭", px + pw - 120, py + ph - 16, ShadeUI.TEXT_MUTED, false);

        super.render(g, mx, my, d);
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (k == 256) { onClose(); return true; }
        if (k == 264 || k == 265) {
            if (activeQuests == null || activeQuests.isEmpty()) return true;
            if (k == 264) selectedIndex = Math.min(selectedIndex + 1, activeQuests.size() - 1);
            else selectedIndex = Math.max(selectedIndex - 1, 0);
            return true;
        }
        return super.keyPressed(k, s, m);
    }

    @Override public boolean isPauseScreen() { return false; }
}
