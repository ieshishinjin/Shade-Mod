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

    // === CG 展示包 (S2C) ===

    public record CgDisplayPayload(String texture, String title, int fadeInTicks) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<CgDisplayPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.parse("shade:cg_display"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CgDisplayPayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUtf(p.texture, 256); buf.writeUtf(p.title, 128); buf.writeVarInt(p.fadeInTicks); },
                buf -> new CgDisplayPayload(buf.readUtf(256), buf.readUtf(128), buf.readVarInt()));
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // === CG 关闭包 (C2S) ===

    public record CgClosePayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<CgClosePayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.parse("shade:cg_close"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CgClosePayload> CODEC = StreamCodec.of(
                (buf, p) -> {}, buf -> new CgClosePayload());
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // === 画廊数据包 (S2C) ===

    public record GalleryPayload(
            List<GalleryEntryData> entries,
            List<String> unlockedIds
    ) implements CustomPacketPayload {
        public record GalleryEntryData(String id, String title, String description, String type, String texturePath) {}
        public static final CustomPacketPayload.Type<GalleryPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.parse("shade:gallery"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GalleryPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeCollection(p.entries, (b, e) -> {
                        b.writeUtf(e.id(), 64); b.writeUtf(e.title(), 128);
                        b.writeUtf(e.description(), 256); b.writeUtf(e.type(), 16);
                        b.writeUtf(e.texturePath() != null ? e.texturePath() : "", 256);
                    });
                    buf.writeCollection(p.unlockedIds, (b, id) -> b.writeUtf(id, 64));
                },
                buf -> new GalleryPayload(
                        buf.readList(b -> new GalleryEntryData(b.readUtf(64), b.readUtf(128), b.readUtf(256), b.readUtf(16), b.readUtf(256))),
                        buf.readList(b -> b.readUtf(64))));
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // === 请求画廊数据 (C2S) ===

    public record GalleryRequestPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<GalleryRequestPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.parse("shade:gallery_req"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GalleryRequestPayload> CODEC = StreamCodec.of(
                (buf, p) -> {}, buf -> new GalleryRequestPayload());
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // === 故事进度包 (S2C) — 供 AiControlScreen 使用 ===

    public record StoryProgressPayload(
            boolean hasActiveStory,
            String activeScriptTitle,
            String activeScriptId,
            boolean completed,
            int totalScripts,
            int completedScripts
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StoryProgressPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.parse("shade:story_progress"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StoryProgressPayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeBoolean(p.hasActiveStory); buf.writeUtf(p.activeScriptTitle, 128); buf.writeUtf(p.activeScriptId, 64); buf.writeBoolean(p.completed); buf.writeVarInt(p.totalScripts); buf.writeVarInt(p.completedScripts); },
                buf -> new StoryProgressPayload(buf.readBoolean(), buf.readUtf(128), buf.readUtf(64), buf.readBoolean(), buf.readVarInt(), buf.readVarInt()));
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // === 请求故事进度 (C2S) ===

    public record StoryProgressRequestPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StoryProgressRequestPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.parse("shade:story_progress_req"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StoryProgressRequestPayload> CODEC = StreamCodec.of(
                (buf, p) -> {}, buf -> new StoryProgressRequestPayload());
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // === 日记数据包 (S2C) ===

    public record JournalPayload(
            List<JournalEntryData> entries,
            List<String> unlockedIds
    ) implements CustomPacketPayload {
        public record JournalEntryData(String id, String title, String description, String type) {}
        public static final CustomPacketPayload.Type<JournalPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.parse("shade:journal"));
        public static final StreamCodec<RegistryFriendlyByteBuf, JournalPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeCollection(p.entries, (b, e) -> {
                        b.writeUtf(e.id(), 64); b.writeUtf(e.title(), 128);
                        b.writeUtf(e.description(), 256); b.writeUtf(e.type(), 16);
                    });
                    buf.writeCollection(p.unlockedIds, (b, id) -> b.writeUtf(id, 64));
                },
                buf -> new JournalPayload(
                        buf.readList(b -> new JournalEntryData(b.readUtf(64), b.readUtf(128), b.readUtf(256), b.readUtf(16))),
                        buf.readList(b -> b.readUtf(64))));
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // === 请求日记数据 (C2S) ===

    public record JournalRequestPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<JournalRequestPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.parse("shade:journal_req"));
        public static final StreamCodec<RegistryFriendlyByteBuf, JournalRequestPayload> CODEC = StreamCodec.of(
                (buf, p) -> {}, buf -> new JournalRequestPayload());
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // === 图鉴数据包 (S2C) ===

    public record BestiaryPayload(
            List<BestiaryEntryData> entries,
            List<String> discoveredIds
    ) implements CustomPacketPayload {
        public record BestiaryEntryData(String id, String title, String description, String type, String category) {}
        public static final CustomPacketPayload.Type<BestiaryPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.parse("shade:bestiary"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BestiaryPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeCollection(p.entries, (b, e) -> {
                        b.writeUtf(e.id(), 64); b.writeUtf(e.title(), 128);
                        b.writeUtf(e.description(), 256); b.writeUtf(e.type(), 16);
                        b.writeUtf(e.category() != null ? e.category() : "", 32);
                    });
                    buf.writeCollection(p.discoveredIds, (b, id) -> b.writeUtf(id, 64));
                },
                buf -> new BestiaryPayload(
                        buf.readList(b -> new BestiaryEntryData(b.readUtf(64), b.readUtf(128), b.readUtf(256), b.readUtf(16), b.readUtf(32))),
                        buf.readList(b -> b.readUtf(64))));
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // === 请求图鉴数据 (C2S) ===

    public record BestiaryRequestPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<BestiaryRequestPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.parse("shade:bestiary_req"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BestiaryRequestPayload> CODEC = StreamCodec.of(
                (buf, p) -> {}, buf -> new BestiaryRequestPayload());
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // === 触发器列表数据包 (S2C) ===

    public record TriggerListPayload(
            List<TriggerEntryData> triggers,
            boolean isOperator
    ) implements CustomPacketPayload {
        public record TriggerEntryData(String id, String type, String scriptId, String targetId,
                                        int x1, int z1, int x2, int z2, boolean oneTime) {}
        public static final CustomPacketPayload.Type<TriggerListPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.parse("shade:trigger_list"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TriggerListPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeCollection(p.triggers, (b, e) -> {
                        b.writeUtf(e.id(), 64); b.writeUtf(e.type(), 32);
                        b.writeUtf(e.scriptId(), 64); b.writeUtf(e.targetId() != null ? e.targetId() : "", 64);
                        b.writeInt(e.x1()); b.writeInt(e.z1()); b.writeInt(e.x2()); b.writeInt(e.z2());
                        b.writeBoolean(e.oneTime());
                    });
                    buf.writeBoolean(p.isOperator);
                },
                buf -> new TriggerListPayload(
                        buf.readList(b -> new TriggerEntryData(
                                b.readUtf(64), b.readUtf(32), b.readUtf(64), b.readUtf(64),
                                b.readInt(), b.readInt(), b.readInt(), b.readInt(), b.readBoolean())),
                        buf.readBoolean()));
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // === 请求触发器列表 (C2S) ===

    public record TriggerListRequestPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TriggerListRequestPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.parse("shade:trigger_list_req"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TriggerListRequestPayload> CODEC = StreamCodec.of(
                (buf, p) -> {}, buf -> new TriggerListRequestPayload());
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // === 删除触发器 (C2S) ===

    public record TriggerRemovePayload(String triggerId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TriggerRemovePayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.parse("shade:trigger_remove"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TriggerRemovePayload> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.triggerId, 64),
                buf -> new TriggerRemovePayload(buf.readUtf(64)));
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // === 注册与处理 ===

    public static void register() {
        PayloadTypeRegistry.playS2C().register(StoryDialogPayload.TYPE, StoryDialogPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StoryMenuPayload.TYPE, StoryMenuPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(QuestSyncPayload.TYPE, QuestSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(QuestLogPayload.TYPE, QuestLogPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StoryActionPayload.TYPE, StoryActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CgDisplayPayload.TYPE, CgDisplayPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GalleryPayload.TYPE, GalleryPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StoryProgressPayload.TYPE, StoryProgressPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(QuestLogRequestPayload.TYPE, QuestLogRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CgClosePayload.TYPE, CgClosePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GalleryRequestPayload.TYPE, GalleryRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StoryProgressRequestPayload.TYPE, StoryProgressRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(JournalPayload.TYPE, JournalPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(JournalRequestPayload.TYPE, JournalRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BestiaryPayload.TYPE, BestiaryPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BestiaryRequestPayload.TYPE, BestiaryRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TriggerListPayload.TYPE, TriggerListPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TriggerListRequestPayload.TYPE, TriggerListRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TriggerRemovePayload.TYPE, TriggerRemovePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(StoryActionPayload.TYPE, (p, ctx) -> ctx.player().server.execute(() -> handlePlayerAction(ctx.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(QuestLogRequestPayload.TYPE, (p, ctx) -> ctx.player().server.execute(() -> sendQuestLogToClient(ctx.player())));
        ServerPlayNetworking.registerGlobalReceiver(GalleryRequestPayload.TYPE, (p, ctx) -> ctx.player().server.execute(() -> sendGalleryToClient(ctx.player())));
        ServerPlayNetworking.registerGlobalReceiver(StoryProgressRequestPayload.TYPE, (p, ctx) -> ctx.player().server.execute(() -> sendStoryProgressToClient(ctx.player())));
        ServerPlayNetworking.registerGlobalReceiver(CgClosePayload.TYPE, (p, ctx) -> ctx.player().server.execute(() -> {
            var player = ctx.player();
            var engine = StoryEngine.getInstance(player.serverLevel());
            if (engine.isInStory(player)) {
                var nextNode = engine.advance(player);
                if (nextNode != null) sendNodeToClient(player, engine, nextNode);
                else ServerPlayNetworking.send(player, StoryDialogPayload.close());
            }
        }));
        ServerPlayNetworking.registerGlobalReceiver(JournalRequestPayload.TYPE, (p, ctx) -> ctx.player().server.execute(() -> sendJournalToClient(ctx.player())));
        ServerPlayNetworking.registerGlobalReceiver(BestiaryRequestPayload.TYPE, (p, ctx) -> ctx.player().server.execute(() -> sendBestiaryToClient(ctx.player())));
        ServerPlayNetworking.registerGlobalReceiver(TriggerListRequestPayload.TYPE, (p, ctx) -> ctx.player().server.execute(() -> sendTriggerListToClient(ctx.player())));
        ServerPlayNetworking.registerGlobalReceiver(TriggerRemovePayload.TYPE, (p, ctx) -> ctx.player().server.execute(() -> {
            var player = ctx.player();
            var tm = io.github.shade.story.trigger.TriggerManager.getInstance(player.serverLevel());
            tm.removeTrigger(p.triggerId());
            sendTriggerListToClient(player);
        }));
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

    /**
     * 发送画廊数据到客户端
     */
    public static void sendGalleryToClient(ServerPlayer player) {
        var gm = io.github.shade.story.gallery.GalleryManager.getInstance(player.serverLevel());
        var data = gm.getDisplayData(player);
        List<GalleryPayload.GalleryEntryData> entries = new ArrayList<>();
        for (var entry : gm.getAllEntries()) {
            entries.add(new GalleryPayload.GalleryEntryData(
                    entry.getId(), entry.getTitle(), entry.getDescription(),
                    entry.getType(), entry.getTexturePath()));
        }
        ServerPlayNetworking.send(player, new GalleryPayload(entries, new ArrayList<>(data.unlockedIds())));
    }

    /**
     * 发送故事进度到客户端（供 AiControlScreen 使用）
     */
    public static void sendStoryProgressToClient(ServerPlayer player) {
        var engine = StoryEngine.getInstance(player.serverLevel());
        var mgr = StoryManager.getInstance(player.serverLevel());
        var progress = mgr.getProgress(player);
        int total = engine.getAllScripts().size();
        int completed = progress.getCompletedScripts().size();
        String activeId = engine.getActiveScriptId(player);
        String activeTitle = "";
        boolean hasActive = activeId != null;
        if (hasActive) { var s = engine.getScript(activeId); if (s != null) activeTitle = s.getTitle(); }
        boolean thisCompleted = activeId != null && progress.getCompletedScripts().contains(activeId);
        ServerPlayNetworking.send(player, new StoryProgressPayload(hasActive, activeTitle, activeId != null ? activeId : "", thisCompleted, total, completed));
    }

    /**
     * 发送日记数据到客户端
     */
    public static void sendJournalToClient(ServerPlayer player) {
        var jm = io.github.shade.story.journal.JournalManager.getInstance(player.serverLevel());
        var data = jm.getDisplayData(player);
        List<JournalPayload.JournalEntryData> entries = new ArrayList<>();
        for (var entry : jm.getAllEntries()) {
            entries.add(new JournalPayload.JournalEntryData(
                    entry.getId(), entry.getTitle(), entry.getDescription(), entry.getType()));
        }
        ServerPlayNetworking.send(player, new JournalPayload(entries, new ArrayList<>(data.unlockedIds())));
    }

    /**
     * 发送图鉴数据到客户端
     */
    public static void sendBestiaryToClient(ServerPlayer player) {
        var bm = io.github.shade.story.journal.BestiaryManager.getInstance(player.serverLevel());
        var data = bm.getDisplayData(player);
        List<BestiaryPayload.BestiaryEntryData> entries = new ArrayList<>();
        for (var entry : bm.getAllEntries()) {
            entries.add(new BestiaryPayload.BestiaryEntryData(
                    entry.getId(), entry.getTitle(), entry.getDescription(), entry.getType(), entry.getCategory()));
        }
        ServerPlayNetworking.send(player, new BestiaryPayload(entries, new ArrayList<>(data.discoveredIds())));
    }

    /**
     * 发送触发器列表到客户端
     */
    public static void sendTriggerListToClient(ServerPlayer player) {
        var tm = io.github.shade.story.trigger.TriggerManager.getInstance(player.serverLevel());
        var triggers = tm.getAllTriggers();
        List<TriggerListPayload.TriggerEntryData> entries = new ArrayList<>();
        for (var t : triggers) {
            entries.add(new TriggerListPayload.TriggerEntryData(
                    t.getId(), t.getType(), t.getScriptId(), t.getTargetId(),
                    t.getX1(), t.getZ1(), t.getX2(), t.getZ2(), t.isOneTime()));
        }
        boolean isOp = player.hasPermissions(player.server.getOperatorUserPermissionLevel());
        ServerPlayNetworking.send(player, new TriggerListPayload(entries, isOp));
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
