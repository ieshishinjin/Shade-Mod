package io.github.shade.client;

import io.github.shade.client.aigen.AiControlScreen;
import io.github.shade.client.story.CgScreen;
import io.github.shade.client.story.GalleryBrowserScreen;
import io.github.shade.client.story.QuestLogScreen;
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

    private static KeyMapping storyMenuKey, aiControlKey, questLogKey;
    private static final QuestLogScreen questLogScreen = new QuestLogScreen();

    /** 缓存的故事进度（供 AiControlScreen 读取） */
    public static StoryPayloads.StoryProgressPayload cachedProgress = null;

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

        // L 键 → 打开任务日志
        questLogKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.shade.quest_log", GLFW.GLFW_KEY_L, "category.shade"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (storyMenuKey.consumeClick()) {
                ClientPlayNetworking.send(StoryPayloads.StoryActionPayload.openMenu());
            }
            if (aiControlKey.consumeClick()) {
                client.setScreen(new AiControlScreen());
                ClientPlayNetworking.send(new StoryPayloads.StoryProgressRequestPayload());
            }
            if (questLogKey.consumeClick()) {
                client.setScreen(questLogScreen);
                ClientPlayNetworking.send(new StoryPayloads.QuestLogRequestPayload());
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

        ClientPlayNetworking.registerGlobalReceiver(StoryPayloads.QuestSyncPayload.TYPE,
                (payload, context) -> {
                    context.client().execute(() -> {
                        if (payload.hasQuest()) {
                            StoryQuestOverlay.getInstance().updateQuest(
                                    payload.questName(),
                                    payload.objectiveTexts(),
                                    payload.progress(),
                                    payload.maxProgress());
                        } else {
                            StoryQuestOverlay.getInstance().clearQuest();
                        }
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(StoryPayloads.QuestLogPayload.TYPE,
                (payload, context) -> {
                    context.client().execute(() -> {
                        questLogScreen.updateData(payload.activeQuests(), payload.completedQuestIds());
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(StoryPayloads.CgDisplayPayload.TYPE,
                (payload, context) -> {
                    context.client().execute(() -> {
                        context.client().setScreen(new CgScreen(
                                payload.texture(), payload.title(), payload.fadeInTicks()));
                    });
                });

        // 画廊数据 → 打开画廊浏览器
        ClientPlayNetworking.registerGlobalReceiver(StoryPayloads.GalleryPayload.TYPE,
                (payload, context) -> {
                    context.client().execute(() -> {
                        context.client().setScreen(new GalleryBrowserScreen(payload.entries(), payload.unlockedIds()));
                    });
                });

        // 故事进度 → 缓存供 AiControlScreen 读取
        ClientPlayNetworking.registerGlobalReceiver(StoryPayloads.StoryProgressPayload.TYPE,
                (payload, context) -> {
                    context.client().execute(() -> {
                        cachedProgress = payload;
                    });
                });
    }
}
