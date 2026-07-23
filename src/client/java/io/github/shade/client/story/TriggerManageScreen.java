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

/**
 * 触发器管理界面 — 左栏列表，右栏详情。
 *
 * 统一配色方案见 ShadeUI。
 */
public class TriggerManageScreen extends Screen {

    private List<StoryPayloads.TriggerListPayload.TriggerEntryData> triggers = List.of();
    private boolean isOperator = false;
    private boolean loaded = false;
    private int selectedIndex = 0;
    private int scrollOffset = 0;
    private Button deleteButton;

    public TriggerManageScreen() {
        super(Component.literal(""));
    }

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

        // 刷新按钮
        addRenderableWidget(Button.builder(Component.literal("§a↻ 刷新"), b -> {
                    ClientPlayNetworking.send(new StoryPayloads.TriggerListRequestPayload());
                }).bounds(cx - 130, 35, 60, 20).build());

        // 关闭按钮
        addRenderableWidget(Button.builder(Component.literal("§7关闭"), b -> onClose())
                .bounds(cx + 100, 35, 50, 20).build());

        // 返回剧情菜单
        addRenderableWidget(Button.builder(Component.literal("§7← 剧情菜单"),
                b -> Minecraft.getInstance().setScreen(null)).bounds(10, 10, 100, 20).build());

        // 删除按钮
        deleteButton = Button.builder(Component.literal("§c删除"), b -> {
                    if (selectedIndex < 0 || selectedIndex >= triggers.size()) return;
                    var selected = triggers.get(selectedIndex);
                    ClientPlayNetworking.send(new StoryPayloads.TriggerRemovePayload(selected.id()));
                }).bounds(cx + 80, height - 35, 60, 20).build();
        deleteButton.visible = false;
        addRenderableWidget(deleteButton);
    }

    private void updateDeleteButton() {
        if (deleteButton == null) return;
        boolean canDelete = isOperator && loaded && selectedIndex >= 0 && selectedIndex < triggers.size();
        deleteButton.visible = canDelete;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float d) {
        renderBackground(g, mx, my, d);
        super.render(g, mx, my, d);

        int cx = width / 2;
        g.drawString(font, "§l§c✦ 触发器管理", cx - 38, 15, ShadeUI.GOLD, false);

        if (!loaded) {
            g.drawString(font, "§7加载中...", cx - 20, height / 2, ShadeUI.TEXT_MUTED, false);
            return;
        }

        if (triggers.isEmpty()) {
            g.drawString(font, "§7没有已配置的触发器", cx - 55, height / 2, ShadeUI.TEXT_MUTED, false);
            return;
        }

        int leftX = 20;
        int rightX = cx + 60;
        int listWidth = rightX - leftX - 10;
        int detailX = rightX + 10;
        int detailWidth = width - detailX - 20;
        int topY = 65;
        int itemH = 22;

        // 统计
        g.drawString(font, "§7共 " + triggers.size() + " 个触发器"
                + (isOperator ? "" : " §c(仅 OP 可删除)"), cx + 30, 19, ShadeUI.TEXT_MUTED, false);

        // 左栏：滚动列表
        for (int i = scrollOffset; i < triggers.size(); i++) {
            int y = topY + (i - scrollOffset) * itemH;
            if (y + itemH > height - 45) break;

            var t = triggers.get(i);
            boolean selected = (i == selectedIndex);

            if (selected) g.fill(leftX, y, rightX, y + itemH, 0x44252545);

            // 类型色条
            int barColor = switch (t.type()) {
                case "ZONE_ENTER" -> 0xFF50E3A0;
                case "ITEM_PICKUP" -> 0xFFA89BFF;
                case "NPC_INTERACT" -> 0xFFFFD700;
                default -> ShadeUI.BG_LOCKED;
            };
            g.fill(leftX, y, leftX + 3, y + itemH, barColor);

            // 类型标签 + 名称
            String typeTag = switch (t.type()) {
                case "ZONE_ENTER" -> "§a[区域]";
                case "ITEM_PICKUP" -> "§d[物品]";
                case "NPC_INTERACT" -> "§e[NPC]";
                default -> "§7[未知]";
            };
            String title = t.id();
            if (font.width(title) > listWidth - 50) {
                title = font.plainSubstrByWidth(title, listWidth - 53) + "...";
            }
            g.drawString(font, typeTag, leftX + 8, y + 3, ShadeUI.TEXT_MAIN, false);
            g.drawString(font, "§f" + title, leftX + 8, y + 12, ShadeUI.TEXT_MAIN, false);
        }

        // 右栏：详情
        if (selectedIndex >= 0 && selectedIndex < triggers.size()) {
            var t = triggers.get(selectedIndex);
            g.fill(detailX, topY, detailX + detailWidth, height - 45, 0x33181A2E);

            g.drawString(font, "§l§6" + t.id(), detailX + 10, topY + 10, ShadeUI.GOLD, false);

            String typeName = switch (t.type()) {
                case "ZONE_ENTER" -> "§a区域触发";
                case "ITEM_PICKUP" -> "§d物品拾取";
                case "NPC_INTERACT" -> "§eNPC交互";
                default -> "§7未知";
            };
            g.drawString(font, typeName + (t.oneTime() ? " §7(一次性)" : " §7(可重复)"),
                    detailX + 10, topY + 24, ShadeUI.TEXT_MUTED, false);

            g.fill(detailX + 10, topY + 34, detailX + detailWidth - 10, topY + 35, ShadeUI.DIVIDER);

            int dy = topY + 42;

            g.drawString(font, "§7绑定脚本: §f" + t.scriptId(),
                    detailX + 12, dy, ShadeUI.TEXT_MAIN, false);
            dy += 12;

            switch (t.type()) {
                case "ZONE_ENTER" -> {
                    g.drawString(font, "§7区域: (§f" + t.x1() + "§7, §f" + t.z1()
                                    + "§7) → (§f" + t.x2() + "§7, §f" + t.z2() + "§7)",
                            detailX + 12, dy, ShadeUI.TEXT_MAIN, false);
                    dy += 12;
                    int sizeX = Math.abs(t.x2() - t.x1());
                    int sizeZ = Math.abs(t.z2() - t.z1());
                    g.drawString(font, "§7范围: §f" + sizeX + " §7× §f" + sizeZ + " §7格",
                            detailX + 12, dy, ShadeUI.TEXT_MUTED, false);
                }
                case "ITEM_PICKUP" -> g.drawString(font, "§7物品: §f" + t.targetId(),
                        detailX + 12, dy, ShadeUI.TEXT_MAIN, false);
                case "NPC_INTERACT" -> g.drawString(font, "§7实体: §f" + t.targetId(),
                        detailX + 12, dy, ShadeUI.TEXT_MAIN, false);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double deltaX, double deltaY) {
        if (deltaY < 0) scrollOffset = Math.min(scrollOffset + 1, triggers.size() - 5);
        else scrollOffset = Math.max(0, scrollOffset - 1);
        return true;
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (k == 256) { onClose(); return true; }
        if (k == 264 || k == 265) {
            int size = triggers.size();
            if (size == 0) return true;
            if (k == 264) selectedIndex = Math.min(selectedIndex + 1, size - 1);
            else selectedIndex = Math.max(selectedIndex - 1, 0);
            int visible = (height - 110) / 22;
            if (selectedIndex < scrollOffset) scrollOffset = selectedIndex;
            if (selectedIndex >= scrollOffset + visible) scrollOffset = selectedIndex - visible + 1;
            updateDeleteButton();
            return true;
        }
        return super.keyPressed(k, s, m);
    }

    @Override
    public boolean shouldCloseOnEsc() { return true; }
    @Override public boolean isPauseScreen() { return false; }
}
