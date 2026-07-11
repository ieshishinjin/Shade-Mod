package io.github.shade.client.story;

import io.github.shade.story.network.StoryPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Galgame 风格对话框屏幕
 *
 * 显示 NPC 立绘、说话人名称、打字机效果对话文本、
 * 选项按钮等。替换 Phase 1 的聊天消息显示。
 *
 * 布局：
 * ┌──────────────────────────────────────┐
 * │          (半透明背景遮盖)              │
 * │                                      │
 * │  ┌──────┐  §e§l说话人名称              │
 * │  │立绘  │                            │
 * │  │ 64x64│  对话文本（打字机效果）       │
 * │  └──────┘  ...                       │
 * │                                      │
 * │  ┌──────────────────────────┐        │
 * │  │ 选项 1                   │        │
 * │  ├──────────────────────────┤        │
 * │  │ 选项 2                   │        │
 *  └──────────────────────────────────────┘
 */
public class StoryDialogScreen extends Screen {

    // === 显示数据 ===
    private int type;              // 0=对话, 1=选项, 2=任务, 3=关闭
    private String speaker;
    private String portraitPath;
    private String fullText;
    private List<String[]> options;
    private String scriptTitle;

    // === 打字机效果 ===
    private int visibleChars = 0;
    private int typewriterDelay = 0;
    private static final int CHARS_PER_TICK = 2;        // 每 tick 显示字数
    private static final int INITIAL_DELAY = 10;         // 初始延迟 ticks
    private boolean textFullyVisible = false;

    // === 布局常量 ===
    private static final int DIALOG_BOX_HEIGHT = 110;
    private static final int DIALOG_BOX_MARGIN = 20;
    private static final int PORTRAIT_SIZE = 64;
    private static final int PORTRAIT_MARGIN = 12;
    private static final int TEXT_LEFT_MARGIN = 10;
    private static final int OPTION_BUTTON_HEIGHT = 28;
    private static final int OPTION_BUTTON_SPACING = 4;

    // === 颜色常量 ===
    private static final int BG_COLOR = 0xCC111111;
    private static final int DIALOG_BG_COLOR = 0xDD1A1A2E;
    private static final int TEXT_COLOR = 0xFFEEEEEE;
    private static final int SPEAKER_COLOR = 0xFFFFD700;
    private static final int CONTINUE_COLOR = 0x88AAAAAA;

    // === 是否正在打字中 ===
    private boolean typing = true;

    public StoryDialogScreen(int type, String speaker, String portraitPath,
                              String text, List<String[]> options, String scriptTitle) {
        super(Component.literal(""));
        this.type = type;
        this.speaker = speaker;
        this.portraitPath = portraitPath != null ? portraitPath : "";
        this.fullText = text != null ? text : "";
        this.options = options;
        this.scriptTitle = scriptTitle != null ? scriptTitle : "";
        this.visibleChars = 0;
        this.typewriterDelay = INITIAL_DELAY;
        this.typing = true;
        this.textFullyVisible = false;
    }

    @Override
    public void tick() {
        super.tick();

        if (typing && visibleChars < fullText.length()) {
            typewriterDelay--;
            if (typewriterDelay <= 0) {
                visibleChars = Math.min(visibleChars + CHARS_PER_TICK, fullText.length());
                typewriterDelay = 1;

                if (visibleChars >= fullText.length()) {
                    typing = false;
                    textFullyVisible = true;
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);

        int width = this.width;
        int height = this.height;

        // 对话框区域
        int dialogLeft = DIALOG_BOX_MARGIN;
        int dialogRight = width - DIALOG_BOX_MARGIN;
        int dialogBottom = height - DIALOG_BOX_MARGIN;
        int dialogTop = dialogBottom - DIALOG_BOX_HEIGHT;
        int dialogWidth = dialogRight - dialogLeft;

        // 绘制对话框背景
        graphics.fill(dialogLeft, dialogTop, dialogRight, dialogBottom, DIALOG_BG_COLOR);
        // 顶部装饰线
        graphics.fill(dialogLeft, dialogTop, dialogRight, dialogTop + 2, 0xFFFFD700);

        // 绘制立绘占位（左侧）
        int portraitLeft = dialogLeft + PORTRAIT_MARGIN;
        int portraitTop = dialogTop + PORTRAIT_MARGIN;
        drawPortrait(graphics, portraitLeft, portraitTop);

        // 文本区域（立绘右侧）
        int textLeft = portraitLeft + PORTRAIT_SIZE + PORTRAIT_MARGIN;
        int textTop = dialogTop + PORTRAIT_MARGIN;
        int textWidth = dialogRight - textLeft - TEXT_LEFT_MARGIN;
        int textHeight = DIALOG_BOX_HEIGHT - PORTRAIT_MARGIN * 2;

        // 绘制说话人名称
        if (speaker != null && !speaker.isEmpty() && type != 2) {
            graphics.drawString(
                    font,
                    "§e§l" + speaker,
                    textLeft, textTop,
                    SPEAKER_COLOR, false
            );
        }

        // 绘制对话文本（带打字机效果）
        String displayText = fullText.substring(0, Math.min(visibleChars, fullText.length()));

        // 手动分行（支持 \n 和自动换行）
        List<String> lines = wrapText(displayText, textWidth);
        int lineY = textTop + (speaker != null && !speaker.isEmpty() && type != 2 ? 14 : 0);

        for (String line : lines) {
            if (lineY + font.lineHeight > dialogBottom - 10) break;
            graphics.drawString(font, line, textLeft, lineY, TEXT_COLOR, false);
            lineY += font.lineHeight + 2;
        }

        // 绘制选项按钮（type=1 时）
        if (type == 1 && options != null && !options.isEmpty() && textFullyVisible) {
            drawOptionButtons(graphics, dialogLeft, dialogRight, dialogTop, mouseX, mouseY);
        }

        // 绘制"点击继续"提示（文本完全显示后）
        if (textFullyVisible && type != 1) {
            String hint = "§7[ 点击继续 ]";
            int hintWidth = font.width(hint);
            graphics.drawString(font, hint,
                    dialogRight - hintWidth - TEXT_LEFT_MARGIN,
                    dialogBottom - 14, CONTINUE_COLOR, false);
        }

        // 绘制标题（右上角）
        if (scriptTitle != null && !scriptTitle.isEmpty()) {
            graphics.drawString(font, "§7" + scriptTitle,
                    width - font.width(scriptTitle) - 10, 10, 0x88AAAAAA, false);
        }
    }

    /**
     * 绘制立绘（或占位）
     */
    private void drawPortrait(GuiGraphics graphics, int x, int y) {
        if (portraitPath != null && !portraitPath.isEmpty()) {
            // 尝试加载自定义立绘纹理
            ResourceLocation portraitLocation = ResourceLocation.parse(portraitPath);
            try {
                graphics.blit(portraitLocation, x, y, 0, 0, PORTRAIT_SIZE, PORTRAIT_SIZE, PORTRAIT_SIZE, PORTRAIT_SIZE);
                return;
            } catch (Exception ignored) {
                // 纹理不存在，回退到占位
            }
        }

        // 默认占位框（紫色边框 + 半透明底色）
        graphics.fill(x, y, x + PORTRAIT_SIZE, y + PORTRAIT_SIZE, 0x66444466);
        graphics.fill(x, y, x + PORTRAIT_SIZE, y + 1, 0xFFAA66DD);       // 上
        graphics.fill(x, y, x + 1, y + PORTRAIT_SIZE, 0xFFAA66DD);       // 左
        graphics.fill(x + PORTRAIT_SIZE - 1, y, x + PORTRAIT_SIZE, y + PORTRAIT_SIZE, 0xFFAA66DD); // 右
        graphics.fill(x, y + PORTRAIT_SIZE - 1, x + PORTRAIT_SIZE, y + PORTRAIT_SIZE, 0xFFAA66DD); // 下

        // 中间画一个问号
        graphics.drawString(font, "§5?",
                x + PORTRAIT_SIZE / 2 - 4, y + PORTRAIT_SIZE / 2 - 4, 0xFFAA66DD, false);
    }

    /**
     * 绘制选项按钮
     */
    private void drawOptionButtons(GuiGraphics graphics, int dialogLeft, int dialogRight,
                                    int dialogTop, int mouseX, int mouseY) {
        if (options == null) return;

        int btnWidth = (dialogRight - dialogLeft) - TEXT_LEFT_MARGIN * 2;
        int totalHeight = options.size() * (OPTION_BUTTON_HEIGHT + OPTION_BUTTON_SPACING);
        int startY = dialogTop - totalHeight - 10;

        // 防止按钮超出屏幕顶部
        if (startY < 10) startY = 10;

        for (int i = 0; i < options.size(); i++) {
            int btnX = dialogLeft + TEXT_LEFT_MARGIN;
            int btnY = startY + i * (OPTION_BUTTON_HEIGHT + OPTION_BUTTON_SPACING);

            boolean hovered = mouseX >= btnX && mouseX <= btnX + btnWidth
                    && mouseY >= btnY && mouseY <= btnY + OPTION_BUTTON_HEIGHT;

            int bgColor = hovered ? 0xCC444466 : 0xCC222244;
            int borderColor = hovered ? 0xFFFFD700 : 0x88555588;

            // 按钮背景
            graphics.fill(btnX, btnY, btnX + btnWidth, btnY + OPTION_BUTTON_HEIGHT, bgColor);
            // 边框
            graphics.fill(btnX, btnY, btnX + btnWidth, btnY + 1, borderColor);
            graphics.fill(btnX, btnY + OPTION_BUTTON_HEIGHT - 1, btnX + btnWidth, btnY + OPTION_BUTTON_HEIGHT, borderColor);
            graphics.fill(btnX, btnY, btnX + 1, btnY + OPTION_BUTTON_HEIGHT, borderColor);
            graphics.fill(btnX + btnWidth - 1, btnY, btnX + btnWidth, btnY + OPTION_BUTTON_HEIGHT, borderColor);

            // 选项文本
            String label = "§6[" + (i + 1) + "] §f" + options.get(i)[0];
            graphics.drawString(font, label,
                    btnX + 10, btnY + (OPTION_BUTTON_HEIGHT - font.lineHeight) / 2,
                    0xFFEEEEEE, false);
        }
    }

    /**
     * 文本自动换行
     */
    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }

        // 先按 \n 分段
        String[] paragraphs = text.split("\n", -1);
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }

            // 对长段进行自动换行
            StringBuilder currentLine = new StringBuilder();
            String[] words = paragraph.split(" ", -1);

            for (String word : words) {
                String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
                if (font.width(testLine) > maxWidth && !currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    currentLine = new StringBuilder(testLine);
                }
            }
            if (!currentLine.isEmpty()) {
                lines.add(currentLine.toString());
            }
        }

        return lines;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 正在打字 → 点击加速完成
            if (typing && visibleChars < fullText.length()) {
                visibleChars = fullText.length();
                typing = false;
                textFullyVisible = true;
                return true;
            }

            // 选项模式 → 检查是否点击了选项
            if (type == 1 && options != null && textFullyVisible) {
                int dialogLeft = DIALOG_BOX_MARGIN;
                int dialogRight = width - DIALOG_BOX_MARGIN;
                int dialogBottom = height - DIALOG_BOX_MARGIN;
                int dialogTop = dialogBottom - DIALOG_BOX_HEIGHT;

                int btnWidth = (dialogRight - dialogLeft) - TEXT_LEFT_MARGIN * 2;
                int totalHeight = options.size() * (OPTION_BUTTON_HEIGHT + OPTION_BUTTON_SPACING);
                int startY = dialogTop - totalHeight - 10;
                if (startY < 10) startY = 10;

                for (int i = 0; i < options.size(); i++) {
                    int btnX = dialogLeft + TEXT_LEFT_MARGIN;
                    int btnY = startY + i * (OPTION_BUTTON_HEIGHT + OPTION_BUTTON_SPACING);

                    if (mouseX >= btnX && mouseX <= btnX + btnWidth
                            && mouseY >= btnY && mouseY <= btnY + OPTION_BUTTON_HEIGHT) {
                        // 发送选择到服务器
                        ClientPlayNetworking.send(StoryPayloads.StoryActionPayload.choose(i));
                        return true;
                    }
                }
            }

            // 对话模式且文本已完全显示 → 推进
            if (textFullyVisible && type != 1) {
                ClientPlayNetworking.send(StoryPayloads.StoryActionPayload.advance());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 按任意键加速/继续（同鼠标点击）
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (typing && visibleChars < fullText.length()) {
            visibleChars = fullText.length();
            typing = false;
            textFullyVisible = true;
            return true;
        }
        if (textFullyVisible && type != 1) {
            ClientPlayNetworking.send(StoryPayloads.StoryActionPayload.advance());
            return true;
        }
        if (type == 1 && textFullyVisible) {
            // 数字键快速选择
            if (keyCode >= 49 && keyCode <= 57) { // 1-9
                int idx = keyCode - 49;
                if (options != null && idx < options.size()) {
                    ClientPlayNetworking.send(StoryPayloads.StoryActionPayload.choose(idx));
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // ESC 不关闭对话框
    }

    @Override
    public boolean isPauseScreen() {
        return false; // 不暂停游戏
    }

    /**
     * 更新屏幕内容（从网络包接收）
     */
    public void updateContent(int type, String speaker, String portraitPath,
                               String text, List<String[]> options, String scriptTitle) {
        this.type = type;
        this.speaker = speaker;
        this.portraitPath = portraitPath != null ? portraitPath : "";
        this.fullText = text != null ? text : "";
        this.options = options;
        this.scriptTitle = scriptTitle != null ? scriptTitle : "";
        this.visibleChars = 0;
        this.typewriterDelay = INITIAL_DELAY;
        this.typing = true;
        this.textFullyVisible = false;
    }

    public static void openOrUpdate(int type, String speaker, String portraitPath,
                                     String text, List<String[]> options, String scriptTitle) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (type == 3) {
                // 关闭对话框
                if (client.screen instanceof StoryDialogScreen) {
                    client.setScreen(null);
                }
                return;
            }

            if (client.screen instanceof StoryDialogScreen existing) {
                // 更新现有对话框
                existing.updateContent(type, speaker, portraitPath, text, options, scriptTitle);
            } else {
                // 打开新对话框
                client.setScreen(new StoryDialogScreen(
                        type, speaker, portraitPath, text, options, scriptTitle));
            }
        });
    }
}
