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
     * 优化：批量收集变化后再统一通知，避免 N+1 调用
     */
    public void scan(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Map<String, Integer> currentSnapshot = takeSnapshot(player);
        Map<String, Integer> lastSnapshot = snapshots.get(uuid);

        if (lastSnapshot != null) {
            // 第一步：批量收集所有增量变化
            var deltas = new java.util.ArrayList<Delta>();
            for (Map.Entry<String, Integer> entry : currentSnapshot.entrySet()) {
                String itemId = entry.getKey();
                int currentCount = entry.getValue();
                int lastCount = lastSnapshot.getOrDefault(itemId, 0);
                if (currentCount > lastCount) {
                    deltas.add(new Delta(itemId, currentCount - lastCount));
                }
            }

            // 第二步：统一通知（批量模式下只需一次 TriggerManager 查找）
            if (!deltas.isEmpty()) {
                var triggerManager = io.github.shade.story.trigger.TriggerManager.getInstance(player.serverLevel());
                for (Delta d : deltas) {
                    AdapterRegistry.notifyProgress(player, "COLLECT_ITEM", d.itemId, d.delta);
                    triggerManager.checkItemPickup(player, d.itemId);
                }
            }
        }

        snapshots.put(uuid, currentSnapshot);
    }

    /** 物品变化记录（避免循环内重复分配） */
    private record Delta(String itemId, int delta) {}

    /**
     * 获取玩家背包中指定物品的数量
     */
    public int getItemCount(ServerPlayer player, String itemId) {
        Map<String, Integer> snapshot = snapshots.get(player.getUUID());
        if (snapshot == null) return 0;
        return snapshot.getOrDefault(itemId, 0);
    }

    /** 缓存物品 ID 解析（减少 BuiltInRegistries 重复查找） */
    private static String getItemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /**
     * 获取玩家背包快照（优化：合并物品槽遍历减少方法调用）
     */
    private Map<String, Integer> takeSnapshot(ServerPlayer player) {
        Map<String, Integer> snapshot = new HashMap<>(48); // 预分配容量

        // 所有物品槽（主背包 36 + 盔甲 4 + 副手 1 = 41）
        scanSlots(snapshot, player.getInventory().items);
        scanSlots(snapshot, player.getInventory().armor);
        scanSlots(snapshot, player.getInventory().offhand);

        // 注意：个人 2x2 合成格属于 ContainerPlayer 容器，不属于 Inventory
        return snapshot;
    }

    /** 批量扫描一组物品槽到快照 */
    private void scanSlots(Map<String, Integer> snapshot, List<ItemStack> slots) {
        for (int i = 0, size = slots.size(); i < size; i++) {
            ItemStack stack = slots.get(i);
            if (!stack.isEmpty()) {
                String id = getItemId(stack);
                snapshot.merge(id, stack.getCount(), Integer::sum);
            }
        }
    }

    /**
     * 重置玩家快照
     */
    public void resetPlayer(ServerPlayer player) {
        snapshots.remove(player.getUUID());
    }
}
