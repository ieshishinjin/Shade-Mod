package io.github.shade.story;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.shade.ShadeMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 故事管理器 — 负责玩家进度的持久化存储
 *
 * 每个玩家独立保存进度，包括：
 * - 已完成的脚本列表
 * - 剧情 Flag（影响 CONDITION 节点判断）
 * - 上次游玩时间
 *
 * 保存位置：<world>/data/shade/story/players/<uuid>.json
 */
public class StoryManager {

    private static final Map<ServerLevel, StoryManager> INSTANCES = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final ServerLevel world;
    private final Path saveDir;
    private final Map<UUID, PlayerProgress> progressCache = new ConcurrentHashMap<>();

    private StoryManager(ServerLevel world) {
        this.world = world;
        this.saveDir = world.getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve("data/shade/story/players");
    }

    // ==================== 单例 ====================

    public static StoryManager getInstance(ServerLevel world) {
        return INSTANCES.computeIfAbsent(world, StoryManager::new);
    }

    public static void cleanup(ServerLevel world) {
        StoryManager mgr = INSTANCES.remove(world);
        if (mgr != null) {
            mgr.saveAll();
        }
    }

    public static void cleanupAll() {
        for (StoryManager mgr : INSTANCES.values()) {
            mgr.saveAll();
        }
        INSTANCES.clear();
    }

    // ==================== 进度加载/保存 ====================

    /**
     * 获取玩家进度（懒加载）
     */
    public PlayerProgress getProgress(ServerPlayer player) {
        return progressCache.computeIfAbsent(player.getUUID(), uuid -> {
            PlayerProgress progress = loadFromDisk(uuid);
            if (progress == null) {
                progress = new PlayerProgress(uuid);
            }
            return progress;
        });
    }

    /**
     * 保存指定玩家进度到磁盘
     */
    public void save(ServerPlayer player) {
        PlayerProgress progress = progressCache.get(player.getUUID());
        if (progress != null) {
            saveToDisk(player.getUUID(), progress);
        }
    }

    /**
     * 保存所有缓存的进度到磁盘
     */
    public void saveAll() {
        for (Map.Entry<UUID, PlayerProgress> entry : progressCache.entrySet()) {
            saveToDisk(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 加载玩家进度
     */
    public void load(ServerPlayer player) {
        // 清除缓存，下次 getProgress 时重新加载
        progressCache.remove(player.getUUID());
    }

    // ==================== Flag 操作 ====================

    /**
     * 设置剧情 Flag
     */
    public void setFlag(ServerPlayer player, String key, Object value) {
        PlayerProgress progress = getProgress(player);
        progress.flags.put(key, value);
        save(player);
    }

    /**
     * 获取剧情 Flag
     */
    public Object getFlag(ServerPlayer player, String key) {
        PlayerProgress progress = getProgress(player);
        return progress.flags.get(key);
    }

    /**
     * 检查 Flag 是否存在且为 true
     */
    public boolean hasFlag(ServerPlayer player, String key) {
        PlayerProgress progress = getProgress(player);
        Object value = progress.flags.get(key);
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() > 0;
        return value != null;
    }

    /**
     * 标记脚本为已完成
     */
    public void markScriptCompleted(ServerPlayer player, String scriptId) {
        PlayerProgress progress = getProgress(player);
        progress.completedScripts.add(scriptId);
        save(player);
    }

    /**
     * 检查脚本是否已完成
     */
    public boolean isScriptCompleted(ServerPlayer player, String scriptId) {
        PlayerProgress progress = getProgress(player);
        return progress.completedScripts.contains(scriptId);
    }

    // ==================== 磁盘 I/O ====================

    private Path getPlayerFile(UUID uuid) {
        return saveDir.resolve(uuid.toString() + ".json");
    }

    private PlayerProgress loadFromDisk(UUID uuid) {
        Path file = getPlayerFile(uuid);
        if (!Files.exists(file)) return null;

        try (Reader reader = Files.newBufferedReader(file)) {
            return GSON.fromJson(reader, PlayerProgress.class);
        } catch (IOException e) {
            ShadeMod.LOGGER.error("[story] 加载玩家进度失败: {}", uuid, e);
            return null;
        }
    }

    private void saveToDisk(UUID uuid, PlayerProgress progress) {
        try {
            Files.createDirectories(saveDir);
            try (Writer writer = Files.newBufferedWriter(getPlayerFile(uuid))) {
                GSON.toJson(progress, writer);
            }
        } catch (IOException e) {
            ShadeMod.LOGGER.error("[story] 保存玩家进度失败: {}", uuid, e);
        }
    }

    // ==================== 内部类 ====================

    /**
     * 玩家进度数据（持久化 JSON）
     */
    public static class PlayerProgress {
        private UUID playerUuid;
        private Set<String> completedScripts = new HashSet<>();
        private Map<String, Object> flags = new LinkedHashMap<>();
        private long lastPlayed;

        public PlayerProgress() {}

        public PlayerProgress(UUID playerUuid) {
            this.playerUuid = playerUuid;
            this.lastPlayed = System.currentTimeMillis();
        }

        public UUID getPlayerUuid() { return playerUuid; }
        public void setPlayerUuid(UUID playerUuid) { this.playerUuid = playerUuid; }

        public Set<String> getCompletedScripts() { return completedScripts; }
        public void setCompletedScripts(Set<String> completedScripts) {
            this.completedScripts = completedScripts;
        }

        public Map<String, Object> getFlags() { return flags; }
        public void setFlags(Map<String, Object> flags) { this.flags = flags; }

        public long getLastPlayed() { return lastPlayed; }
        public void setLastPlayed(long lastPlayed) { this.lastPlayed = lastPlayed; }

        /** 重置所有进度 */
        public void reset() {
            completedScripts.clear();
            flags.clear();
        }
    }
}
