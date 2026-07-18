package io.github.shade.story.journal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.github.shade.ShadeMod;
import io.github.shade.story.StoryManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 日记管理器 — 记录剧情关键节点、NPC、地点和 Flag 事件
 *
 * 每个玩家的解锁记录独立保存：
 * <world>/data/shade/journal/<uuid>.json
 */
public class JournalManager {

    private static final Map<ServerLevel, JournalManager> INSTANCES = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STRING_SET_TYPE = new TypeToken<Set<String>>() {}.getType();

    private final ServerLevel world;
    private final Path saveDir;

    /** 内置日记条目定义 */
    private final Map<String, JournalEntry> journalDefs = new LinkedHashMap<>();

    /** 玩家解锁缓存：uuid → Set<entryId> */
    private final Map<UUID, Set<String>> unlockedEntries = new ConcurrentHashMap<>();

    private JournalManager(ServerLevel world) {
        this.world = world;
        this.saveDir = world.getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve("data/shade/journal");
        registerDefaultEntries();
    }

    // ==================== 单例 ====================

    public static JournalManager getInstance(ServerLevel world) {
        return INSTANCES.computeIfAbsent(world, JournalManager::new);
    }

    public static void cleanup(ServerLevel world) {
        JournalManager mgr = INSTANCES.remove(world);
        if (mgr != null) mgr.saveAll();
    }

    public static void cleanupAll() {
        for (JournalManager mgr : INSTANCES.values()) mgr.saveAll();
        INSTANCES.clear();
    }

    // ==================== 内置条目 ====================

    private void registerDefaultEntries() {
        // —— 剧情章节 ——
        addDefinition(JournalEntry.script("journal_chapter1", "第一章：苏醒",
                "在晨曦营地中醒来，遇到了向导阿卡娅，开始了在这片废土上的冒险。", "chapter1_wake_up"));
        addDefinition(JournalEntry.script("journal_chapter2", "第二章：林中迷途",
                "深入晨曦营地以西的森林，探索隐藏在密林中的秘密。", "chapter2_forest_whispers"));

        // —— 关键 NPC ——
        addDefinition(JournalEntry.npc("journal_npc_akaya", "阿卡娅",
                "晨曦营地的向导，一位披着斗篷的神秘女子。她在营地外围发现了重伤的你，并将你救回。",
                "akaya"));

        // —— 关键地点 ——
        addDefinition(JournalEntry.location("journal_loc_camp", "晨曦营地",
                "这片废土上最后的庇护所之一。营地位于一片丘陵之中，四周有木栅栏围护。"));

        // —— 关键事件 ——
        addDefinition(JournalEntry.flag("journal_flag_first_quest", "第一个任务",
                "接受了阿卡娅的请求，前往西边哨站侦察掠夺者的动向。",
                "accepted_first_quest=true"));
        addDefinition(JournalEntry.flag("journal_flag_quest_complete", "首次任务完成",
                "成功侦察了西边哨站，清理了盘踞在那里的掠夺者。阿卡娅对你的能力刮目相看。",
                "completed_recon=true"));
    }

    public void addDefinition(JournalEntry entry) {
        journalDefs.put(entry.getId(), entry);
    }

    // ==================== 解锁 ====================

    /**
     * 为玩家解锁一个日记条目
     */
    public boolean unlock(ServerPlayer player, String entryId) {
        if (!journalDefs.containsKey(entryId)) return false;

        Set<String> unlocked = getUnlockedSet(player);
        if (unlocked.contains(entryId)) return false;

        unlocked.add(entryId);
        save(player);
        ShadeMod.LOGGER.debug("[journal] 玩家 {} 解锁: {} ({})",
                player.getName().getString(),
                journalDefs.get(entryId).getTitle(), entryId);

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§6✦ 日记解锁: §e" + journalDefs.get(entryId).getTitle()));
        return true;
    }

    /**
     * 根据脚本 ID 自动解锁关联日记条目
     */
    public void unlockByScript(ServerPlayer player, String scriptId) {
        for (JournalEntry entry : journalDefs.values()) {
            if (!"SCRIPT".equals(entry.getType())) continue;
            if (!scriptId.equals(entry.getScriptId())) continue;
            unlock(player, entry.getId());
        }
    }

    /**
     * 根据脚本 ID 和 Flag 解锁关联条目（含条件结局条目）
     */
    public void unlockByScript(ServerPlayer player, String scriptId, Map<String, Object> playerFlags) {
        // 解锁 SCRIPT 类型条目
        unlockByScript(player, scriptId);

        // 解锁有关联 Flag 条件的条目
        if (playerFlags != null) {
            for (JournalEntry entry : journalDefs.values()) {
                if (!scriptId.equals(entry.getScriptId()) && !"FLAG".equals(entry.getType())) continue;
                if (entry.getCondition() != null && !entry.getCondition().isEmpty()) {
                    if (evaluateCondition(entry.getCondition(), playerFlags)) {
                        unlock(player, entry.getId());
                    }
                }
            }
        }
    }

    /**
     * 为玩家解锁一个 NPC 日记条目
     */
    public boolean unlockNpc(ServerPlayer player, String entityId) {
        for (JournalEntry entry : journalDefs.values()) {
            if (!"NPC".equals(entry.getType())) continue;
            if (entityId.equals(entry.getScriptId())) {
                return unlock(player, entry.getId());
            }
        }
        return false;
    }

    /**
     * 评估条件表达式（格式: "flag_name=value"）
     */
    private boolean evaluateCondition(String condition, Map<String, Object> flags) {
        String[] parts = condition.split("=");
        if (parts.length < 2) return true;

        String key = parts[0].trim();
        String expectedValue = parts[1].trim();

        Object actualValue = flags.get(key);
        if (actualValue == null) return false;

        return actualValue.toString().equals(expectedValue);
    }

    // ==================== 查询 ====================

    public boolean isUnlocked(ServerPlayer player, String entryId) {
        return getUnlockedSet(player).contains(entryId);
    }

    public List<JournalEntry> getAllEntries() {
        return new ArrayList<>(journalDefs.values());
    }

    public JournalEntry getEntry(String id) {
        return journalDefs.get(id);
    }

    public int getUnlockedCount(ServerPlayer player) {
        return getUnlockedSet(player).size();
    }

    public int getTotalCount() {
        return journalDefs.size();
    }

    /**
     * 获取带解锁状态的展示数据
     */
    public JournalDisplayData getDisplayData(ServerPlayer player) {
        Set<String> unlocked = getUnlockedSet(player);
        return new JournalDisplayData(new ArrayList<>(journalDefs.values()), unlocked);
    }

    // ==================== 持久化 ====================

    private Set<String> getUnlockedSet(ServerPlayer player) {
        return unlockedEntries.computeIfAbsent(player.getUUID(), uuid -> loadFromDisk(uuid));
    }

    private Set<String> loadFromDisk(UUID uuid) {
        Path file = saveDir.resolve(uuid + ".json");
        if (!Files.exists(file)) return ConcurrentHashMap.newKeySet();
        try (Reader reader = Files.newBufferedReader(file)) {
            Set<String> loaded = GSON.fromJson(reader, STRING_SET_TYPE);
            if (loaded != null) { Set<String> result = ConcurrentHashMap.newKeySet(); result.addAll(loaded); return result; }
            return ConcurrentHashMap.newKeySet();
        } catch (IOException e) {
            ShadeMod.LOGGER.error("[journal] 加载失败: {}", uuid, e);
            return ConcurrentHashMap.newKeySet();
        }
    }

    public void save(ServerPlayer player) {
        Set<String> unlocked = unlockedEntries.get(player.getUUID());
        if (unlocked == null) return;
        try {
            Files.createDirectories(saveDir);
            try (Writer writer = Files.newBufferedWriter(saveDir.resolve(player.getUUID() + ".json"))) {
                GSON.toJson(unlocked, writer);
            }
        } catch (IOException e) {
            ShadeMod.LOGGER.error("[journal] 保存失败: {}", player.getUUID(), e);
        }
    }

    private void saveAll() {
        // 持久化已在每次 unlock 时同步写入，saveAll 用于优雅关闭
    }

    // ==================== 内部类 ====================

    public record JournalDisplayData(
            List<JournalEntry> entries,
            Set<String> unlockedIds
    ) {}
}
