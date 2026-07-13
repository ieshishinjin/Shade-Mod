package io.github.shade.client.aigen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 自定义主题按钮 — 深色底 + 左侧色条 + 悬浮高亮
 */
public class ThemeButton extends Button {

    private final int accentColor;
    private final int textColor;
    private final boolean primary;
    private final boolean toggled;

    private static final int C_BTN     = 0xFF252645;
    private static final int C_BTN_H   = 0xFF36386A;
    private static final int C_BTN_ON  = 0xFF2A2B5A;
    private static final int C_ACCENT2 = 0xFF8B7BE8;

    public ThemeButton(int x, int y, int w, int h, String label,
                       int accentColor, int textColor,
                       boolean primary, boolean toggled, OnPress onPress) {
        super(x, y, w, h, Component.literal(label), onPress, DEFAULT_NARRATION);
        this.accentColor = accentColor;
        this.textColor = textColor;
        this.primary = primary;
        this.toggled = toggled;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
        boolean hovered = isHoveredOrFocused();

        int bg;
        if (primary) {
            bg = hovered ? C_ACCENT2 : accentColor;
        } else if (toggled) {
            bg = hovered ? C_BTN_H : C_BTN_ON;
        } else {
            bg = hovered ? C_BTN_H : C_BTN;
        }

        g.fill(getX(), getY(), getX() + width, getY() + height, bg);

        if (!primary) {
            g.fill(getX(), getY(), getX() + 2, getY() + height, accentColor);
        }

        g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, 0x22000000);

        Font f = Minecraft.getInstance().font;
        int tx = primary
            ? getX() + (width - f.width(getMessage())) / 2
            : getX() + 5;
        int ty = getY() + (height - f.lineHeight) / 2;
        g.drawString(f, getMessage(), tx, ty, textColor, false);
    }
}
