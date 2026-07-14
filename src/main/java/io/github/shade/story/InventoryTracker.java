package io.github.shade.story;

import io.github.shade.ShadeMod;
import io.github.shade.story.adapter.AdapterRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 背包追踪器 — 检测玩家物品变化（新获得/合成/交易）
 *
 * 通过定期扫描玩家背包快照，比较前后差异来检测：
 * - COLLECT_ITEM — 新物品出现在背包中
 * - CRAFT_ITEM — 物品数量增加（通过合成）
 * - TRADE_VILLAGER — 物品数量增加（通过交易）
 *
 * 注意：由于无法精确区分获得途径，所有新物品都触发 COLLECT_ITEM。
 * CRAFT_ITEM 和 TRADE_VILLAGER 需要混入或特定事件支持。
 */
public class InventoryTracker {

    private static InventoryTracker INSTANCE;

    /** 每位玩家的物品快照：物品ID → 数量 */
    private final Map<UUID, Map<String, Integer>> snapshots = new ConcurrentHashMap<>();

    private InventoryTracker() {}

    public static InventoryTracker getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new InventoryTracker();
        }
        return INSTANCE;
    }

    /**
     * 扫描玩家背包，检测物品变化
     * 在服务器 tick 中定期调用
     */
    public void scan(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Map<String, Integer> currentSnapshot = takeSnapshot(player);
        Map<String, Integer> lastSnapshot = snapshots.get(uuid);

        if (lastSnapshot != null) {
            // 检测物品数量增加
            for (Map.Entry<String, Integer> entry : currentSnapshot.entrySet()) {
                String itemId = entry.getKey();
                int currentCount = entry.getValue();
                int lastCount = lastSnapshot.getOrDefault(itemId, 0);

                if (currentCount > lastCount) {
                    int delta = currentCount - lastCount;
                    // 通知 Quest 系统（日志已移除，减少控制台输出）
                    AdapterRegistry.notifyProgress(player, "COLLECT_ITEM", itemId, delta);
                }
            }
        }

        snapshots.put(uuid, currentSnapshot);
    }

    /**
     * 获取玩家背包中指定物品的数量
     */
    public int getItemCount(ServerPlayer player, String itemId) {
        Map<String, Integer> snapshot = snapshots.get(player.getUUID());
        if (snapshot == null) return 0;
        return snapshot.getOrDefault(itemId, 0);
    }

    /**
     * 获取玩家背包快照
     */
    private Map<String, Integer> takeSnapshot(ServerPlayer player) {
        Map<String, Integer> snapshot = new HashMap<>();

        // 主背包
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                snapshot.merge(id, stack.getCount(), Integer::sum);
            }
        }

        // 快捷栏
        for (ItemStack stack : player.getInventory().armor) {
            if (!stack.isEmpty()) {
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                snapshot.merge(id, stack.getCount(), Integer::sum);
            }
        }

        // 副手
        ItemStack offhand = player.getInventory().offhand.get(0);
        if (!offhand.isEmpty()) {
            String id = BuiltInRegistries.ITEM.getKey(offhand.getItem()).toString();
            snapshot.merge(id, offhand.getCount(), Integer::sum);
        }

        return snapshot;
    }

    /**
     * 重置玩家快照
     */
    public void resetPlayer(ServerPlayer player) {
        snapshots.remove(player.getUUID());
    }
}
