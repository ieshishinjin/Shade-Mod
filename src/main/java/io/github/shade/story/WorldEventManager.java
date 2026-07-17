package io.github.shade.story;

import io.github.shade.ShadeMod;
import io.github.shade.story.aigen.AiConfig;
import io.github.shade.story.aigen.AutoStoryGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.core.particles.ParticleTypes;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 世界事件系统 — 随机世界事件，AI 自动生成引导剧情
 *
 * 事件类型：
 * - 流星雨：天空划落流星，撞击地面产生爆炸和火焰
 * - 怪物攻城：玩家附近刷新大量敌对怪物
 * - 贸易车队：流浪商人和羊驼出现在附近
 * - 神秘商人：特殊 NPC 短暂出现
 * - 地震：地面震动，暴露矿石
 */
public class WorldEventManager {

    private static WorldEventManager INSTANCE;

    /** 每位玩家的活跃事件 */
    private final Map<UUID, WorldEvent> activeEvents = new ConcurrentHashMap<>();

    /** 每位玩家的上次事件触发时间（游戏 tick） */
    private final Map<UUID, Long> perPlayerLastEventTime = new ConcurrentHashMap<>();

    /** 两次事件最小间隔（游戏 tick） */
    private static final long EVENT_COOLDOWN_TICKS = 12000; // 10分钟

    private WorldEventManager() {}

    public static WorldEventManager getInstance() {
        if (INSTANCE == null) INSTANCE = new WorldEventManager();
        return INSTANCE;
    }

    /**
     * 每 tick 调用 — 检测并触发新事件，同时更新活跃事件
     */
    public void tick(ServerLevel level, int tickCount) {
        // 每 200 tick（10秒）检测一次
        if (tickCount % 200 != 0) return;
        if (level.players().isEmpty()) return;
        if (!AiConfig.getInstance(level).isEnabled()) return;

        long gameTime = level.getGameTime();

        // 清理过期事件
        cleanupExpiredEvents(gameTime);

        // 更新活跃事件的效果
        tickActiveEvents(level, gameTime);

        // 遍历所有玩家，检查冷却和触发
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            UUID uuid = player.getUUID();

            Long lastTime = perPlayerLastEventTime.get(uuid);
            if (lastTime != null && gameTime - lastTime < EVENT_COOLDOWN_TICKS) continue;

            // 5% 概率触发事件
            if (level.random.nextFloat() > 0.05f) continue;

            // 检查是否已有活跃事件（同一玩家不叠加）
            if (activeEvents.containsKey(uuid)) continue;

            triggerRandomEvent(level, player);
            return; // 每次 tick 至多触发一个事件
        }
    }

    /**
     * 更新活跃事件的世界效果
     */
    private void tickActiveEvents(ServerLevel level, long gameTime) {
        for (Map.Entry<UUID, WorldEvent> entry : activeEvents.entrySet()) {
            WorldEvent event = entry.getValue();
            long elapsed = gameTime - event.startTime;

            switch (event.type) {
                case "METEOR" -> {
                    // 流星雨：每 60 tick 在事件位置附近生成一个流星实体
                    if (elapsed % 60 == 0 && elapsed < event.durationTicks / 2) {
                        spawnMeteor(level, event);
                    }
                }
                case "SIEGE" -> {
                    // 怪物攻城：通过 event.siegeMobIds 追踪怪物存活情况
                    if (elapsed > 0 && elapsed % 400 == 0 && event.spawnedEntities > 0) {
                        event.siegeMobIds.removeIf(uuid -> {
                            var entity = level.getEntity(uuid);
                            return entity == null || !entity.isAlive();
                        });
                        if (event.siegeMobIds.isEmpty()) {
                            var targetPlayer = (net.minecraft.server.level.ServerPlayer) level.getPlayerByUUID(entry.getKey());
                            if (targetPlayer != null) {
                                spawnSiegeMobs(level, event, targetPlayer);
                                targetPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                        "§c⚔ 怪物的援军到了！"));
                            }
                        }
                    }
                }
                case "MERCHANT" -> {
                    // 商队：存在期间在事件位置播放粒子效果
                    if (elapsed % 100 == 0) {
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                event.position.getX() + 0.5, event.position.getY() + 2, event.position.getZ() + 0.5,
                                3, 1.5, 0.5, 1.5, 0);
                    }
                }
            }
        }
    }

    /**
     * 清理过期的事件
     */
    private void cleanupExpiredEvents(long gameTime) {
        activeEvents.entrySet().removeIf(entry -> {
            WorldEvent event = entry.getValue();
            if (gameTime > event.startTime + event.durationTicks) {
                // 事件结束时的清理
                switch (event.type) {
                    case "METEOR" -> {
                        // 清理所有事件标记的实体
                    }
                    case "SIEGE" -> {
                        // 怪物攻城结束 - 事件数据自行清理
                        event.siegeMobIds.clear();
                    }
                }
                return true;
            }
            return false;
        });
    }

    /**
     * 为指定玩家触发一个随机世界事件
     */
    private void triggerRandomEvent(ServerLevel level, ServerPlayer target) {
        perPlayerLastEventTime.put(target.getUUID(), level.getGameTime());

        // 随机选择事件类型
        WorldEvent event = switch (level.random.nextInt(3)) {
            case 0 -> createMeteorEvent(level, target);
            case 1 -> createSiegeEvent(level, target);
            case 2 -> createMerchantEvent(level, target);
            default -> null;
        };

        if (event == null) return;

        activeEvents.put(target.getUUID(), event);

        // 执行事件效果
        switch (event.type) {
            case "METEOR" -> triggerMeteor(level, event, target);
            case "SIEGE" -> triggerSiege(level, event, target);
            case "MERCHANT" -> triggerMerchant(level, event, target);
        }

        // AI 联动：事件触发 → 生成剧情
        AutoStoryGenerator.getInstance().onStoryCompleted(target);

        ShadeMod.LOGGER.debug("[event] 世界事件触发: {} (玩家: {})",
                event.type, target.getName().getString());
    }

    // ==================== 流星雨事件 ====================

    /**
     * 触发流星雨 — 玩家位置附近召唤多个流星实体
     */
    private void triggerMeteor(ServerLevel level, WorldEvent event, ServerPlayer player) {
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\n§6✦ §e夜空中划过一道流星..."));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§7天空中有火光划过，流星坠落在了远方！"));
        player.playNotifySound(net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.AMBIENT, 1.0f, 1.0f);

        // 立即生成 3 颗流星
        for (int i = 0; i < 3; i++) {
            spawnMeteor(level, event);
        }
    }

    /**
     * 生成一颗流星实体
     */
    private void spawnMeteor(ServerLevel level, WorldEvent event) {
        BlockPos pos = event.position;
        var random = level.random;

        // 在事件位置附近随机偏移
        int ox = random.nextInt(40) - 20;
        int oz = random.nextInt(40) - 20;
        BlockPos impactPos = pos.offset(ox, 0, oz);

        // 找到地表高度
        int topY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, impactPos.getX(), impactPos.getZ());
        if (topY <= level.getMinBuildHeight()) return;

        // 在高空产生火焰粒子效果
        BlockPos particlePos = new BlockPos(impactPos.getX(), topY + 30 + random.nextInt(20), impactPos.getZ());
        level.sendParticles(ParticleTypes.FLAME,
                particlePos.getX(), particlePos.getY(), particlePos.getZ(),
                20, 2.0, 0.5, 2.0, 0.1);
        level.sendParticles(ParticleTypes.SMOKE,
                particlePos.getX(), particlePos.getY(), particlePos.getZ(),
                10, 1.5, 0.3, 1.5, 0.05);
        level.sendParticles(ParticleTypes.EXPLOSION,
                impactPos.getX(), topY + 1, impactPos.getZ(),
                1, 1.0, 0.5, 1.0, 0);
    }

    // ==================== 怪物攻城事件 ====================

    /**
     * 触发怪物攻城 — 在玩家附近生成成群怪物
     */
    private void triggerSiege(ServerLevel level, WorldEvent event, ServerPlayer player) {
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\n§c⚔ §e你感觉到了一股邪恶的气息正在逼近！"));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§7四周响起了低沉的嘶吼声...准备好战斗吧！"));
        player.playNotifySound(net.minecraft.sounds.SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 1.0f, 1.0f);

        int count = spawnSiegeMobs(level, event, player);
        event.spawnedEntities = count;

        ShadeMod.LOGGER.debug("[event] 怪物攻城: 生成 {} 只怪物", count);
    }

    /**
     * 在事件位置附近生成攻城怪物，UUID 记录在 event.siegeMobIds 中
     */
    private int spawnSiegeMobs(ServerLevel level, WorldEvent event, ServerPlayer player) {
        BlockPos center = player.blockPosition();
        var random = level.random;

        // 可生成的怪物类型
        String[] mobTypes = {"minecraft:zombie", "minecraft:skeleton", "minecraft:spider",
                "minecraft:creeper", "minecraft:husk", "minecraft:stray"};
        int count = 4 + random.nextInt(4); // 4~7 只

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            // 在玩家附近 15~30 格随机位置生成
            double angle = random.nextDouble() * 2 * Math.PI;
            int dist = 15 + random.nextInt(16);
            int dx = (int) (Math.cos(angle) * dist);
            int dz = (int) (Math.sin(angle) * dist);
            BlockPos spawnPos = center.offset(dx, 0, dz);

            // 找到地表
            int topY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, spawnPos.getX(), spawnPos.getZ());
            if (topY <= level.getMinBuildHeight()) continue;

            spawnPos = new BlockPos(spawnPos.getX(), topY, spawnPos.getZ());

            // 不能离玩家太近
            if (spawnPos.closerThan(player.blockPosition(), 10)) continue;
            // 不能在水里
            if (!level.getBlockState(spawnPos).isAir() && !level.getBlockState(spawnPos.above()).isAir()) continue;

            String mobId = mobTypes[random.nextInt(mobTypes.length)];
            var entityType = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(mobId));
            if (entityType == null) continue;

            var entity = entityType.create(level);
            if (entity instanceof Mob mob) {
                mob.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                mob.setPersistenceRequired();
                mob.setAggressive(true);
                mob.setTarget(player);
                level.addFreshEntity(mob);
                event.siegeMobIds.add(mob.getUUID());
                spawned++;
            }
        }
        return spawned;
    }

    /**
     * 清除所有攻城怪物（由 siegeMobIds 追踪，事件过期时自动清空列表）
     */
    // removeSiegeMobs 已由 siegeMobIds 追踪替代

    // ==================== 贸易车队事件 ====================

    /**
     * 触发贸易车队 — 生成流浪商人和羊驼
     */
    private void triggerMerchant(ServerLevel level, WorldEvent event, ServerPlayer player) {
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\n§b✦ §e远处似乎有一支商队正在靠近..."));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§7在 " + formatPos(event.position) + " 附近出现了一支商队！"));
        player.playNotifySound(SoundEvents.VILLAGER_YES, SoundSource.AMBIENT, 1.0f, 1.0f);

        BlockPos pos = event.position;
        int topY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        if (topY <= level.getMinBuildHeight()) return;

        BlockPos spawnPos = new BlockPos(pos.getX(), topY, pos.getZ());

        // 生成流浪商人
        var trader = EntityType.WANDERING_TRADER.create(level);
        if (trader != null) {
            trader.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
            trader.setPersistenceRequired();
            level.addFreshEntity(trader);
        }

        // 生成 2 只交易羊驼
        for (int i = 0; i < 2; i++) {
            var llama = EntityType.TRADER_LLAMA.create(level);
            if (llama != null) {
                llama.setPos(spawnPos.getX() + 0.5 + (i - 0.5) * 2, spawnPos.getY(), spawnPos.getZ() + 0.5);
                llama.setPersistenceRequired();
                level.addFreshEntity(llama);
            }
        }

        // 粒子标记
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                spawnPos.getX() + 0.5, spawnPos.getY() + 2, spawnPos.getZ() + 0.5,
                15, 1.5, 1.0, 1.5, 0);
    }

    // ==================== 事件创建 ====================

    /**
     * 流星雨事件 — 在玩家远处生成落地点
     */
    private WorldEvent createMeteorEvent(ServerLevel level, ServerPlayer player) {
        BlockPos pos = player.blockPosition().offset(
                level.random.nextInt(-200, 201), 0, level.random.nextInt(-200, 201));
        return new WorldEvent("METEOR", pos, "流星坠落", 6000, level.getGameTime());
    }

    /**
     * 怪物攻城事件 — 以玩家当前位置为中心
     */
    private WorldEvent createSiegeEvent(ServerLevel level, ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        return new WorldEvent("SIEGE", pos, "怪物围攻", 12000, level.getGameTime());
    }

    /**
     * 贸易车队事件 — 在玩家附近生成商队
     */
    private WorldEvent createMerchantEvent(ServerLevel level, ServerPlayer player) {
        BlockPos pos = player.blockPosition().offset(
                level.random.nextInt(-100, 101), 0, level.random.nextInt(-100, 101));
        return new WorldEvent("MERCHANT", pos, "神秘商队", 8000, level.getGameTime());
    }

    // ==================== 查询 ====================

    public WorldEvent getActiveEvent(UUID playerUuid) {
        return activeEvents.get(playerUuid);
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

    // ==================== 工具 ====================

    private String formatPos(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getZ() + ")";
    }

    // ==================== 事件数据 ====================

    public static class WorldEvent {
        public final String type;
        public final BlockPos position;
        public final String name;
        public final long durationTicks;
        public final long startTime;
        /** 生成实体计数（用于攻城/商队） */
        public int spawnedEntities = 0;
        /** 攻城怪物的 UUID（用于追踪存活情况，替代 PersistentData） */
        public final java.util.Set<java.util.UUID> siegeMobIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

        WorldEvent(String type, BlockPos position, String name, long durationTicks) {
            this.type = type;
            this.position = position;
            this.name = name;
            this.durationTicks = durationTicks;
            this.startTime = 0;
        }

        WorldEvent(String type, BlockPos position, String name, long durationTicks, long startTime) {
            this.type = type;
            this.position = position;
            this.name = name;
            this.durationTicks = durationTicks;
            this.startTime = startTime;
        }
    }
}
