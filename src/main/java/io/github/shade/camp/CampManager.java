
package io.github.shade.camp;
import io.github.shade.story.adapter.AdapterRegistry;
import io.github.shade.worldlevel.WorldLevel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.github.shade.ShadeMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Camp 管理器 — CRUD、持久化、Tick 主循环 */
public class CampManager {

    private static final Type CAMP_LIST_TYPE = new TypeToken<List<Camp>>() {}.getType();
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    /** 包级可见，供 CampEventHandler 访问 */
    static final Map<ServerLevel, CampManager> INSTANCES = new ConcurrentHashMap<>();

    private final ServerLevel world;
    private final Path saveFile;
    private final Map<String, Camp> camps = new LinkedHashMap<>();

    /** 待创建的候选据点（自动生成用，存 BlockPos） */
    private final List<BlockPos> pendingCamps = new ArrayList<>();

    public boolean isCampMob(java.util.UUID uuid) {
        for (Camp camp : camps.values()) {
            if (camp.getActiveMobIds().contains(uuid)) return true;
        }
        return false;
    }
    /** 是否已完成本种子的自动生成 */
    private boolean seedGenerated = false;

    private int tickCounter = 0;

    private CampManager(ServerLevel world) {
        this.world = world;
        this.saveFile = world.getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve("data/shadecamp/camps.json");
    }

    // ==================== 单例 ====================

    public static CampManager getInstance(ServerLevel world) {
        return INSTANCES.computeIfAbsent(world, k -> {
            CampManager mgr = new CampManager(world);
            mgr.load();
            return mgr;
        });
    }

    public static void cleanup(ServerLevel world) {
        CampManager mgr = INSTANCES.remove(world);
        if (mgr != null) {
            mgr.save();
        }
    }

    public static void cleanupAll() {
        for (CampManager mgr : INSTANCES.values()) {
            mgr.save();
        }
        INSTANCES.clear();
    }

    // ==================== 持久化 ====================

    /**
     * 从磁盘加载据点数据
     */
    public void load() {
        camps.clear();
        if (!Files.exists(saveFile)) {
            ShadeMod.LOGGER.info("未找到据点数据文件，将创建新文件: {}", saveFile);
            save(); // 创建默认文件
            return;
        }

        try (Reader reader = Files.newBufferedReader(saveFile)) {
            CampDataWrapper wrapper = GSON.fromJson(reader, CampDataWrapper.class);
            if (wrapper != null && wrapper.camps != null) {
                for (Camp camp : wrapper.camps) {
                    // 重置运行时状态
                    camp.clearActiveEntities();
                    camps.put(camp.getName(), camp);
                }
            }
            ShadeMod.LOGGER.info("已加载 {} 个据点", camps.size());
        } catch (IOException e) {
            ShadeMod.LOGGER.error("加载据点数据失败", e);
        }
    }

    /**
     * 保存据点数据到磁盘
     */
    public void save() {
        try {
            Files.createDirectories(saveFile.getParent());
            try (Writer writer = Files.newBufferedWriter(saveFile)) {
                CampDataWrapper wrapper = new CampDataWrapper();
                wrapper.camps = new ArrayList<>(camps.values());
                // 保存已生成的种子标记
                if (seedGenerated && !wrapper.generatedSeeds.contains(
                        CampWorldGenerator.GENERATION_FLAG_PREFIX + world.getSeed())) {
                    wrapper.generatedSeeds.add(
                            CampWorldGenerator.GENERATION_FLAG_PREFIX + world.getSeed());
                }
                GSON.toJson(wrapper, writer);
            }
        } catch (IOException e) {
            ShadeMod.LOGGER.error("保存据点数据失败", e);
        }
    }

    // ==================== CRUD ====================

    /**
     * 创建据点
     */
    public Camp createCamp(String name, BlockPos pos) {
        if (camps.containsKey(name)) {
            return null;
        }

        Camp camp = new Camp(name, pos);
        camp.setSafeSpawnPointsFromBlocks(
                CampSpawnValidator.findSafeSpawnPoints(world, pos, 8)
        );

        // 根据生物群系生成随机怪物配置
        var biome = world.getBiome(pos);
        List<String> mobPool = CampRandomizer.getMobPoolForBiome(biome);
        Random random = new Random(world.getSeed() + pos.asLong());
        camp.setMobConfig(CampRandomizer.generateRandomMobConfig(random, mobPool));

        camps.put(name, camp);
        save();
        return camp;
    }

    /**
     * 删除据点
     */
    public void deleteCamp(String name) {
        Camp camp = camps.remove(name);
        if (camp != null) {
            // 清理残留怪物
            despawnCampMobs(camp);
            camp.removeBossBar();
            // 清理宝箱
            if (camp.getStatus() == Camp.Status.CLEARED) {
                CampRewardHandler.removeChest(world, camp);
            }
            save();
        }
    }

    /**
     * 获取所有据点
     */
    public List<Camp> getAllCamps() {
        return new ArrayList<>(camps.values());
    }

    /**
     * 按名称获取据点
     */
    public Camp getCamp(String name) {
        return camps.get(name);
    }

    /**
     * 当前管理的据点数
     */
    public int getCampCount() {
        return camps.size();
    }

    /**
     * 待创建的候选据点数
     */
    public int getPendingCampCount() {
        return pendingCamps.size();
    }

    /**
     * 清空所有据点
     */
    public void clearAll() {
        for (Camp camp : camps.values()) {
            despawnCampMobs(camp);
            camp.removeBossBar();
            if (camp.getStatus() == Camp.Status.CLEARED) {
                CampRewardHandler.removeChest(world, camp);
            }
        }
        camps.clear();
        save();
    }

    // ==================== 命令辅助 ====================

    /**
     * 重置据点（刷新怪物和宝箱）
     */
    public void resetCamp(String name) {
        Camp camp = camps.get(name);
        if (camp == null) return;

        // 清理旧怪物和宝箱
        despawnCampMobs(camp);
        if (camp.getStatus() == Camp.Status.CLEARED) {
            CampRewardHandler.removeChest(world, camp);
        }
        camp.removeBossBar();

        // 重置状态
        camp.setStatus(Camp.Status.IDLE);
        camp.setLastClearedTime(0);
        camp.setLastSpawnedTick(0);  // 重置冷却，允许立即重生
        camp.setActiveMobIds(new HashSet<>());
        camp.clearActiveEntities();

        // 重新生成怪物配置
        var biome = world.getBiome(camp.getBlockPos());
        List<String> mobPool = CampRandomizer.getMobPoolForBiome(biome);
        Random random = new Random(world.getSeed() + camp.getBlockPos().asLong());
        camp.setMobConfig(CampRandomizer.generateRandomMobConfig(random, mobPool));

        save();
    }

    /**
     * 刷新安全生成点缓存
     */
    public int refreshCache(String name) {
        Camp camp = camps.get(name);
        if (camp == null) return -1;

        List<BlockPos> safePoints = CampSpawnValidator.findSafeSpawnPoints(world, camp.getBlockPos(), 8);
        camp.setSafeSpawnPointsFromBlocks(safePoints);
        save();
        return safePoints.size();
    }

    /**
     * 检查据点安全状态
     */
    public CheckResult checkCamp(String name) {
        Camp camp = camps.get(name);
        if (camp == null) return null;

        BlockPos center = camp.getBlockPos();
        List<BlockPos> safePoints = CampSpawnValidator.findSafeSpawnPoints(world, center, 8);
        boolean centerSafe = CampSpawnValidator.isPositionSafe(world, center);
        boolean centerGroundSafe = CampSpawnValidator.isPositionSafe(world, new BlockPos(center.getX(), center.getY(), center.getZ()));

        return new CheckResult(
                camp.getName(),
                center,
                centerSafe,
                safePoints.size(),
                camp.getStatus(),
                camp.getTotalMobCount()
        );
    }

    /**
     * 直接添加一个据点（用于自动生成器）
     */
    public void addCamp(Camp camp) {
        if (camp == null || camp.getName() == null) return;
        if (camps.containsKey(camp.getName())) return;
        camps.put(camp.getName(), camp);
    }

    /**
     * 添加候选据点列表（用于调试）
     */
    public void addPendingCamps(List<BlockPos> candidates) {
        pendingCamps.addAll(candidates);
    }

    /**
     * 触发自动据点生成
     * <p>
     * 基于世界种子计算所有候选位置，之后区块加载时逐步完成创建。
     */
    public boolean triggerAutoGeneration() {
        if (seedGenerated) return false;

        long seed = world.getSeed();
        String flagKey = CampWorldGenerator.GENERATION_FLAG_PREFIX + seed;

        // 从数据包装器检查是否已生成
        if (hasGenerationFlag(flagKey)) {
            seedGenerated = true;
            ShadeMod.LOGGER.info("[shadecamp] 种子 {} 的据点已生成过，跳过自动生成", seed);
            return false;
        }

        ShadeMod.LOGGER.info("[shadecamp] 开始自动据点生成（种子: {}）...", seed);

        // 计算候选位置（纯种子计算，不加载区块）
        List<BlockPos> candidates = CampWorldGenerator.generateCandidates(world);
        pendingCamps.addAll(candidates);

        // 标记为已生成
        seedGenerated = true;
        setGenerationFlag(flagKey);
        save();

        ShadeMod.LOGGER.info("[shadecamp] 自动生成完成，{} 个候选待区块加载后创建", pendingCamps.size());
        return true;
    }

    /**
     * 快速检查种子是否已生成（无需反序列化完整数据）
     */
    private boolean hasGenerationFlag(String flagKey) {
        try {
            if (!Files.exists(saveFile)) return false;
            try (Reader reader = Files.newBufferedReader(saveFile)) {
                CampDataWrapper wrapper = GSON.fromJson(reader, CampDataWrapper.class);
                return wrapper != null && wrapper.generatedSeeds != null
                        && wrapper.generatedSeeds.contains(flagKey);
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void setGenerationFlag(String flagKey) {
        // 由 save() 方法在保存时统一写入
    }

    // ==================== 核心逻辑 ====================

    /**
     * 每 tick 调用一次，更新所有据点状态
     */
    public void tick() {
        tickCounter++;

        // 1. 处理待创建的候选据点（区块加载后自动完成）
        if (tickCounter % 10 == 0) { processPendingCamps(); }

        // 2. 更新已激活据点的状态
        long gameTime = world.getGameTime();
        List<Camp> processed = new ArrayList<>(camps.values());
        for (Camp camp : processed) {
            switch (camp.getStatus()) {
                case IDLE -> tickIdle(camp, gameTime);
                case FIGHTING -> tickFighting(camp, gameTime);
                case CLEARED -> tickCleared(camp, gameTime);
            }
        }

        // 每 5 秒自动保存一次（100 ticks）
        if (tickCounter % 200 == 0) {
            save();
        }
    }

    /**
     * 处理待创建的候选据点
     * <p>
     * 当候选据点所在区块加载后，执行完整的安全验证并创建据点。
     */
    private void processPendingCamps() {
        if (pendingCamps.isEmpty()) return;

        Iterator<BlockPos> it = pendingCamps.iterator();
        int created = 0;
        while (it.hasNext()) {
            BlockPos pending = it.next();
            if (!world.isLoaded(pending)) continue;

            Camp camp = CampWorldGenerator.finalizeCamp(world, pending, this);
            if (camp != null) {
                created++;
            }
            it.remove();
        }

        if (created > 0) {
            ShadeMod.LOGGER.debug("[shadecamp] 本轮自动创建 {} 个据点，怪物已预生成", created);
            save();
        }
    }

    /** 据点创建后立即预生成怪物（闲置状态，散布在各生成点） */
    private void spawnIdleMobs(Camp camp) {
        // 防止无限循环刷新（至少间隔 10 分钟=12000 tick）
        // 但首次生成（lastSpawnedTick <= 0）不受限制
        long lastSpawn = camp.getLastSpawnedTick();
        if (lastSpawn > 0 && world.getGameTime() - lastSpawn < 12000) return;
        camp.setLastSpawnedTick(world.getGameTime());

        BlockPos campPos = camp.getBlockPos();
        List<BlockPos> spawnPoints = camp.getSafeSpawnBlockPositions();
        Random random = new Random(world.getSeed() + campPos.asLong());

        int spawnedCount = 0;
        for (Map.Entry<String, Integer> entry : camp.getMobConfig().entrySet()) {
            String entityId = entry.getKey();
            int count = entry.getValue();
            var entityType = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(entityId));
            if (entityType == null) continue;

            for (int i = 0; i < count; i++) {
                if (spawnPoints.isEmpty()) break;
                BlockPos spawnPos = spawnPoints.get(random.nextInt(spawnPoints.size()));

                Entity entity = entityType.create(world);
                if (entity != null) {
                    entity.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                    if (entity instanceof Mob mob) {
                        mob.setPersistenceRequired();
                        mob.setAggressive(false);
                        mob.setTarget(null);
                    }
                    world.addFreshEntity(entity);
                    camp.addActiveEntity(entity);
                    spawnedCount++;
                }
            }
        }
        ShadeMod.LOGGER.info("[shadecamp] 据点 '{}' 预生成 {} 只怪物，散布在 {} 个位置",
                camp.getName(), spawnedCount, spawnPoints.size());
        save();
    }

    /** 玩家进入范围，激活闲置怪物 */
    private void aggroMobs(Camp camp) {
        ShadeMod.LOGGER.info("[shadecamp] 据点 '{}' 怪物被激活！", camp.getName());
        ServerPlayer nearest = findNearestPlayer(camp);

        for (Entity entity : camp.getActiveEntities()) {
            if (entity instanceof Mob mob && entity.isAlive()) {
                mob.setAggressive(true);
                        
                if (nearest != null) mob.setTarget(nearest);
            }
        }

        camp.setEnteredFightingTick(world.getGameTime());
        camp.setStatus(Camp.Status.FIGHTING);
        camp.getOrCreateBossBar();
        camp.syncBossBarPlayers(getPlayersInRange(camp), camp.getActiveMobIds().size());
    }

    /**
     * 判断候选位置是否在任意玩家的附近
     */
    private boolean isNearAnyPlayer(BlockPos pos, int radius) {
        for (ServerPlayer player : world.players()) {
            if (player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < radius * radius) {
                return true;
            }
        }
        return false;
    }

    /**
     * 处理空闲状态的据点
     */
    private void tickIdle(Camp camp, long gameTime) {
        // 如果设置了刷新时间，检查是否需要重生
        if (camp.getRefreshTime() > 0 && camp.getLastClearedTime() > 0) {
            long elapsed = gameTime - camp.getLastClearedTime();
            if (elapsed >= camp.getRefreshTime() * 20L) {
                ShadeMod.LOGGER.debug("[shadecamp] 据点 {} 刷新时间到，重置", camp.getName());
                resetCamp(camp.getName());
                return;
            }
        }

        // === 玩家靠近时生成/激活怪物 ===
        BlockPos campPos = camp.getBlockPos();
        double rangeSq = camp.getTriggerRange() * camp.getTriggerRange();

        for (ServerPlayer player : world.players()) {
            if (player.isSpectator()) continue;

            double distSq = player.distanceToSqr(campPos.getX() + 0.5, campPos.getY() + 0.5, campPos.getZ() + 0.5);
            if (distSq <= rangeSq) {
                ShadeMod.LOGGER.info(
                        "[shadecamp] 玩家 {} 进入据点 {} 范围 ({} < {})",
                        player.getName().getString(),
                        camp.getName(),
                        String.format("%.1f", Math.sqrt(distSq)),
                        camp.getTriggerRange()
                );
                try {
                    if (!camp.getActiveMobIds().isEmpty()) {
                        // 已有预生成的怪物 → 直接激活
                        aggroMobs(camp);
                    } else {
                        // 旧式据点 → 现场生成+激活
                        activateCamp(camp);
                    }
                } catch (Exception e) {
                    ShadeMod.LOGGER.error("[shadecamp] 激活据点 {} 失败", camp.getName(), e);
                }
                return;
            }
        }
    }

    /**
     * 处理战斗中的据点
     */
    private void tickFighting(Camp camp, long gameTime) {
        List<Entity> entities = camp.getActiveEntities();

        // 如果没有活跃怪物 → 战斗结束（避免回 IDLE 导致重新刷怪）
        if (entities.isEmpty()) {
            clearCamp(camp);
            return;
        }

        // 检查是否所有怪物都已死亡
        boolean allDead = true;
        Iterator<Entity> it = entities.iterator();
        while (it.hasNext()) {
            Entity entity = it.next();
            if (entity == null || !entity.isAlive()) {
                it.remove();
                camp.getActiveMobIds().remove(entity != null ? entity.getUUID() : null);
            } else {
                allDead = false;
                if (!isAnyPlayerInRange(camp)) {
                    if (entity instanceof Mob mob) {
                        mob.setTarget(null);
                        mob.setAggressive(false);
                    }
                }
            }
        }

        // 更新 BOSS 进度条（存活数/总数）
        int aliveCount = entities.size();
        List<ServerPlayer> playersInRange = getPlayersInRange(camp);
        camp.syncBossBarPlayers(playersInRange, aliveCount);

        // 全部击杀 → 清空（但至少等待 3 tick 防止误判）
        if (allDead && camp.getTotalMobCount() > 0
                && gameTime - camp.getEnteredFightingTick() >= 3) {
            clearCamp(camp);
        }
    }

    /**
     * 处理已清空的据点
     */
    private void tickCleared(Camp camp, long gameTime) {
        // 如果设置了刷新时间，检查是否需要重生
        if (camp.getRefreshTime() > 0 && camp.getLastClearedTime() > 0) {
            long elapsed = gameTime - camp.getLastClearedTime();
            if (elapsed >= camp.getRefreshTime() * 20L) {
                // 移除旧宝箱
                CampRewardHandler.removeChest(world, camp);
                // 重置
                resetCamp(camp.getName());
            }
        }
    }

    /**
     * 激活据点 - 生成怪物
     */
    private void activateCamp(Camp camp) {
        // 防无限刷新（至少间隔 10 分钟=12000 tick）
        // 但首次生成（lastSpawnedTick <= 0）不受限制
        long lastSpawn2 = camp.getLastSpawnedTick();
        if (lastSpawn2 > 0 && world.getGameTime() - lastSpawn2 < 12000) return;
        camp.setLastSpawnedTick(world.getGameTime());

        BlockPos campPos = camp.getBlockPos();
        List<BlockPos> spawnPoints = camp.getSafeSpawnBlockPositions();
        ShadeMod.LOGGER.debug("[shadecamp] 缓存安全点: {} 个", spawnPoints.size());

        // 如果缓存为空，尝试重新计算
        if (spawnPoints.isEmpty()) {
            ShadeMod.LOGGER.debug("[shadecamp] 缓存为空，重新计算安全点...");
            spawnPoints = CampSpawnValidator.findSafeSpawnPoints(world, campPos, 8);
            camp.setSafeSpawnPointsFromBlocks(spawnPoints);
            ShadeMod.LOGGER.debug("[shadecamp] 重新计算后安全点: {} 个", spawnPoints.size());
        }

        // 如果依然没有安全位置，拒绝激活
        if (spawnPoints.isEmpty()) {
            ShadeMod.LOGGER.warn("[shadecamp] 据点 {} 没有安全生成点，无法激活！", camp.getName());
            return;
        }

        Random random = new Random(world.getSeed() + campPos.asLong() + gameTime());

        int worldLevel = WorldLevel.getLevel(world);
        if (worldLevel > 0) {
            ShadeMod.LOGGER.info("[shadecamp] 世界等级 {}, 怪物强化", WorldLevel.getName(worldLevel));
        }

        for (Map.Entry<String, Integer> entry : camp.getMobConfig().entrySet()) {
            String entityId = entry.getKey();
            int count = entry.getValue();

            ShadeMod.LOGGER.debug("[shadecamp]   - 生成: {} × {}", entityId, count);

            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(entityId));
            if (entityType == null) {
                ShadeMod.LOGGER.warn("[shadecamp] 实体类型不存在: {}", entityId);
                continue;
            }

            for (int i = 0; i < count; i++) {
                BlockPos spawnPos = spawnPoints.get(random.nextInt(spawnPoints.size()));

                Entity entity = entityType.create(world);
                if (entity != null) {
                    entity.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

                    if (entity instanceof Mob mob) {
                        mob.setPersistenceRequired();
                        mob.setAggressive(true);
                        WorldLevel.applyScaling(mob, worldLevel);

                        // 锁定最近的玩家为目标
                        ServerPlayer nearest = findNearestPlayer(camp);
                        if (nearest != null) {
                            mob.setTarget(nearest);
                        }
                    }

                    world.addFreshEntity(entity);
                    camp.addActiveEntity(entity);
                    ShadeMod.LOGGER.debug("[shadecamp]     → {} @ [{}, {}, {}]",
                            entityId, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
                } else {
                    ShadeMod.LOGGER.warn("[shadecamp]     → 实体创建失败 (create 返回 null)");
                }
            }
        }

        ShadeMod.LOGGER.debug("[shadecamp] 共计生成 {} 只怪物", camp.getActiveMobIds().size());
        // 世界等级额外怪物
        if (worldLevel > 0) {
            var configTypes = camp.getMobConfig().keySet().toArray(new String[0]);
            for (int wl = 0; wl < worldLevel; wl++) {
                for (String eid : configTypes) {
                    var xt = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(eid));
                    if (xt == null) continue;
                    Entity xe = xt.create(world);
                    if (xe != null) {
                        BlockPos sp = spawnPoints.get(random.nextInt(spawnPoints.size()));
                        xe.setPos(sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5);
                        if (xe instanceof Mob xm) {
                            xm.setPersistenceRequired();
                            xm.setAggressive(true);
                            WorldLevel.applyScaling(xm, worldLevel);
                            ServerPlayer nearest = findNearestPlayer(camp);
                            if (nearest != null) xm.setTarget(nearest);
                        }
                        world.addFreshEntity(xe);
                        camp.addActiveEntity(xe);
                    }
                }
            }
        }


        // 播放激活效果
        CampRewardHandler.playActivationEffects(world, Vec3.atCenterOf(
                campPos.offset(
                        world.random.nextInt(7) - 3,
                        world.random.nextInt(3) - 1,
                        world.random.nextInt(7) - 3
                )
        ));

        camp.setEnteredFightingTick(world.getGameTime());
        camp.setStatus(Camp.Status.FIGHTING);

        // 创建 BOSS 进度条并同步给范围内的玩家
        camp.getOrCreateBossBar();
        List<ServerPlayer> playersInRange = getPlayersInRange(camp);
        ShadeMod.LOGGER.debug("[shadecamp] 范围内玩家: {} 人", playersInRange.size());
        camp.syncBossBarPlayers(playersInRange, camp.getActiveMobIds().size());

        save();
        ShadeMod.LOGGER.debug("[shadecamp] === 据点 {} 激活完成 ===", camp.getName());
    }

    /**
     * 清空据点 - 生成奖励宝箱
     */
    private void clearCamp(Camp camp) {
        // 防重复触发
        if (camp.getStatus() == Camp.Status.CLEARED) return;

        ShadeMod.LOGGER.info("据点 {} 已被清空！", camp.getName());

        camp.removeBossBar();
        camp.setStatus(Camp.Status.CLEARED);
        camp.setLastClearedTime(world.getGameTime());
        camp.clearActiveEntities();

        // 按怪物数量分等级生成宝箱
        CampRewardHandler.ChestTier tier = CampRewardHandler.ChestTier.forMobCount(camp.getTotalMobCount(), WorldLevel.getLevel(world));
        CampRewardHandler.spawnRewardChest(world, camp, tier);

        // 通知 Quest 系统：该营地已被占领
        for (ServerPlayer player : getPlayersInRange(camp)) {
            AdapterRegistry.notifyProgress(player, "OCCUPY_CAMP", camp.getName(), 1);
        }

        save();
    }

    // ==================== 辅助方法 ====================

    /**
     * 查找据点范围内最近的玩家
     */
    private ServerPlayer findNearestPlayer(Camp camp) {
        BlockPos campPos = camp.getBlockPos();
        double rangeSq = camp.getTriggerRange() * camp.getTriggerRange();
        ServerPlayer nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (ServerPlayer player : world.players()) {
            if (player.isSpectator()) continue;
            double distSq = player.distanceToSqr(campPos.getX() + 0.5, campPos.getY() + 0.5, campPos.getZ() + 0.5);
            if (distSq <= rangeSq && distSq < nearestDist) {
                nearest = player;
                nearestDist = distSq;
            }
        }
        return nearest;
    }

    /**
     * 判断是否有玩家在据点范围内
     */
    private boolean isAnyPlayerInRange(Camp camp) {
        BlockPos campPos = camp.getBlockPos();
        double rangeSq = camp.getTriggerRange() * camp.getTriggerRange();

        for (ServerPlayer player : world.players()) {
            if (player.isSpectator()) continue;
            double distSq = player.distanceToSqr(campPos.getX() + 0.5, campPos.getY() + 0.5, campPos.getZ() + 0.5);
            if (distSq <= rangeSq) return true;
        }
        return false;
    }

    /**
     * 移除据点所有已生成的怪物
     */
    private void despawnCampMobs(Camp camp) {
        for (Entity entity : camp.getActiveEntities()) {
            if (entity != null) {
                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        }
        camp.clearActiveEntities();
    }

    /**
     * 获取据点范围内的所有玩家
     */
    private List<ServerPlayer> getPlayersInRange(Camp camp) {
        BlockPos campPos = camp.getBlockPos();
        double rangeSq = camp.getTriggerRange() * camp.getTriggerRange();
        List<ServerPlayer> result = new ArrayList<>();
        for (ServerPlayer player : world.players()) {
            if (player.isSpectator()) continue;
            double distSq = player.distanceToSqr(
                    campPos.getX() + 0.5, campPos.getY() + 0.5, campPos.getZ() + 0.5);
            if (distSq <= rangeSq) {
                result.add(player);
            }
        }
        return result;
    }

    /**
     * 获取游戏时间（用于随机）
     */
    private long gameTime() {
        return world.getGameTime();
    }

    // ==================== 内部类 ====================

    /**
     * Gson 包装器
     */
    private static class CampDataWrapper {
        List<Camp> camps = new ArrayList<>();
        /** 已完成自动生成的种子列表 */
        List<String> generatedSeeds = new ArrayList<>();
    }

    /**
     * 检查结果
     */
    public record CheckResult(
            String name,
            BlockPos position,
            boolean centerSafe,
            int safeSpawnCount,
            Camp.Status status,
            int totalMobs
    ) {
        public boolean isSafe() {
            return centerSafe && safeSpawnCount > 0;
        }
    }
}
