package io.github.shade.client;

import io.github.shade.client.aigen.AiControlScreen;
import io.github.shade.client.story.StoryDialogScreen;
import io.github.shade.client.story.StoryMenuScreen;
import io.github.shade.client.story.StoryQuestOverlay;
import io.github.shade.story.network.StoryPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class ShadeModClient implements ClientModInitializer {

    private static KeyMapping storyMenuKey, aiControlKey;

    @Override
    public void onInitializeClient() {
        registerKeybindings();
        registerReceivers();

        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> {
            StoryQuestOverlay.getInstance().render(graphics);
        });
    }

    private void registerKeybindings() {
        storyMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.shade.story_menu", GLFW.GLFW_KEY_R, "category.shade"));

        // U 键 → 打开 AI 控制中心（独立于剧情菜单）
        aiControlKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.shade.ai_control", GLFW.GLFW_KEY_U, "category.shade"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (storyMenuKey.consumeClick()) {
                ClientPlayNetworking.send(StoryPayloads.StoryActionPayload.openMenu());
            }
            if (aiControlKey.consumeClick()) {
                client.setScreen(new AiControlScreen());
            }
        });
    }

    private void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(StoryPayloads.StoryDialogPayload.TYPE,
                (payload, context) -> {
                    context.client().execute(() -> {
                        StoryDialogScreen.openOrUpdate(
                                payload.dialogType(), payload.speaker(),
                                payload.portrait(), payload.text(),
                                payload.options(), payload.scriptTitle());
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(StoryPayloads.StoryMenuPayload.TYPE,
                (payload, context) -> {
                    context.client().execute(() -> {
                        context.client().setScreen(new StoryMenuScreen(payload));
                    });
                });
    }
}
