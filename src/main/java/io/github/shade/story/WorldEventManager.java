package io.github.shade.story;

import io.github.shade.ShadeMod;
import io.github.shade.story.aigen.AiConfig;
import io.github.shade.story.aigen.AutoStoryGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 世界事件系统 — 随机世界事件，AI 自动生成引导剧情
 *
 * 事件类型：
 * - 流星雨：夜间天空出现流星，掉落特殊物品
 * - 怪物攻城：大量怪物出现在玩家附近
 * - 贸易车队：NPC 出现在特定位置，可交易稀有物品
 * - 神秘商人：特殊 NPC 短暂出现
 * - 地震：地面震动，暴露矿石
 */
public class WorldEventManager {

    private static WorldEventManager INSTANCE;

    /** 每位玩家的活跃事件 */
    private final Map<UUID, WorldEvent> activeEvents = new ConcurrentHashMap<>();

    /** 上次事件触发时间 */
    private long lastEventTime = 0;

    /** 两次事件最小间隔（游戏 tick） */
    private static final long EVENT_COOLDOWN_TICKS = 12000; // 10分钟

    private WorldEventManager() {}

    public static WorldEventManager getInstance() {
        if (INSTANCE == null) INSTANCE = new WorldEventManager();
        return INSTANCE;
    }

    /**
     * 每 tick 调用 — 检测并触发新事件
     */
    public void tick(ServerLevel level, int tickCount) {
        // 每 200 tick（10秒）检测一次
        if (tickCount % 200 != 0) return;
        if (level.players().isEmpty()) return;
        if (!AiConfig.getInstance(level).isEnabled()) return;

        long gameTime = level.getGameTime();
        if (gameTime - lastEventTime < EVENT_COOLDOWN_TICKS) return;

        // 5% 概率触发事件
        if (level.random.nextFloat() > 0.05f) return;

        triggerRandomEvent(level);
    }

    /**
     * 触发一个随机世界事件
     */
    private void triggerRandomEvent(ServerLevel level) {
        lastEventTime = level.getGameTime();

        // 选择一个随机玩家作为事件中心
        List<ServerPlayer> players = level.players();
        ServerPlayer target = players.get(level.random.nextInt(players.size()));

        // 随机选择事件类型
        WorldEvent event = switch (level.random.nextInt(3)) {
            case 0 -> createMeteorEvent(level, target);
            case 1 -> createSiegeEvent(level, target);
            case 2 -> createMerchantEvent(level, target);
            default -> null;
        };

        if (event == null) return;

        activeEvents.put(target.getUUID(), event);

        // 通知玩家
        String msg = switch (event.type) {
            case "METEOR" -> "§6✦ §e夜空中划过一道流星...";
            case "SIEGE" -> "§c⚔ §e你感觉到了一股邪恶的气息正在逼近！";
            case "MERCHANT" -> "§b✦ §e远处似乎有一支商队正在靠近...";
            default -> "§e✦ §e你感到周围的世界变得有些不一样了...";
        };
        target.sendSystemMessage(net.minecraft.network.chat.Component.literal("\n" + msg));
        target.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§7试试看附近发生了什么..."));

        // AI 联动：事件触发 → 生成剧情
        AutoStoryGenerator.getInstance().onStoryCompleted(target);

        ShadeMod.LOGGER.debug("[event] 世界事件触发: {} (玩家: {})",
                event.type, target.getName().getString());
    }

    /**
     * 流星雨事件
     */
    private WorldEvent createMeteorEvent(ServerLevel level, ServerPlayer player) {
        BlockPos pos = player.blockPosition().offset(
                level.random.nextInt(-200, 201), 0, level.random.nextInt(-200, 201));
        return new WorldEvent("METEOR", pos, "流星坠落", 6000);
    }

    /**
     * 怪物攻城事件
     */
    private WorldEvent createSiegeEvent(ServerLevel level, ServerPlayer player) {
        BlockPos pos = player.blockPosition().offset(
                level.random.nextInt(-50, 51), 0, level.random.nextInt(-50, 51));
        return new WorldEvent("SIEGE", pos, "怪物围攻", 12000);
    }

    /**
     * 贸易车队事件
     */
    private WorldEvent createMerchantEvent(ServerLevel level, ServerPlayer player) {
        BlockPos pos = player.blockPosition().offset(
                level.random.nextInt(-100, 101), 0, level.random.nextInt(-100, 101));
        return new WorldEvent("MERCHANT", pos, "神秘商队", 8000);
    }

    // ==================== 查询 ====================

    public WorldEvent getActiveEvent(UUID playerUuid) {
        WorldEvent event = activeEvents.get(playerUuid);
        return event;
    }

    public void clearEvent(UUID playerUuid) {
        activeEvents.remove(playerUuid);
    }

    public static void cleanup() {
        if (INSTANCE != null) {
            INSTANCE.activeEvents.clear();
        }
        INSTANCE = null;
    }

    // ==================== 事件数据 ====================

    public static class WorldEvent {
        public final String type;
        public final BlockPos position;
        public final String name;
        public final long durationTicks;

        WorldEvent(String type, BlockPos position, String name, long durationTicks) {
            this.type = type;
            this.position = position;
            this.name = name;
            this.durationTicks = durationTicks;
        }
    }
}
