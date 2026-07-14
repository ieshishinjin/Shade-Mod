package io.github.shade.story;

import io.github.shade.ShadeMod;
import io.github.shade.story.adapter.AdapterRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 区域追踪器 — 管理命名区域（Zone），检测玩家进入特定区域
 *
 * 用于 REACH_LOCATION Objective 类型。当玩家进入某个命名区域时，
 * 通知 Quest 系统更新对应进度。
 *
 * 区域数据可来自：
 * 1. 命令动态添加
 * 2. 配置文件加载
 * 3. TriggerManager 的区域触发器
 */
public class ZoneTracker {

    private static final Map<String, ZoneTracker> INSTANCES = new ConcurrentHashMap<>();

    /** 命名区域列表：名称 → 区域边界 */
    private final Map<String, Zone> zones = new LinkedHashMap<>();

    /** 每位玩家已到达的区域集合（防重复触发） */
    private final Map<UUID, Set<String>> reachedZones = new ConcurrentHashMap<>();

    private ZoneTracker() {}

    public static ZoneTracker getInstance() {
        // 全局单例，不依赖世界实例
        return INSTANCES.computeIfAbsent("global", k -> new ZoneTracker());
    }

    // ==================== 区域管理 ====================

    /**
     * 添加一个矩形区域
     *
     * @param name 区域名称（与 REACH_LOCATION 的 targetId 匹配）
     * @param x1   角1 X 坐标
     * @param z1   角1 Z 坐标
     * @param x2   角2 X 坐标
     * @param z2   角2 Z 坐标
     */
    public void addZone(String name, int x1, int z1, int x2, int z2) {
        Zone zone = new Zone(
                Math.min(x1, x2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(z1, z2)
        );
        zones.put(name, zone);
        ShadeMod.LOGGER.debug("[zone] 添加区域: {}", name);
    }

    /**
     * 移除一个区域
     */
    public void removeZone(String name) {
        zones.remove(name);
    }

    /**
     * 获取所有区域名称
     */
    public Set<String> getZoneNames() {
        return zones.keySet();
    }

    /**
     * 获取区域信息
     */
    public Zone getZone(String name) {
        return zones.get(name);
    }

    /**
     * 检查区域是否存在
     */
    public boolean hasZone(String name) {
        return zones.containsKey(name);
    }

    // ==================== 检测逻辑 ====================

    /**
     * 检查玩家是否在已注册的区域内，触发 REACH_LOCATION 进度更新
     *
     * @param player 目标玩家
     */
    public void checkPlayerPosition(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        UUID uuid = player.getUUID();

        for (Map.Entry<String, Zone> entry : zones.entrySet()) {
            String zoneName = entry.getKey();
            Zone zone = entry.getValue();

            if (zone.contains(pos.getX(), pos.getZ())) {
                // 玩家在区域内
                Set<String> reached = reachedZones.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
                if (reached.add(zoneName)) {
                    // 首次进入该区域 → 通知 Quest 系统
                    ShadeMod.LOGGER.debug("[zone] 玩家 {} 到达区域: {}",
                            player.getName().getString(), zoneName);
                    AdapterRegistry.notifyProgress(player, "REACH_LOCATION", zoneName, 1);
                }
            }
        }
    }

    /**
     * 重置玩家的到达记录（使其可重新触发）
     */
    public void resetPlayer(ServerPlayer player) {
        reachedZones.remove(player.getUUID());
    }

    // ==================== 内部类 ====================

    /**
     * 矩形区域定义
     */
    public static class Zone {
        public final int x1, z1, x2, z2;

        Zone(int x1, int z1, int x2, int z2) {
            this.x1 = x1;
            this.z1 = z1;
            this.x2 = x2;
            this.z2 = z2;
        }

        public boolean contains(int x, int z) {
            return x >= x1 && x <= x2 && z >= z1 && z <= z2;
        }

        public int getCenterX() { return (x1 + x2) / 2; }
        public int getCenterZ() { return (z1 + z2) / 2; }
    }
}
