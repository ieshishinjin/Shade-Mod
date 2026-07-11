package io.github.shade.client;

import io.github.shade.client.story.StoryDialogScreen;
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

    private static KeyMapping resumeKey;

    @Override
    public void onInitializeClient() {
        registerClientReceivers();
        registerKeybindings();

        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> {
            StoryQuestOverlay.getInstance().render(graphics);
        });
    }

    private void registerKeybindings() {
        resumeKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.shade.resume",
                GLFW.GLFW_KEY_R,
                "category.shade"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (resumeKey.consumeClick()) {
                ClientPlayNetworking.send(StoryPayloads.StoryActionPayload.resume());
            }
        });
    }

    private void registerClientReceivers() {
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
    }
}
