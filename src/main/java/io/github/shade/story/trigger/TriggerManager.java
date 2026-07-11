package io.github.shade.story.trigger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.github.shade.ShadeMod;
import io.github.shade.story.StoryEngine;
import io.github.shade.story.adapter.AdapterRegistry;
import io.github.shade.story.network.StoryPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
 * 触发器管理器 — 管理所有剧情触发器的生命周期
 *
 * 职责：
 * 1. 触发器的 CRUD 和持久化
 * 2. 每 tick 检测区域进入
 * 3. 接收物品拾取和 NPC 交互事件
 * 4. 每个玩家记录已触发的触发器（防重复）
 */
public class TriggerManager {

    private static final Map<ServerLevel, TriggerManager> INSTANCES = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TRIGGER_LIST_TYPE = new TypeToken<List<StoryTrigger>>() {}.getType();

    private final ServerLevel world;
    private final Path saveFile;

    /** 所有触发器 */
    private final Map<String, StoryTrigger> triggers = new LinkedHashMap<>();

    /** 每个玩家已触发的触发器（防重复） */
    private final Map<UUID, Set<String>> firedTriggers = new ConcurrentHashMap<>();

    /** 每个玩家物品快照（用于检测拾取） */
    private final Map<UUID, Set<String>> lastInventorySnapshots = new ConcurrentHashMap<>();

    private int tickCounter = 0;

    private TriggerManager(ServerLevel world) {
        this.world = world;
        this.saveFile = world.getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve("data/shade/story/triggers.json");
        load();
    }

    // ==================== 单例 ====================

    public static TriggerManager getInstance(ServerLevel world) {
        return INSTANCES.computeIfAbsent(world, TriggerManager::new);
    }

    public static void cleanup(ServerLevel world) {
        TriggerManager mgr = INSTANCES.remove(world);
        if (mgr != null) mgr.save();
    }

    public static void cleanupAll() {
        for (TriggerManager mgr : INSTANCES.values()) mgr.save();
        INSTANCES.clear();
    }

    // ==================== 持久化 ====================

    private void load() {
        triggers.clear();
        if (!Files.exists(saveFile)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(saveFile)) {
            TriggerDataWrapper wrapper = GSON.fromJson(reader, TriggerDataWrapper.class);
            if (wrapper != null && wrapper.triggers != null) {
                for (StoryTrigger t : wrapper.triggers) {
                    triggers.put(t.getId(), t);
                }
            }
            ShadeMod.LOGGER.info("[trigger] 已加载 {} 个触发器", triggers.size());
        } catch (IOException e) {
            ShadeMod.LOGGER.error("[trigger] 加载触发器失败", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(saveFile.getParent());
            try (Writer writer = Files.newBufferedWriter(saveFile)) {
                TriggerDataWrapper wrapper = new TriggerDataWrapper();
                wrapper.triggers = new ArrayList<>(triggers.values());
                GSON.toJson(wrapper, writer);
            }
        } catch (IOException e) {
            ShadeMod.LOGGER.error("[trigger] 保存触发器失败", e);
        }
    }

    // ==================== CRUD ====================

    public void addTrigger(StoryTrigger trigger) {
        triggers.put(trigger.getId(), trigger);
        save();
        ShadeMod.LOGGER.info("[trigger] 添加触发器: {}", trigger);
    }

    public void removeTrigger(String id) {
        triggers.remove(id);
        save();
        ShadeMod.LOGGER.info("[trigger] 移除触发器: {}", id);
    }

    public StoryTrigger getTrigger(String id) {
        return triggers.get(id);
    }

    public List<StoryTrigger> getAllTriggers() {
        return new ArrayList<>(triggers.values());
    }

    // ==================== Tick 检测 ====================

    /**
     * 每 tick 调用 — 检测区域进入和物品拾取
     */
    public void tick() {
        tickCounter++;
        if (tickCounter % 10 != 0) return; // 每 0.5 秒检测一次

        for (ServerPlayer player : world.players()) {
            if (player.isSpectator()) continue;
            if (StoryEngine.getInstance(world).isInStory(player)) continue;

            int px = player.blockPosition().getX();
            int pz = player.blockPosition().getZ();

            // 区域触发检测
            for (StoryTrigger trigger : triggers.values()) {
                if (!"ZONE_ENTER".equals(trigger.getType())) continue;
                if (hasFired(player, trigger)) continue;
                if (trigger.isInZone(px, pz)) {
                    fireTrigger(player, trigger);
                }
            }

            // 物品拾取检测（通过比较背包快照）
            checkInventoryForPickup(player);
        }
    }

    /**
     * 检测玩家背包中新获得的物品 → 触发物品拾取触发器
     */
    private void checkInventoryForPickup(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Set<String> currentItems = new java.util.HashSet<>();
        for (var stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(stack.getItem()).toString();
                currentItems.add(id);
            }
        }

        Set<String> lastItems = lastInventorySnapshots.get(uuid);
        if (lastItems != null) {
            for (String itemId : currentItems) {
                if (!lastItems.contains(itemId)) {
                    checkItemPickup(player, itemId);
                }
            }
        }

        lastInventorySnapshots.put(uuid, currentItems);
    }

    // ==================== 事件触发 ====================

    /**
     * 检查物品拾取触发
     */
    public void checkItemPickup(ServerPlayer player, String itemId) {
        if (StoryEngine.getInstance(world).isInStory(player)) return;

        for (StoryTrigger trigger : triggers.values()) {
            if (!"ITEM_PICKUP".equals(trigger.getType())) continue;
            if (hasFired(player, trigger)) continue;

            if (trigger.matchesItem(itemId)) {
                fireTrigger(player, trigger);
                return;
            }
        }
    }

    /**
     * 检查 NPC 交互触发
     */
    public void checkNpcInteract(ServerPlayer player, String entityId) {
        if (StoryEngine.getInstance(world).isInStory(player)) return;

        for (StoryTrigger trigger : triggers.values()) {
            if (!"NPC_INTERACT".equals(trigger.getType())) continue;
            if (hasFired(player, trigger)) continue;

            if (trigger.matchesEntity(entityId)) {
                fireTrigger(player, trigger);
                return;
            }
        }
    }

    // ==================== 触发执行 ====================

    /**
     * 执行触发器 — 开始对应的剧情脚本
     */
    private void fireTrigger(ServerPlayer player, StoryTrigger trigger) {
        // 记录已触发
        firedTriggers.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet())
                .add(trigger.getId());

        // 检查脚本是否存在
        StoryEngine engine = StoryEngine.getInstance(world);
        var script = engine.getScript(trigger.getScriptId());
        if (script == null) {
            ShadeMod.LOGGER.warn("[trigger] 触发器 {} 指向的脚本 {} 不存在", trigger.getId(), trigger.getScriptId());
            return;
        }

        // 检查是否已完成过
        var progress = io.github.shade.story.StoryManager.getInstance(world).getProgress(player);
        if (progress.getCompletedScripts().contains(trigger.getScriptId()) && trigger.isOneTime()) {
            ShadeMod.LOGGER.debug("[trigger] 脚本 {} 已完成，跳过触发器", trigger.getScriptId());
            return;
        }

        // 开始剧情
        var startNode = engine.startScript(player, trigger.getScriptId());
        if (startNode != null) {
            StoryPayloads.sendNodeToClient(player, engine, startNode);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e✦ 触发剧情: §f" + script.getTitle()));
        }
    }

    /**
     * 检查玩家是否已触发过该触发器（防重复）
     */
    public boolean hasFired(ServerPlayer player, StoryTrigger trigger) {
        Set<String> fired = firedTriggers.get(player.getUUID());
        return fired != null && fired.contains(trigger.getId());
    }

    /**
     * 重置玩家触发器状态（使其可重新触发）
     */
    public void resetPlayerTriggers(ServerPlayer player) {
        firedTriggers.remove(player.getUUID());
    }

    // ==================== 内部类 ====================

    private static class TriggerDataWrapper {
        List<StoryTrigger> triggers = new ArrayList<>();
    }
}
