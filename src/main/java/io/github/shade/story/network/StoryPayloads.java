package io.github.shade.story.network;

import io.github.shade.ShadeMod;
import io.github.shade.story.StoryEngine;
import io.github.shade.story.StoryManager;
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

public class StoryPayloads {

    // === 菜单数据包 (S2C) ===

    public record StoryMenuPayload(
            boolean hasActiveStory,
            String activeScriptId,
            String activeScriptTitle,
            String activeSpeaker,
            String activeTextPreview,
            List<ScriptInfo> scripts
    ) implements CustomPacketPayload {
        public record ScriptInfo(String id, String title, String description, boolean completed) {}
        public static final CustomPacketPayload.Type<StoryMenuPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.parse("shade:story_menu"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StoryMenuPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBoolean(p.hasActiveStory); buf.writeUtf(p.activeScriptId, 128);
                    buf.writeUtf(p.activeScriptTitle, 128); buf.writeUtf(p.activeSpeaker, 64);
                    buf.writeUtf(p.activeTextPreview, 256);
                    buf.writeCollection(p.scripts, (b, s) -> { b.writeUtf(s.id(), 64); b.writeUtf(s.title(), 128); b.writeUtf(s.description(), 256); b.writeBoolean(s.completed()); });
                },
                buf -> new StoryMenuPayload(buf.readBoolean(), buf.readUtf(128), buf.readUtf(128),
                        buf.readUtf(64), buf.readUtf(256),
                        buf.readList(b -> new StoryMenuPayload.ScriptInfo(b.readUtf(64), b.readUtf(128), b.readUtf(256), b.readBoolean()))));
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // === 对话框包 (S2C) ===

    public record StoryDialogPayload(int dialogType, String speaker, String portrait, String text, List<String[]> options, String scriptTitle) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StoryDialogPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.parse("shade:story_dialog"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StoryDialogPayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeVarInt(p.dialogType); buf.writeUtf(p.speaker, 256); buf.writeUtf(p.portrait, 256); buf.writeUtf(p.text, 65536);
                    if (p.options == null || p.options.isEmpty()) buf.writeByte(0); else { buf.writeByte(Math.min(p.options.size(), 255)); for (String[] o : p.options) { buf.writeUtf(o[0] != null ? o[0] : "", 512); buf.writeUtf(o[1] != null ? o[1] : "", 128); } }
                    buf.writeUtf(p.scriptTitle, 128); },
                buf -> { int dt = buf.readVarInt(); String sp = buf.readUtf(256); String po = buf.readUtf(256); String te = buf.readUtf(65536);
                    int oc = buf.readByte() & 0xFF; List<String[]> opts = null; if (oc > 0) { opts = new ArrayList<>(); for (int i = 0; i < oc; i++) opts.add(new String[]{buf.readUtf(512), buf.readUtf(128)}); }
                    return new StoryDialogPayload(dt, sp, po, te, opts, buf.readUtf(128)); });
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
        public static StoryDialogPayload openDialog(String s, String p, String t, String ti) { return new StoryDialogPayload(0, s, p != null ? p : "", t, null, ti); }
        public static StoryDialogPayload openChoice(String s, String t, List<StoryChoice> cs, String ti) { List<String[]> opts = null; if (cs != null && !cs.isEmpty()) { opts = new ArrayList<>(); for (StoryChoice c : cs) opts.add(new String[]{c.getLabel(), c.getNext()}); } return new StoryDialogPayload(1, s, "", t, opts, ti); }
        public static StoryDialogPayload close() { return new StoryDialogPayload(3, "", "", "", null, ""); }
        public static StoryDialogPayload ending(String t, String ti) { return new StoryDialogPayload(4, "", "", t, null, ti); }
    }

    // === 操作包 (C2S) ===

    public record StoryActionPayload(int action, int index) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StoryActionPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.parse("shade:story_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StoryActionPayload> CODEC = StreamCodec.of((buf, p) -> { buf.writeVarInt(p.action); buf.writeVarInt(p.index); }, buf -> new StoryActionPayload(buf.readVarInt(), buf.readVarInt()));
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
        public static StoryActionPayload advance() { return new StoryActionPayload(0, -1); }
        public static StoryActionPayload choose(int i) { return new StoryActionPayload(1, i); }
        public static StoryActionPayload openMenu() { return new StoryActionPayload(3, -1); }
    }

    // === 注册与处理 ===

    public static void register() {
        PayloadTypeRegistry.playS2C().register(StoryDialogPayload.TYPE, StoryDialogPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StoryMenuPayload.TYPE, StoryMenuPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StoryActionPayload.TYPE, StoryActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(StoryActionPayload.TYPE, (p, ctx) -> ctx.player().server.execute(() -> handlePlayerAction(ctx.player(), p)));
        ShadeMod.LOGGER.info("[story] 网络包已注册");
    }

    private static void handlePlayerAction(ServerPlayer player, StoryActionPayload payload) {
        StoryEngine engine = StoryEngine.getInstance(player.serverLevel());
        if (payload.action == 3) { sendMenuToClient(player, engine); return; }
        if (!engine.isInStory(player)) return;
        StoryNode nextNode = payload.action == 0 ? engine.advance(player) : engine.choose(player, payload.index);
        if (nextNode == null) ServerPlayNetworking.send(player, StoryDialogPayload.close());
        else sendNodeToClient(player, engine, nextNode);
    }

    private static void sendMenuToClient(ServerPlayer player, StoryEngine engine) {
        StoryManager mgr = StoryManager.getInstance(player.serverLevel());
        List<StoryMenuPayload.ScriptInfo> scripts = new ArrayList<>();
        for (var script : engine.getAllScripts()) {
            scripts.add(new StoryMenuPayload.ScriptInfo(script.getId(), script.getTitle(),
                    script.getDescription() != null ? script.getDescription() : "", mgr.isScriptCompleted(player, script.getId())));
        }
        String activeId = engine.getActiveScriptId(player);
        String activeTitle = "", activeSpeaker = "", activeText = "";
        if (activeId != null) {
            var s = engine.getScript(activeId);
            if (s != null) activeTitle = s.getTitle();
            var n = engine.getCurrentNode(player);
            if (n != null) { activeSpeaker = n.getSpeaker() != null ? n.getSpeaker() : ""; activeText = n.getText() != null ? n.getText().substring(0, Math.min(n.getText().length(), 80)) : ""; }
        }
        ServerPlayNetworking.send(player, new StoryMenuPayload(activeId != null, activeId != null ? activeId : "", activeTitle, activeSpeaker, activeText, scripts));
    }

    public static void sendNodeToClient(ServerPlayer player, StoryEngine engine, StoryNode node) {
        if (node == null) { ServerPlayNetworking.send(player, StoryDialogPayload.close()); return; }
        String title = ""; String sid = engine.getActiveScriptId(player); var s = sid != null ? engine.getScript(sid) : null; if (s != null) title = s.getTitle();
        switch (node.getType()) {
            case DIALOG -> ServerPlayNetworking.send(player, StoryDialogPayload.openDialog(node.getSpeaker() != null ? node.getSpeaker() : "???", node.getPortrait(), node.getText(), title));
            case CHOICE -> ServerPlayNetworking.send(player, StoryDialogPayload.openChoice(node.getSpeaker() != null ? node.getSpeaker() : "???", node.getText(), node.getOptions(), title));
            case QUEST_START -> { if (node.getSpeaker() != null && !node.getText().isEmpty()) ServerPlayNetworking.send(player, StoryDialogPayload.openDialog(node.getSpeaker(), node.getPortrait(), node.getText(), title)); }
            case QUEST_COMPLETE, QUEST_UPDATE -> { if (node.getText() != null && !node.getText().isEmpty()) ServerPlayNetworking.send(player, StoryDialogPayload.openDialog("", "", node.getText(), title)); }
            case END -> { if (node.getText() != null && !node.getText().isEmpty()) ServerPlayNetworking.send(player, StoryDialogPayload.ending(node.getText(), title)); }
            default -> {}
        }
    }
}
