package io.github.shade.client.story;

import io.github.shade.story.network.StoryPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * CG 全屏展示界面 — 带淡入淡出效果
 *
 * 打开：黑色遮罩 100% → 0%（fadeInTicks 内完成）
 * 关闭：点击后 黑色遮罩 0% → 100%（8 tick 淡出）→ 自动关闭
 */
public class CgScreen extends Screen {

    private static final int FADE_OUT_TICKS = 8;

    private final ResourceLocation texture;
    private final String title;
    private final int fadeInTicks;

    private int tickCount = 0;
    private boolean fadingOut = false;
    private int fadeOutStart = 0;

    public CgScreen(String texturePath, String title, int fadeInTicks) {
        super(Component.literal("CG"));
        this.texture = ResourceLocation.tryParse(texturePath);
        this.title = title != null ? title : "";
        this.fadeInTicks = Math.max(1, fadeInTicks);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);

        // 绘制 CG 纹理
        if (texture != null) {
            graphics.pose().pushPose();
            graphics.pose().scale(1, 1, 1);

            // 根据当前画面比例铺满全屏
            int screenW = this.width;
            int screenH = this.height;
            graphics.blit(texture, 0, 0, 0, 0, screenW, screenH, screenW, screenH);
            graphics.pose().popPose();
        }

        // 淡入/淡出遮罩
        float alpha = getCurrentAlpha();
        if (alpha > 0) {
            int maskAlpha = (int) (alpha * 255);
            graphics.fill(0, 0, this.width, this.height, (maskAlpha << 24) | 0x000000);
        }

        // 标题文字
        if (!title.isEmpty() && alpha < 0.1f) {
            int titleW = this.font.width(title);
            graphics.drawString(this.font, title,
                    (this.width - titleW) / 2, this.height / 3 + 40,
                    0xFFFFFFFF, true);
        }

        // 点击提示
        if (tickCount > fadeInTicks && !fadingOut) {
            String hint = "§7[ 点击继续 ]";
            int hintW = this.font.width(hint);
            graphics.drawString(this.font, hint,
                    (this.width - hintW) / 2, this.height - 30,
                    0x88FFFFFF, false);
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        tickCount++;
        if (fadingOut) {
            if (tickCount - fadeOutStart >= FADE_OUT_TICKS) {
                onClose();
            }
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (!fadingOut && tickCount > fadeInTicks) {
            fadingOut = true;
            fadeOutStart = tickCount;
            // 通知服务器 CG 已关闭
            ClientPlayNetworking.send(new StoryPayloads.CgClosePayload());
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (key == 256) { // ESC
            if (!fadingOut) {
                fadingOut = true;
                fadeOutStart = tickCount;
                ClientPlayNetworking.send(new StoryPayloads.CgClosePayload());
            }
            return true;
        }
        return super.keyPressed(key, scan, mod);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 计算当前遮罩透明度（0.0=全透明，1.0=全黑）
     */
    private float getCurrentAlpha() {
        if (fadingOut) {
            int elapsed = tickCount - fadeOutStart;
            return Math.min(1.0f, (float) elapsed / FADE_OUT_TICKS);
        }
        if (tickCount < fadeInTicks) {
            return 1.0f - (float) tickCount / fadeInTicks;
        }
        return 0.0f;
    }
}
