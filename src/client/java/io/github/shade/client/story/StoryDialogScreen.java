package io.github.shade.client.story;

import io.github.shade.client.ShadeUI;
import io.github.shade.story.network.StoryPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class StoryDialogScreen extends Screen {

    private int type;
    private String speaker;
    private String portraitPath;
    private String fullText;
    private List<String[]> options;
    private String scriptTitle;

    // 打字机效果
    private int visibleChars = 0;
    private int typewriterDelay = 0;
    private static final int CHARS_PER_TICK = 2;
    private static final int INITIAL_DELAY = 10;
    private boolean textFullyVisible = false;

    // 结束语淡入淡出（仅 type=4）
    private int fadeTicks = 8;
    private static final int FADE_DURATION = 8;
    private boolean closing = false;

    // 布局常量
    private static final int DIALOG_BOX_HEIGHT = 130;
    private static final int DIALOG_BOX_MARGIN = 16;
    private static final int PORTRAIT_SIZE = 80;
    private static final int PORTRAIT_MARGIN = 14;
    private static final int TEXT_LEFT_MARGIN = 12;
    private static final int OPTION_BUTTON_HEIGHT = 28;
    private static final int OPTION_BUTTON_SPACING = 4;

    // 颜色常量（来自 ShadeUI 统一配色）
    private static final int DIALOG_BG_COLOR = ShadeUI.DLG_BG;
    private static final int TEXT_COLOR = ShadeUI.DLG_TEXT;
    private static final int NARRATION_COLOR = ShadeUI.DLG_NARR;
    private static final int SPEAKER_COLOR = ShadeUI.GOLD;
    private static final int CONTINUE_COLOR = ShadeUI.DLG_CONTINUE;

    private boolean typing = true;
    private static final Pattern QUOTE_PATTERN = Pattern.compile("「[^」]*」");

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
        this.fadeTicks = type == 4 ? 0 : FADE_DURATION; // 仅结束语有淡入
    }

    @Override
    public void tick() {
        super.tick();

        // 结束语淡入（仅 type=4）
        if (type == 4 && fadeTicks < FADE_DURATION && !closing) fadeTicks++;

        // 结束语淡出
        if (closing) {
            fadeTicks--;
            if (fadeTicks <= 0) Minecraft.getInstance().setScreen(null);
        }

        // 打字机效果
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

        int dialogLeft = DIALOG_BOX_MARGIN;
        int dialogRight = width - DIALOG_BOX_MARGIN;
        int dialogBottom = height - DIALOG_BOX_MARGIN;
        int dialogTop = dialogBottom - DIALOG_BOX_HEIGHT;

        // 对话框背景
        graphics.fill(dialogLeft, dialogTop, dialogRight, dialogBottom, DIALOG_BG_COLOR);
        graphics.fill(dialogLeft, dialogTop, dialogRight, dialogTop + 2, 0xFFFFD700);

        // 立绘（下移6px与字体顶部视觉对齐）
        int portraitLeft = dialogLeft + PORTRAIT_MARGIN;
        int portraitTop = dialogTop + PORTRAIT_MARGIN - 2;
        drawPortrait(graphics, portraitLeft, portraitTop);

        // 文本区域
        int textLeft = portraitLeft + PORTRAIT_SIZE + PORTRAIT_MARGIN;
        int textTop = dialogTop + PORTRAIT_MARGIN;
        int textWidth = dialogRight - textLeft - TEXT_LEFT_MARGIN;

        // 说话人名称
        if (speaker != null && !speaker.isEmpty() && type != 2) {
            graphics.drawString(font, "§e§l" + speaker, textLeft, textTop, SPEAKER_COLOR, false);
        }

        // 对话文本
        String displayText = fullText.substring(0, Math.min(visibleChars, fullText.length()));
        List<String> lines = wrapText(displayText, textWidth);
        int lineY = textTop + (speaker != null && !speaker.isEmpty() && type != 2 ? font.lineHeight + 4 : 0);

        for (String line : lines) {
            if (lineY + font.lineHeight > dialogBottom - 10) break;
            renderStyledText(graphics, line, textLeft, lineY);
            lineY += font.lineHeight + 2;
        }

        // 选项按钮
        if (type == 1 && options != null && !options.isEmpty() && textFullyVisible) {
            drawOptionButtons(graphics, dialogLeft, dialogRight, dialogTop, mouseX, mouseY);
        }

        // 继续提示
        if (textFullyVisible && type != 1 && type != 4) {
            String hint = "§7[ 点击继续 ]";
            graphics.drawString(font, hint,
                    dialogRight - font.width(hint) - TEXT_LEFT_MARGIN,
                    dialogBottom - 14, CONTINUE_COLOR, false);
        }

        // 结束语提示
        if (textFullyVisible && type == 4) {
            String hint = "§7[ 点击关闭 ]";
            graphics.drawString(font, hint,
                    dialogRight - font.width(hint) - TEXT_LEFT_MARGIN,
                    dialogBottom - 14, CONTINUE_COLOR, false);
        }

        // 标题
        if (scriptTitle != null && !scriptTitle.isEmpty()) {
            graphics.drawString(font, "§7" + scriptTitle,
                    width - font.width(scriptTitle) - 10, 10, 0x88AAAAAA, false);
        }

        // === 结束语淡入淡出遮罩（仅 type=4） ===
        if (type == 4 && fadeTicks < FADE_DURATION) {
            int alpha = (int)((1.0f - (float)fadeTicks / FADE_DURATION) * 200);
            graphics.fill(0, 0, width, height, (alpha << 24) | 0x000000);
        }
    }

    private void drawPortrait(GuiGraphics graphics, int x, int y) {
        if (portraitPath != null && !portraitPath.isEmpty()) {
            try {
                ResourceLocation loc = ResourceLocation.parse(portraitPath);
                graphics.blit(loc, x, y, 0, 0, PORTRAIT_SIZE, PORTRAIT_SIZE, PORTRAIT_SIZE, PORTRAIT_SIZE);
                return;
            } catch (Exception ignored) {}
        }
        graphics.fill(x, y, x + PORTRAIT_SIZE, y + PORTRAIT_SIZE, 0x66444466);
        graphics.fill(x, y, x + PORTRAIT_SIZE, y + 1, 0xFFAA66DD);
        graphics.fill(x, y, x + 1, y + PORTRAIT_SIZE, 0xFFAA66DD);
        graphics.fill(x + PORTRAIT_SIZE - 1, y, x + PORTRAIT_SIZE, y + PORTRAIT_SIZE, 0xFFAA66DD);
        graphics.fill(x, y + PORTRAIT_SIZE - 1, x + PORTRAIT_SIZE, y + PORTRAIT_SIZE, 0xFFAA66DD);
        graphics.drawString(font, "§5?", x + PORTRAIT_SIZE / 2 - 4, y + PORTRAIT_SIZE / 2 - 4, 0xFFAA66DD, false);
    }

    private void drawOptionButtons(GuiGraphics g, int dl, int dr, int dt, int mx, int my) {
        if (options == null) return;
        int bw = (dr - dl) - TEXT_LEFT_MARGIN * 2;
        int sy = dt - options.size() * (OPTION_BUTTON_HEIGHT + OPTION_BUTTON_SPACING) - 10;
        if (sy < 10) sy = 10;
        for (int i = 0; i < options.size(); i++) {
            int bx = dl + TEXT_LEFT_MARGIN;
            int by = sy + i * (OPTION_BUTTON_HEIGHT + OPTION_BUTTON_SPACING);
            boolean h = mx >= bx && mx <= bx + bw && my >= by && my <= by + OPTION_BUTTON_HEIGHT;
            g.fill(bx, by, bx + bw, by + OPTION_BUTTON_HEIGHT, h ? 0xCC444466 : 0xCC222244);
            g.fill(bx, by, bx + bw, by + 1, h ? 0xFFFFD700 : 0x88555588);
            g.fill(bx, by + OPTION_BUTTON_HEIGHT - 1, bx + bw, by + OPTION_BUTTON_HEIGHT, h ? 0xFFFFD700 : 0x88555588);
            g.fill(bx, by, bx + 1, by + OPTION_BUTTON_HEIGHT, h ? 0xFFFFD700 : 0x88555588);
            g.fill(bx + bw - 1, by, bx + bw, by + OPTION_BUTTON_HEIGHT, h ? 0xFFFFD700 : 0x88555588);
            g.drawString(font, "§6[" + (i + 1) + "] §f" + options.get(i)[0],
                    bx + 10, by + (OPTION_BUTTON_HEIGHT - font.lineHeight) / 2, 0xFFEEEEEE, false);
        }
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) { lines.add(""); return lines; }
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

    private void renderStyledText(GuiGraphics g, String line, int x, int y) {
        if (line.isEmpty()) return;
        java.util.regex.Matcher m = QUOTE_PATTERN.matcher(line);
        int last = 0, dx = x;
        while (m.find()) {
            if (m.start() > last) {
                String nar = line.substring(last, m.start());
                g.drawString(font, "§7" + nar, dx, y, NARRATION_COLOR, false);
                dx += font.width(nar);
            }
            String dia = m.group();
            g.drawString(font, "§f" + dia, dx, y, TEXT_COLOR, false);
            dx += font.width(dia);
            last = m.end();
        }
        if (last < line.length()) {
            String rem = line.substring(last);
            g.drawString(font, "§7" + rem, dx, y, NARRATION_COLOR, false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            if (typing && visibleChars < fullText.length()) {
                visibleChars = fullText.length(); typing = false; textFullyVisible = true;
                return true;
            }
            if (type == 1 && options != null && textFullyVisible) {
                int dl = DIALOG_BOX_MARGIN, dr = width - DIALOG_BOX_MARGIN;
                int db = height - DIALOG_BOX_MARGIN, dt = db - DIALOG_BOX_HEIGHT;
                int bw = (dr - dl) - TEXT_LEFT_MARGIN * 2;
                int sy = dt - options.size() * (OPTION_BUTTON_HEIGHT + OPTION_BUTTON_SPACING) - 10;
                if (sy < 10) sy = 10;
                for (int i = 0; i < options.size(); i++) {
                    int bx = dl + TEXT_LEFT_MARGIN;
                    int by = sy + i * (OPTION_BUTTON_HEIGHT + OPTION_BUTTON_SPACING);
                    if (mx >= bx && mx <= bx + bw && my >= by && my <= by + OPTION_BUTTON_HEIGHT) {
                        ClientPlayNetworking.send(StoryPayloads.StoryActionPayload.choose(i));
                        return true;
                    }
                }
            }
            if (textFullyVisible && type != 1) {
                if (type == 4) { startClose(); return true; }
                ClientPlayNetworking.send(StoryPayloads.StoryActionPayload.advance());
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int mod) {
        if (keyCode == 256) { // ESC
            if (type == 4) { startClose(); return true; }
            return super.keyPressed(keyCode, scanCode, mod);
        }
        if (typing && visibleChars < fullText.length()) {
            visibleChars = fullText.length(); typing = false; textFullyVisible = true;
            return true;
        }
        if (textFullyVisible && type != 1) {
            if (type == 4) { startClose(); return true; }
            ClientPlayNetworking.send(StoryPayloads.StoryActionPayload.advance());
            return true;
        }
        if (type == 1 && textFullyVisible && keyCode >= 49 && keyCode <= 57) {
            int idx = keyCode - 49;
            if (options != null && idx < options.size()) {
                ClientPlayNetworking.send(StoryPayloads.StoryActionPayload.choose(idx));
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, mod);
    }

    private void startClose() { closing = true; fadeTicks = FADE_DURATION; }

    @Override public void onClose() { super.onClose(); }
    @Override public boolean shouldCloseOnEsc() { return type != 4; }
    @Override public boolean isPauseScreen() { return false; }

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
        this.closing = false;
        this.fadeTicks = type == 4 ? 0 : FADE_DURATION;
    }

    public static void openOrUpdate(int type, String speaker, String portraitPath,
                                     String text, List<String[]> options, String scriptTitle) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (type == 3) {
                if (client.screen instanceof StoryDialogScreen) client.setScreen(null);
                return;
            }
            if (type == 4) {
                if (client.screen instanceof StoryDialogScreen existing) {
                    existing.updateContent(4, "", "", text, null, "");
                } else {
                    client.setScreen(new StoryDialogScreen(4, "", "", text, null, ""));
                }
                return;
            }
            if (client.screen instanceof StoryDialogScreen existing) {
                existing.updateContent(type, speaker, portraitPath, text, options, scriptTitle);
            } else {
                client.setScreen(new StoryDialogScreen(type, speaker, portraitPath, text, options, scriptTitle));
            }
        });
    }
}
