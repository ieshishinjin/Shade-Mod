package io.github.shade.story.gallery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.github.shade.ShadeMod;
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
 * 画廊管理器 — 管理 CG 和结局的解锁状态
 *
 * 每个玩家的解锁记录独立保存：
 * <world>/data/shade/story/gallery/<uuid>.json
 */
public class GalleryManager {

    private static final Map<ServerLevel, GalleryManager> INSTANCES = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STRING_SET_TYPE = new TypeToken<Set<String>>() {}.getType();

    private final ServerLevel world;
    private final Path saveDir;

    /** 内置画廊条目定义 */
    private final Map<String, GalleryEntry> galleryDefs = new LinkedHashMap<>();

    /** 玩家解锁缓存：uuid → Set<entryId> */
    private final Map<UUID, Set<String>> unlockedEntries = new ConcurrentHashMap<>();

    private GalleryManager(ServerLevel world) {
        this.world = world;
        this.saveDir = world.getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve("data/shade/story/gallery");
        registerDefaultEntries();
    }

    // ==================== 单例 ====================

    public static GalleryManager getInstance(ServerLevel world) {
        return INSTANCES.computeIfAbsent(world, GalleryManager::new);
    }

    public static void cleanup(ServerLevel world) {
        GalleryManager mgr = INSTANCES.remove(world);
        if (mgr != null) mgr.saveAll();
    }

    public static void cleanupAll() {
        for (GalleryManager mgr : INSTANCES.values()) mgr.saveAll();
        INSTANCES.clear();
    }

    // ==================== 内置条目 ====================

    private void registerDefaultEntries() {
        // 示例 CG 条目（实际应由脚本 JSON 配置）
        addDefinition(GalleryEntry.cg("cg_wake_up", "苏醒",
                "在晨曦营地中醒来，陌生的天花板和温暖的阳光。", "shade:textures/gui/cg/wake_up.png", "chapter1_wake_up"));
        addDefinition(GalleryEntry.cg("cg_meet_akaya", "初次相遇",
                "与阿卡娅的第一次对话，命运的齿轮开始转动。", "shade:textures/gui/cg/meet_akaya.png", "chapter1_wake_up"));

        // 结局条目带条件：根据玩家选择的 Flag 解锁不同结局
        var endingNormal = GalleryEntry.ending("end_chapter1_normal", "第一章：踏上旅途",
                "接受了阿卡娅的请求，踏入了陌生的世界。", "chapter1_wake_up");
        endingNormal.setCondition("accepted_first_quest=true");
        addDefinition(endingNormal);

        var endingRest = GalleryEntry.ending("end_chapter1_rest", "第一章：休整",
                "选择先休息一天，养精蓄锐。", "chapter1_wake_up");
        endingRest.setCondition("declined_first_quest=true");
        addDefinition(endingRest);
    }

    public void addDefinition(GalleryEntry entry) {
        galleryDefs.put(entry.getId(), entry);
    }

    // ==================== 解锁 ====================

    /**
     * 为玩家解锁一个画廊条目
     */
    public boolean unlock(ServerPlayer player, String entryId) {
        if (!galleryDefs.containsKey(entryId)) return false;

        Set<String> unlocked = getUnlockedSet(player);
        if (unlocked.contains(entryId)) return false; // 已解锁

        unlocked.add(entryId);
        save(player);
        ShadeMod.LOGGER.debug("[gallery] 玩家 {} 解锁: {} ({})",
                player.getName().getString(),
                galleryDefs.get(entryId).getTitle(), entryId);

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§6✦ 解锁 CG: §e" + galleryDefs.get(entryId).getTitle()));
        return true;
    }

    /**
     * 根据脚本 ID 自动解锁关联条目
     */
    public void unlockByScript(ServerPlayer player, String scriptId) {
        unlockByScript(player, scriptId, null);
    }

    /**
     * 根据脚本 ID 和 Flag 条件解锁关联条目
     * 如果 ending 有 conditions 要求，只有满足条件才解锁
     */
    public void unlockByScript(ServerPlayer player, String scriptId, Map<String, Object> playerFlags) {
        for (GalleryEntry entry : galleryDefs.values()) {
            if (!scriptId.equals(entry.getScriptId())) continue;

            if (entry.getType().equals("ENDING")) {
                // 检查条件：如果 entry 有 condition 字段且 flags 不为空，则验证
                if (entry.getCondition() != null && !entry.getCondition().isEmpty() && playerFlags != null) {
                    if (!evaluateCondition(entry.getCondition(), playerFlags)) {
                        continue; // 条件不满足，不解锁
                    }
                }
                unlock(player, entry.getId());
            } else if (entry.getType().equals("CG")) {
                // CG 无条件解锁
                unlock(player, entry.getId());
            }
        }
    }

    /**
     * 评估条件表达式（格式: "flag_name=value"）
     */
    private boolean evaluateCondition(String condition, Map<String, Object> flags) {
        String[] parts = condition.split("=");
        if (parts.length < 2) return true; // 无有效条件，默认通过

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

    public List<GalleryEntry> getAllEntries() {
        return new ArrayList<>(galleryDefs.values());
    }

    public GalleryEntry getEntry(String id) {
        return galleryDefs.get(id);
    }

    public int getUnlockedCount(ServerPlayer player) {
        return getUnlockedSet(player).size();
    }

    public int getTotalCount() {
        return galleryDefs.size();
    }

    /**
     * 获取分类后的展示数据
     */
    public GalleryDisplayData getDisplayData(ServerPlayer player) {
        Set<String> unlocked = getUnlockedSet(player);
        List<GalleryEntry> cgs = new ArrayList<>();
        List<GalleryEntry> endings = new ArrayList<>();

        for (GalleryEntry entry : galleryDefs.values()) {
            if ("CG".equals(entry.getType())) cgs.add(entry);
            else endings.add(entry);
        }

        return new GalleryDisplayData(cgs, endings, unlocked);
    }

    // ==================== 持久化 ====================

    private Set<String> getUnlockedSet(ServerPlayer player) {
        return unlockedEntries.computeIfAbsent(player.getUUID(), uuid -> loadFromDisk(uuid));
    }

    private Set<String> loadFromDisk(UUID uuid) {
        Path file = saveDir.resolve(uuid + ".json");
        if (!Files.exists(file)) return ConcurrentHashMap.newKeySet();
        try (Reader reader = Files.newBufferedReader(file)) {
            return GSON.fromJson(reader, STRING_SET_TYPE);
        } catch (IOException e) {
            ShadeMod.LOGGER.error("[gallery] 加载失败: {}", uuid, e);
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
            ShadeMod.LOGGER.error("[gallery] 保存失败: {}", player.getUUID(), e);
        }
    }

    private void saveAll() {
        // 保存已加载的玩家进度
    }

    // ==================== 内部类 ====================

    public record GalleryDisplayData(
            List<GalleryEntry> cgs,
            List<GalleryEntry> endings,
            Set<String> unlockedIds
    ) {}
}
