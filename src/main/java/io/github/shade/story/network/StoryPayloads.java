package io.github.shade.story.network;

import io.github.shade.ShadeMod;
import io.github.shade.story.StoryEngine;
import io.github.shade.story.StoryManager;
import io.github.shade.story.model.StoryChoice;
import io.github.shade.story.model.StoryNode;
import io.github.shade.story.model.StoryScript;
import io.github.shade.story.quest.QuestManager;
import io.github.shade.story.quest.RuntimeQuest;
import io.github.shade.story.quest.RuntimeObjective;
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

    // === Quest 同步包 (S2C) ===

    public record QuestSyncPayload(
            boolean hasQuest,
            String questName,
            String[] objectiveTexts,
            int[] progress,
            int[] maxProgress
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<QuestSyncPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.parse("shade:quest_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, QuestSyncPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBoolean(p.hasQuest);
                    buf.writeUtf(p.questName != null ? p.questName : "", 256);
                    if (p.hasQuest) {
                        buf.writeVarInt(p.objectiveTexts != null ? p.objectiveTexts.length : 0);
                        if (p.objectiveTexts != null) for (String t : p.objectiveTexts) buf.writeUtf(t, 512);
                        if (p.progress != null) for (int v : p.progress) buf.writeVarInt(v);
                        if (p.maxProgress != null) for (int v : p.maxProgress) buf.writeVarInt(v);
                    }
                },
                buf -> {
                    boolean hasQuest = buf.readBoolean();
                    String qn = buf.readUtf(256);
                    if (!hasQuest) return new QuestSyncPayload(false, qn, null, null, null);
                    int len = buf.readVarInt();
                    String[] texts = new String[len];
                    int[] prog = new int[len];
                    int[] maxP = new int[len];
                    for (int i = 0; i < len; i++) texts[i] = buf.readUtf(512);
                    for (int i = 0; i < len; i++) prog[i] = buf.readVarInt();
                    for (int i = 0; i < len; i++) maxP[i] = buf.readVarInt();
                    return new QuestSyncPayload(true, qn, texts, prog, maxP);
                });
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // === 任务日志包 (S2C) ===

    public record QuestLogPayload(List<QuestLogEntry> activeQuests, List<String> completedQuestIds) implements CustomPacketPayload {
        public record QuestLogEntry(String questName, String questDescription, String[] objectiveTexts, int[] progress, int[] maxProgress) {}
        public static final CustomPacketPayload.Type<QuestLogPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.parse("shade:quest_log"));
        public static final StreamCodec<RegistryFriendlyByteBuf, QuestLogPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeCollection(p.activeQuests, (b, e) -> {
                        b.writeUtf(e.questName, 256); b.writeUtf(e.questDescription, 512);
                        b.writeVarInt(e.objectiveTexts.length);
                        for (String t : e.objectiveTexts) b.writeUtf(t, 512);
                        for (int v : e.progress) b.writeVarInt(v);
                        for (int v : e.maxProgress) b.writeVarInt(v);
                    });
                    buf.writeCollection(p.completedQuestIds, (b, id) -> b.writeUtf(id, 128));
                },
                buf -> {
                    List<QuestLogEntry> active = buf.readList(b -> {
                        String name = b.readUtf(256); String desc = b.readUtf(512);
                        int len = b.readVarInt();
                        String[] texts = new String[len]; int[] prog = new int[len]; int[] maxP = new int[len];
                        for (int i = 0; i < len; i++) texts[i] = b.readUtf(512);
                        for (int i = 0; i < len; i++) prog[i] = b.readVarInt();
                        for (int i = 0; i < len; i++) maxP[i] = b.readVarInt();
                        return new QuestLogEntry(name, desc, texts, prog, maxP);
                    });
                    return new QuestLogPayload(active, buf.readList(b -> b.readUtf(128)));
                });
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // === 请求任务日志 (C2S) ===

    public record QuestLogRequestPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<QuestLogRequestPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.parse("shade:quest_log_req"));
        public static final StreamCodec<RegistryFriendlyByteBuf, QuestLogRequestPayload> CODEC = StreamCodec.of(
                (buf, p) -> {}, buf -> new QuestLogRequestPayload());
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // === 注册与处理 ===

    public static void register() {
        PayloadTypeRegistry.playS2C().register(StoryDialogPayload.TYPE, StoryDialogPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StoryMenuPayload.TYPE, StoryMenuPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(QuestSyncPayload.TYPE, QuestSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(QuestLogPayload.TYPE, QuestLogPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StoryActionPayload.TYPE, StoryActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(QuestLogRequestPayload.TYPE, QuestLogRequestPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(StoryActionPayload.TYPE, (p, ctx) -> ctx.player().server.execute(() -> handlePlayerAction(ctx.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(QuestLogRequestPayload.TYPE, (p, ctx) -> ctx.player().server.execute(() -> sendQuestLogToClient(ctx.player())));
        ShadeMod.LOGGER.debug("[story] 网络包已注册");
    }

    /**
     * 发送任务日志到客户端
     */
    public static void sendQuestLogToClient(ServerPlayer player) {
        QuestManager qm = QuestManager.getInstance(player.serverLevel());
        var active = qm.getActiveQuests(player);
        var completed = qm.getCompletedQuestIds(player);

        List<QuestLogPayload.QuestLogEntry> entries = new ArrayList<>();
        for (RuntimeQuest q : active) {
            var objs = q.getObjectives();
            int n = objs.size();
            String[] texts = new String[n];
            int[] prog = new int[n];
            int[] maxP = new int[n];
            for (int i = 0; i < n; i++) {
                texts[i] = objs.get(i).getDisplayText();
                prog[i] = objs.get(i).getProgress();
                maxP[i] = objs.get(i).getTargetCount();
            }
            entries.add(new QuestLogPayload.QuestLogEntry(
                    q.getQuestName(), q.getQuestDescription(), texts, prog, maxP));
        }

        ServerPlayNetworking.send(player, new QuestLogPayload(entries, new ArrayList<>(completed)));
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
