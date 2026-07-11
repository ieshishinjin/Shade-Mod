package io.github.shade.story.network;

import io.github.shade.ShadeMod;
import io.github.shade.story.StoryEngine;
import io.github.shade.story.model.StoryChoice;
import io.github.shade.story.model.StoryNode;
import io.github.shade.story.model.StoryScript;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 故事系统网络包 — 服务器 ↔ 客户端通信
 */
public class StoryPayloads {

    // ============================================================
    //  Server → Client：对话框内容包
    // ============================================================

    /**
     * @param dialogType   0=对话, 1=选项, 3=关闭
     * @param speaker      说话人
     * @param portrait     立绘路径
     * @param text         文本
     * @param options      选项列表 (dialogType=1)
     * @param scriptTitle  剧情标题
     */
    public record StoryDialogPayload(
            int dialogType,
            String speaker,
            String portrait,
            String text,
            List<String[]> options,
            String scriptTitle
    ) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<StoryDialogPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.parse("shade:story_dialog"));

        public static final StreamCodec<RegistryFriendlyByteBuf, StoryDialogPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeVarInt(p.dialogType);
                            buf.writeUtf(p.speaker, 256);
                            buf.writeUtf(p.portrait, 256);
                            buf.writeUtf(p.text, 65536);
                            // options
                            if (p.options == null || p.options.isEmpty()) {
                                buf.writeByte(0);
                            } else {
                                buf.writeByte(Math.min(p.options.size(), 255));
                                for (String[] opt : p.options) {
                                    buf.writeUtf(opt[0] != null ? opt[0] : "", 512);
                                    buf.writeUtf(opt[1] != null ? opt[1] : "", 128);
                                }
                            }
                            buf.writeUtf(p.scriptTitle, 128);
                        },
                        buf -> {
                            int dt = buf.readVarInt();
                            String sp = buf.readUtf(256);
                            String po = buf.readUtf(256);
                            String te = buf.readUtf(65536);
                            int optCount = buf.readByte() & 0xFF;
                            List<String[]> opts = null;
                            if (optCount > 0) {
                                opts = new ArrayList<>();
                                for (int i = 0; i < optCount; i++) {
                                    opts.add(new String[]{buf.readUtf(512), buf.readUtf(128)});
                                }
                            }
                            String st = buf.readUtf(128);
                            return new StoryDialogPayload(dt, sp, po, te, opts, st);
                        }
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        // ---- 工厂方法 ----

        public static StoryDialogPayload openDialog(String speaker, String portrait, String text, String title) {
            return new StoryDialogPayload(0, speaker, portrait != null ? portrait : "", text, null, title);
        }

        public static StoryDialogPayload openChoice(String speaker, String text, List<StoryChoice> choices, String title) {
            List<String[]> opts = null;
            if (choices != null && !choices.isEmpty()) {
                opts = new ArrayList<>();
                for (StoryChoice c : choices) {
                    opts.add(new String[]{c.getLabel(), c.getNext()});
                }
            }
            return new StoryDialogPayload(1, speaker, "", text, opts, title);
        }

        public static StoryDialogPayload close() {
            return new StoryDialogPayload(3, "", "", "", null, "");
        }
    }

    // ============================================================
    //  Client → Server：玩家操作包
    // ============================================================

    public record StoryActionPayload(
            int action,
            int index
    ) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<StoryActionPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.parse("shade:story_action"));

        public static final StreamCodec<RegistryFriendlyByteBuf, StoryActionPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeVarInt(p.action);
                            buf.writeVarInt(p.index);
                        },
                        buf -> new StoryActionPayload(buf.readVarInt(), buf.readVarInt())
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static StoryActionPayload advance() {
            return new StoryActionPayload(0, -1);
        }

        public static StoryActionPayload choose(int index) {
            return new StoryActionPayload(1, index);
        }

        /** 重新打开当前节点的 GUI（快捷键用） */
        public static StoryActionPayload resume() {
            return new StoryActionPayload(2, -1);
        }
    }

    // ============================================================
    //  注册 + 服务器端接收处理
    // ============================================================

    public static void register() {
        PayloadTypeRegistry.playS2C().register(StoryDialogPayload.TYPE, StoryDialogPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StoryActionPayload.TYPE, StoryActionPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(StoryActionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.server.execute(() -> handlePlayerAction(player, payload));
        });

        ShadeMod.LOGGER.info("[story] 网络包已注册");
    }

    private static void handlePlayerAction(ServerPlayer player, StoryActionPayload payload) {
        StoryEngine engine = StoryEngine.getInstance(player.serverLevel());

        // Action 2 = 重新打开 GUI（快捷键 resume）
        if (payload.action == 2) {
            if (engine.isInStory(player)) {
                StoryNode node = engine.getCurrentNode(player);
                sendNodeToClient(player, engine, node);
            }
            return;
        }

        if (!engine.isInStory(player)) return;

        StoryNode nextNode;
        if (payload.action == 0) {
            nextNode = engine.advance(player);
        } else {
            nextNode = engine.choose(player, payload.index);
        }

        if (nextNode == null) {
            ServerPlayNetworking.send(player, StoryDialogPayload.close());
        } else {
            sendNodeToClient(player, engine, nextNode);
        }
    }

    public static void sendNodeToClient(ServerPlayer player, StoryEngine engine, StoryNode node) {
        if (node == null) {
            ServerPlayNetworking.send(player, StoryDialogPayload.close());
            return;
        }

        StoryScript script = null;
        String scriptId = engine.getActiveScriptId(player);
        if (scriptId != null) {
            script = engine.getScript(scriptId);
        }
        String title = script != null ? script.getTitle() : "";

        switch (node.getType()) {
            case DIALOG -> ServerPlayNetworking.send(player,
                    StoryDialogPayload.openDialog(
                            node.getSpeaker() != null ? node.getSpeaker() : "???",
                            node.getPortrait(), node.getText(), title));

            case CHOICE -> ServerPlayNetworking.send(player,
                    StoryDialogPayload.openChoice(
                            node.getSpeaker() != null ? node.getSpeaker() : "???",
                            node.getText(), node.getOptions(), title));

            case QUEST_START -> {
                if (node.getSpeaker() != null && !node.getText().isEmpty()) {
                    ServerPlayNetworking.send(player,
                            StoryDialogPayload.openDialog(node.getSpeaker(), node.getPortrait(), node.getText(), title));
                }
            }
            case QUEST_COMPLETE, QUEST_UPDATE -> {
                if (node.getText() != null && !node.getText().isEmpty()) {
                    ServerPlayNetworking.send(player,
                            StoryDialogPayload.openDialog("", "", node.getText(), title));
                }
            }
            case END -> {
                if (node.getText() != null && !node.getText().isEmpty()) {
                    ServerPlayNetworking.send(player,
                            StoryDialogPayload.openDialog("", "", node.getText(), title));
                }
            }
            default -> {}
        }
    }
}
