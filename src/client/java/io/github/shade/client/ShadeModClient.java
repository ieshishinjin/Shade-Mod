package io.github.shade.client;

import io.github.shade.client.story.StoryDialogScreen;
import io.github.shade.client.story.StoryQuestOverlay;
import io.github.shade.story.network.StoryPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class ShadeModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 注册客户端网络包接收器 — 处理服务器发来的对话框内容
        registerClientReceivers();

        // 注册 HUD 叠加层 — 显示 Quest 追踪
        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> {
            StoryQuestOverlay.getInstance().render(graphics);
        });

        net.minecraft.client.Minecraft.getInstance().execute(() -> {
            // 空执行确保客户端初始化完成
        });
    }

    /**
     * 注册客户端接收器
     */
    private void registerClientReceivers() {
        // 接收对话框内容包
        ClientPlayNetworking.registerGlobalReceiver(StoryPayloads.StoryDialogPayload.TYPE,
                (payload, context) -> {
                    context.client().execute(() -> {
                        StoryDialogScreen.openOrUpdate(
                                payload.dialogType(),
                                payload.speaker(),
                                payload.portrait(),
                                payload.text(),
                                payload.options(),
                                payload.scriptTitle()
                        );
                    });
                });

        // 未来可在此注册更多客户端包接收器
    }
}
