package io.github.shade.client.story;

import io.github.shade.client.ShadeUI;
import io.github.shade.story.network.StoryPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 触发器管理界面 — 左栏列表，右栏详情。
 *
 * 统一风格参照 ShadeUI。
 */
public class TriggerManageScreen extends Screen {

    private List<StoryPayloads.TriggerListPayload.TriggerEntryData> triggers = List.of();
    private boolean isOperator = false;
    private boolean loaded = false;
    private int selectedIndex = 0;
    private int scrollOffset = 0;
    private Button deleteButton;

    private static final int LIST_W = 170;
    private static final int ITEM_H = 22;
    private static final int TOP = 65;

    public TriggerManageScreen() { super(Component.literal("")); }

    public void updateTriggers(StoryPayloads.TriggerListPayload payload) {
        this.triggers = payload.triggers();
        this.isOperator = payload.isOperator();
        this.loaded = true;
        if (selectedIndex >= triggers.size()) selectedIndex = Math.max(0, triggers.size() - 1);
        updateDeleteButton();
    }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        addRenderableWidget(Button.builder(Component.literal("§a↻ 刷新"), b ->
                        ClientPlayNetworking.send(new StoryPayloads.TriggerListRequestPayload()))
                .bounds(cx - 130, 35, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§7关闭"), b -> onClose())
                .bounds(cx + 100, 35, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§7← 剧情菜单"),
                b -> Minecraft.getInstance().setScreen(null)).bounds(10, 10, 100, 20).build());

        deleteButton = Button.builder(Component.literal("§c删除"), b -> {
                    if (selectedIndex < 0 || selectedIndex >= triggers.size()) return;
                    ClientPlayNetworking.send(new StoryPayloads.TriggerRemovePayload(triggers.get(selectedIndex).id()));
                }).bounds(cx + 80, height - 35, 60, 20).build();
        deleteButton.visible = false;
        addRenderableWidget(deleteButton);
    }

    private void updateDeleteButton() {
        if (deleteButton == null) return;
        deleteButton.visible = isOperator && loaded && selectedIndex >= 0 && selectedIndex < triggers.size();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float d) {
        renderBackground(g, mx, my, d);
        super.render(g, mx, my, d);

        int cx = width / 2;
        g.drawString(font, "§l§6✦ 触发器管理", cx - 40, 15, ShadeUI.GOLD, false);

        if (!loaded) {
            g.drawString(font, "§7加载中...", cx - 25, height / 2, ShadeUI.TEXT_MUTED, false);
            return;
        }
        if (triggers.isEmpty()) {
            g.drawString(font, "§7没有已配置的触发器", cx - 60, height / 2, ShadeUI.TEXT_MUTED, false);
            return;
        }

        g.drawString(font, "§7共 " + triggers.size() + " 个触发器"
                + (isOperator ? "" : " §c(仅 OP 可删除)"), cx + 35, 19, ShadeUI.TEXT_MUTED, false);

        int leftX = 20, rightX = leftX + LIST_W, detailX = rightX + 10, detailW = width - detailX - 20;

        // 左栏列表
        for (int i = scrollOffset; i < triggers.size(); i++) {
            int y = TOP + (i - scrollOffset) * ITEM_H;
            if (y + ITEM_H > height - 45) break;
            var t = triggers.get(i);
            boolean sel = (i == selectedIndex);
            if (sel) g.fill(leftX, y, rightX, y + ITEM_H, ShadeUI.BG_SELECTED);
            int barColor = switch (t.type()) {
                case "ZONE_ENTER" -> ShadeUI.GREEN;
                case "ITEM_PICKUP" -> ShadeUI.ACCENT;
                case "NPC_INTERACT" -> ShadeUI.GOLD;
                default -> ShadeUI.BG_LOCKED;
            };
            g.fill(leftX, y, leftX + 3, y + ITEM_H, barColor);
            g.drawString(font, ShadeUI.typeTag(t.type()) + " §f" + truncate(t.id(), LIST_W - 50),
                    leftX + 8, y + 4, ShadeUI.TEXT_MAIN, false);
        }

        // 右栏详情
        if (selectedIndex >= 0 && selectedIndex < triggers.size()) {
            var t = triggers.get(selectedIndex);
            g.fill(detailX, TOP, detailX + detailW, height - 45, ShadeUI.BG_DETAIL);
            int dy = TOP + 10;
            g.drawString(font, "§l§6" + t.id(), detailX + 10, dy, ShadeUI.GOLD, false); dy += 14;
            String typeName = switch (t.type()) {
                case "ZONE_ENTER" -> "§a区域触发";
                case "ITEM_PICKUP" -> "§d物品拾取";
                case "NPC_INTERACT" -> "§eNPC交互";
                default -> "§7未知";
            };
            g.drawString(font, typeName + (t.oneTime() ? " §7(一次性)" : " §7(可重复)"),
                    detailX + 10, dy, ShadeUI.TEXT_MUTED, false); dy += 14;
            g.fill(detailX + 10, dy, detailX + detailW - 10, dy + 1, ShadeUI.DIVIDER); dy += 6;
            g.drawString(font, "§7绑定脚本: §f" + t.scriptId(), detailX + 12, dy, ShadeUI.TEXT_MAIN, false); dy += 12;
            switch (t.type()) {
                case "ZONE_ENTER" -> {
                    g.drawString(font, "§7区域: (§f" + t.x1() + "§7, §f" + t.z1() + "§7) → (§f" + t.x2() + "§7, §f" + t.z2() + "§7)",
                            detailX + 12, dy, ShadeUI.TEXT_MAIN, false); dy += 12;
                    g.drawString(font, "§7范围: §f" + Math.abs(t.x2() - t.x1()) + " §7× §f" + Math.abs(t.z2() - t.z1()) + " §7格",
                            detailX + 12, dy, ShadeUI.TEXT_MUTED, false);
                }
                case "ITEM_PICKUP" -> g.drawString(font, "§7物品: §f" + t.targetId(), detailX + 12, dy, ShadeUI.TEXT_MAIN, false);
                case "NPC_INTERACT" -> g.drawString(font, "§7实体: §f" + t.targetId(), detailX + 12, dy, ShadeUI.TEXT_MAIN, false);
            }
        }
    }

    private static String truncate(String s, int maxW) {
        if (s == null || s.isEmpty()) return s;
        var f = Minecraft.getInstance().font;
        if (f.width(s) <= maxW) return s;
        return f.plainSubstrByWidth(s, maxW - 6) + "…";
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (dy < 0) scrollOffset = Math.min(scrollOffset + 1, Math.max(0, triggers.size() - 5));
        else scrollOffset = Math.max(0, scrollOffset - 1);
        return true;
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (k == 256) { onClose(); return true; }
        if (k == 264 || k == 265) {
            if (triggers.isEmpty()) return true;
            if (k == 264) selectedIndex = Math.min(selectedIndex + 1, triggers.size() - 1);
            else selectedIndex = Math.max(selectedIndex - 1, 0);
            int vis = (height - 100) / ITEM_H;
            if (selectedIndex < scrollOffset) scrollOffset = selectedIndex;
            if (selectedIndex >= scrollOffset + vis) scrollOffset = selectedIndex - vis + 1;
            updateDeleteButton();
            return true;
        }
        return super.keyPressed(k, s, m);
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public boolean isPauseScreen() { return false; }
}
