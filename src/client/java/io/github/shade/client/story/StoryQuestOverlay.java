package io.github.shade.client.story;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Quest 追踪 HUD — 在游戏画面右上角显示当前 Quest 进度
 *
 * 显示内容：
 * - Quest 名称（金色）
 * - 每个 Objective 的进度条（类别标记 + 进度数字）
 * - 已完成状态显示
 */
public class StoryQuestOverlay {

    private static StoryQuestOverlay INSTANCE;

    private String questName = "";
    private String[] objectiveTexts = new String[0];
    private int[] objectiveProgress = new int[0];
    private int[] objectiveMax = new int[0];
    private boolean active = false;

    private StoryQuestOverlay() {}

    public static StoryQuestOverlay getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new StoryQuestOverlay();
        }
        return INSTANCE;
    }

    /**
     * 更新 Quest 追踪数据（由网络包接收器调用）
     */
    public void updateQuest(String questName, String[] objectiveTexts,
                             int[] progress, int[] max) {
        this.questName = questName != null ? questName : "";
        this.objectiveTexts = objectiveTexts != null ? objectiveTexts : new String[0];
        this.objectiveProgress = progress != null ? progress : new int[0];
        this.objectiveMax = max != null ? max : new int[0];
        this.active = questName != null && !questName.isEmpty();
    }

    /**
     * 清除 Quest 追踪（Quest 完成时）
     */
    public void clearQuest() {
        this.active = false;
        this.questName = "";
        this.objectiveTexts = new String[0];
        this.objectiveProgress = new int[0];
        this.objectiveMax = new int[0];
    }

    /**
     * 在 HUD 上渲染 Quest 追踪
     */
    public void render(GuiGraphics graphics) {
        if (!active || questName.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        // 右上角偏移
        int rightX = screenWidth - 15;
        int currentY = 15;

        // Quest 名称标题
        String title = "§6✦ " + questName;
        int titleWidth = client.font.width(title);
        graphics.drawString(client.font, title,
                rightX - titleWidth, currentY, 0xFFFFD700, false);
        currentY += 14;

        // 每个 Objective
        for (int i = 0; i < objectiveTexts.length; i++) {
            if (i >= objectiveProgress.length || i >= objectiveMax.length) break;

            String objText = objectiveTexts[i];
            int progress = objectiveProgress[i];
            int max = objectiveMax[i];
            boolean completed = progress >= max;

            // 状态标记
            String status = completed ? "§a✔ " : "§7○ ";
            String progressStr = completed ? "§a" : "§e";
            progressStr += progress + "§7/§e" + max;

            String line = status + "§f" + objText + " §7[ " + progressStr + " §7]";
            int lineWidth = client.font.width(line);

            graphics.drawString(client.font, line,
                    rightX - lineWidth, currentY,
                    completed ? 0xFF55FF55 : 0xFFEEEEEE, false);
            currentY += 11;
        }

        // 底部装饰线
        if (currentY > 20) {
            graphics.fill(rightX - 1, 14, rightX, currentY, 0x33FFD700);
        }
    }

    public boolean isActive() {
        return active;
    }
}
