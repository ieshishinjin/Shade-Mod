package io.github.shade.client.story;

import io.github.shade.client.ShadeUI;
import io.github.shade.story.network.StoryPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 任务日志界面 — 按 L 键打开。
 *
 * 统一配色方案见 ShadeUI。
 */
public class QuestLogScreen extends Screen {

    // 旧常量改为 ShadeUI 引用，保留别名避免全量重写
    private static final int BG_COLOR = ShadeUI.BG_PANEL;
    private static final int ACCENT_COLOR = ShadeUI.ACCENT;
    private static final int TEXT_COLOR = ShadeUI.TEXT_MAIN;
    private static final int DIM_TEXT = ShadeUI.TEXT_MUTED;
    private static final int GREEN = ShadeUI.GREEN;

    private List<StoryPayloads.QuestLogPayload.QuestLogEntry> activeQuests;
    private List<String> completedQuestIds;
    private int selectedIndex = 0;
    private int scrollOffset = 0;

    public QuestLogScreen() {
        super(Component.literal("任务日志"));
    }

    @Override
    protected void init() {
        super.init();
        // 请求服务器发送任务日志
        if (Minecraft.getInstance().getConnection() != null) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new StoryPayloads.QuestLogRequestPayload());
        }
    }

    /**
     * 由网络包接收器调用，更新数据
     */
    public void updateData(List<StoryPayloads.QuestLogPayload.QuestLogEntry> active,
                           List<String> completed) {
        this.activeQuests = active;
        this.completedQuestIds = completed;
        this.selectedIndex = 0;
        this.scrollOffset = 0;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        int w = this.width, h = this.height;

        // 主面板背景
        int panelW = Math.min(440, w - 40);
        int panelH = Math.min(320, h - 40);
        int px = (w - panelW) / 2, py = (h - panelH) / 2;

        graphics.fill(px, py, px + panelW, py + panelH, BG_COLOR);
        // 顶部装饰线
        graphics.fill(px, py, px + panelW, py + 2, ACCENT_COLOR);

        // 标题
        String title = "✦ 任务日志";
        int titleW = this.font.width(title);
        graphics.drawString(this.font, title, px + (panelW - titleW) / 2, py + 10, ACCENT_COLOR, false);

        if (activeQuests == null) {
            // 加载中
            String loading = "§7加载中...";
            graphics.drawString(this.font, loading, px + 20, py + 40, DIM_TEXT, false);
            super.render(graphics, mouseX, mouseY, delta);
            return;
        }

        // 左侧：任务列表
        int listX = px + 12, listY = py + 32;
        int listW = panelW / 2 - 16;

        graphics.fill(listX - 2, listY - 2, listX + listW + 2, py + panelH - 12, 0x22252645);

        // 标题
        graphics.drawString(this.font, "§7— 进行中 —", listX, listY, DIM_TEXT, false);
        int currentY = listY + 14;

        if (activeQuests == null || activeQuests.isEmpty()) {
            graphics.drawString(this.font, "§7暂无活跃任务", listX + 4, currentY + 4, DIM_TEXT, false);
        } else {
            for (int i = 0; i < activeQuests.size(); i++) {
                var q = activeQuests.get(i);
                boolean selected = (i == selectedIndex);
                int itemColor = selected ? ACCENT_COLOR : TEXT_COLOR;

                // 选中高亮背景
                if (selected) {
                    graphics.fill(listX - 2, currentY - 2, listX + listW + 2, currentY + 14, 0x3336386A);
                }

                String prefix = (i == 0) ? "§6▶ " : "§7○ ";
                String label = prefix + q.questName();
                int labelW = this.font.width(label);

                // 截断过长文本
                if (labelW > listW - 8) {
                    while (this.font.width(label + "…") > listW - 8 && label.length() > 2) {
                        label = label.substring(0, label.length() - 1);
                    }
                    label += "…";
                }

                graphics.drawString(this.font, label, listX + 4, currentY, itemColor, false);
                currentY += 14;
            }
        }

        // 右侧：详情面板
        int detailX = px + panelW / 2 + 8;
        int detailW = panelW / 2 - 20;

        if (activeQuests != null && !activeQuests.isEmpty() && selectedIndex < activeQuests.size()) {
            var selectedQuest = activeQuests.get(selectedIndex);

            // Quest 名
            graphics.drawString(this.font, "§6✦ " + selectedQuest.questName(), detailX, listY, TEXT_COLOR, false);

            // 描述
            graphics.drawString(this.font, "§7" + selectedQuest.questDescription(), detailX, listY + 14, DIM_TEXT, false);

            // Objective 列表
            int objY = listY + 36;
            graphics.fill(detailX - 2, objY - 2, detailX + detailW + 2, objY, 0x333A3C6A);

            for (int i = 0; i < selectedQuest.objectiveTexts().length; i++) {
                boolean completed = selectedQuest.progress()[i] >= selectedQuest.maxProgress()[i];
                String status = completed ? "§a✔ " : "§7○ ";
                String progressStr = completed
                        ? "§a" + selectedQuest.progress()[i] + "§7/§a" + selectedQuest.maxProgress()[i]
                        : "§e" + selectedQuest.progress()[i] + "§7/§e" + selectedQuest.maxProgress()[i];

                String line = status + "§f" + selectedQuest.objectiveTexts()[i] + " §7[ " + progressStr + " §7]";
                graphics.drawString(this.font, line, detailX, objY + 4,
                        completed ? GREEN : TEXT_COLOR, false);
                objY += 12;
            }
        } else {
            graphics.drawString(this.font, "§7选择一个任务查看详情", detailX, listY, DIM_TEXT, false);
        }

        // 底部：已完成任务计数
        int completedCount = completedQuestIds != null ? completedQuestIds.size() : 0;
        String footer = "§7已完成任务: §e" + completedCount;
        graphics.drawString(this.font, footer, px + 12, py + panelH - 18, DIM_TEXT, false);

        // 提示
        String hint = "§7↑↓ 选择  §eESC §7关闭";
        int hintW = this.font.width(hint);
        graphics.drawString(this.font, hint, px + panelW - hintW - 12, py + panelH - 18, DIM_TEXT, false);

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_UP) {
            if (activeQuests != null && selectedIndex > 0) {
                selectedIndex--;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if (activeQuests != null && selectedIndex < activeQuests.size() - 1) {
                selectedIndex++;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
