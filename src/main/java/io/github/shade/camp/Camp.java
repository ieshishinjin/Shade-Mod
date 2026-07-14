package io.github.shade.camp;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.*;


public class Camp {

    /** 据点功能的独立命名空间 */
    public static final String CAMP_ID = "shadecamp";

    public enum Type {
        NORMAL,
        BOSS,
        RESOURCE,
        PUZZLE
    }

    public enum Status {
        /** 空闲 - 怪物存在但未激活 */
        IDLE,
        /** 战斗中 - 怪物已激活，正在与玩家战斗 */
        FIGHTING,
        /** 已清空 - 所有怪物已死亡，宝箱已出现或已被开启 */
        CLEARED
    }

    private String name;
    private Type type = Type.NORMAL;     // 据点类型
    private int[] position;              // [x, y, z] 据点中心坐标
    private int triggerRange = 12;       // 触发半径
    private Map<String, Integer> mobConfig = new LinkedHashMap<>(); // 实体ID -> 数量
    private String lootTable = "minecraft:chests/simple_dungeon";   // 战利品表
    private int[] chestPosition;         // 宝箱位置
    private Status status = Status.IDLE;
    private int refreshTime = 0;         // 刷新时间（秒），0=永不
    private List<int[]> safeSpawnPoints = new ArrayList<>(); // 安全生成点列表
    private long lastClearedTime = 0;    // 上次清空时间戳

    // === 运行时字段（不序列化） ===
    /** 当前已生成怪物的 UUID（用于序列化/持久化） */
    private transient Set<UUID> activeMobIds = new HashSet<>();
    /** 当前已生成怪物的实体引用（直接持有，不用 UUID 反查避免查找失败） */
    private transient List<net.minecraft.world.entity.Entity> activeEntities = new ArrayList<>();
    /** 屏幕顶部的 BOSS 进度条 */
    private transient ServerBossEvent bossBar;
    /** 进入战斗时的游戏 tick */
    private transient long enteredFightingTick = 0;
    /** 上次生成怪物的 tick（防止无限循环刷新） */
    private transient long lastSpawnedTick = -12000;

    public Camp() {
    }

    public Camp(String name, BlockPos pos) {
        this.name = name;
        this.position = new int[]{pos.getX(), pos.getY(), pos.getZ()};
        this.chestPosition = new int[]{pos.getX(), pos.getY(), pos.getZ()};
    }

    // === 位置转换辅助方法 ===

    public BlockPos getBlockPos() {
        return new BlockPos(position[0], position[1], position[2]);
    }

    public void setBlockPos(BlockPos pos) {
        this.position = new int[]{pos.getX(), pos.getY(), pos.getZ()};
    }

    public BlockPos getChestBlockPos() {
        if (chestPosition == null) return getBlockPos();
        return new BlockPos(chestPosition[0], chestPosition[1], chestPosition[2]);
    }

    public void setChestBlockPos(BlockPos pos) {
        this.chestPosition = new int[]{pos.getX(), pos.getY(), pos.getZ()};
    }

    public List<BlockPos> getSafeSpawnBlockPositions() {
        List<BlockPos> result = new ArrayList<>();
        if (safeSpawnPoints != null) {
            for (int[] p : safeSpawnPoints) {
                result.add(new BlockPos(p[0], p[1], p[2]));
            }
        }
        return result;
    }

    public void setSafeSpawnPointsFromBlocks(List<BlockPos> points) {
        this.safeSpawnPoints = new ArrayList<>();
        for (BlockPos p : points) {
            this.safeSpawnPoints.add(new int[]{p.getX(), p.getY(), p.getZ()});
        }
    }

    // === 击杀进度 Boss Bar ===

    /**
     * 获取或创建 BOSS 进度条
     */
    public ServerBossEvent getOrCreateBossBar() {
        if (bossBar == null) {
            bossBar = new ServerBossEvent(
                    makeBossBarTitle(0),
                    BossEvent.BossBarColor.GREEN,
                    BossEvent.BossBarOverlay.PROGRESS
            );
            bossBar.setVisible(true);
        }
        return bossBar;
    }

    /**
     * 移除 BOSS 进度条
     */
    public void removeBossBar() {
        if (bossBar != null) {
            bossBar.removeAllPlayers();
            bossBar.setVisible(false);
            bossBar = null;
        }
    }

    /**
     * 更新 BOSS 进度条的名称和进度
     */
    public void updateBossBar(int aliveCount) {
        if (bossBar != null) {
            int total = getTotalMobCount();
            if (total < 1) total = 1;
            float progress = (float) aliveCount / total;
            bossBar.setName(Component.translatable("shadecamp.bossbar.title", name, total - aliveCount, total));
            bossBar.setProgress(progress);
        }
    }

    /**
     * 为附近玩家添加/移除 BOSS 进度条
     */
    public void syncBossBarPlayers(List<ServerPlayer> playersInRange, int aliveCount) {
        getOrCreateBossBar();
        // 添加范围内的玩家
        for (ServerPlayer player : playersInRange) {
            if (!bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            }
        }
        // 移除范围外的玩家
        Set<ServerPlayer> toRemove = new HashSet<>();
        for (ServerPlayer existing : bossBar.getPlayers()) {
            if (!playersInRange.contains(existing)) {
                toRemove.add(existing);
            }
        }
        for (ServerPlayer player : toRemove) {
            bossBar.removePlayer(player);
        }
        updateBossBar(aliveCount);
    }

    /**
     * 生成 BOSS 进度条标题（使用 translatable）
     */
    private Component makeBossBarTitle(int killed) {
        int total = getTotalMobCount();
        return Component.translatable("shadecamp.bossbar.title", name, killed, total);
    }

    // === Getters & Setters ===

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public int[] getPosition() { return position; }
    public void setPosition(int[] position) { this.position = position; }

    public int getTriggerRange() { return triggerRange; }
    public void setTriggerRange(int triggerRange) { this.triggerRange = triggerRange; }

    public Map<String, Integer> getMobConfig() { return mobConfig; }
    public void setMobConfig(Map<String, Integer> mobConfig) { this.mobConfig = mobConfig; }

    public String getLootTable() { return lootTable; }
    public void setLootTable(String lootTable) { this.lootTable = lootTable; }

    public int[] getChestPosition() { return chestPosition; }
    public void setChestPosition(int[] chestPosition) { this.chestPosition = chestPosition; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public int getRefreshTime() { return refreshTime; }
    public void setRefreshTime(int refreshTime) { this.refreshTime = refreshTime; }

    public List<int[]> getSafeSpawnPoints() { return safeSpawnPoints; }
    public void setSafeSpawnPoints(List<int[]> safeSpawnPoints) { this.safeSpawnPoints = safeSpawnPoints; }

    public long getLastClearedTime() { return lastClearedTime; }
    public void setLastClearedTime(long lastClearedTime) { this.lastClearedTime = lastClearedTime; }

    public Set<UUID> getActiveMobIds() { return activeMobIds; }
    public void setActiveMobIds(Set<UUID> activeMobIds) { this.activeMobIds = activeMobIds; }

    public List<net.minecraft.world.entity.Entity> getActiveEntities() { return activeEntities; }
    public void clearActiveEntities() { this.activeEntities.clear(); this.activeMobIds.clear(); }
    public void addActiveEntity(net.minecraft.world.entity.Entity entity) {
        this.activeEntities.add(entity);
        this.activeMobIds.add(entity.getUUID());
    }

    public long getEnteredFightingTick() { return enteredFightingTick; }
    public void setEnteredFightingTick(long tick) { this.enteredFightingTick = tick; }
    public long getLastSpawnedTick() { return lastSpawnedTick; }
    public void setLastSpawnedTick(long tick) { this.lastSpawnedTick = tick; }

    public int getTotalMobCount() {
        return mobConfig.values().stream().mapToInt(Integer::intValue).sum();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Camp camp)) return false;
        return Objects.equals(name, camp.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
