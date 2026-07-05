package io.github.shade.camp;

import io.github.shade.ShadeMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;

import java.util.ArrayList;
import java.util.List;

/**
 * 据点事件监听器
 * <p>
 * 替换原版怪物刷新机制，整个世界只通过据点系统生成敌对怪物。
 * 所有非据点追踪的原版自然生成的敌对怪物都会被移除。
 */
public class CampEventHandler {

    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;

        ShadeMod.LOGGER.info("[shadecamp] ===== 注册事件监听器 =====");
        ServerLifecycleEvents.SERVER_STARTED.register(CampEventHandler::onServerStarted);
        ServerTickEvents.END_SERVER_TICK.register(CampEventHandler::onServerTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(CampEventHandler::onServerStopping);
    }

    private static void onServerStarted(MinecraftServer server) {
        ShadeMod.LOGGER.info("[shadecamp] ===== SERVER_STARTED 触发 =====");

        for (ServerLevel level : server.getAllLevels()) {
            try {
                if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) continue;

                CampManager campManager = CampManager.getInstance(level);
                ShadeMod.LOGGER.info("[shadecamp] 种子={}", level.getSeed());

                // 生成更密集的据点覆盖整个世界
                List<BlockPos> candidates = new ArrayList<>();

                // 内圈（100~400格）：每个都有
                int[] inner = {100, 130, 160, 190, 220, 250, 300, 350, 400};
                java.util.Random r = new java.util.Random(level.getSeed() ^ 0xCAFE_BABE_DEAD_BEEFL);
                for (int dist : inner) {
                    for (int q = 0; q < 4; q++) {
                        if (r.nextDouble() > 0.55) continue;
                        double angle = q * (Math.PI / 2) + (r.nextDouble() - 0.5) * (Math.PI / 3);
                        int x = (int) Math.round(Math.cos(angle) * dist) + r.nextInt(21) - 10;
                        int z = (int) Math.round(Math.sin(angle) * dist) + r.nextInt(21) - 10;
                        if (x * x + z * z < 80 * 80) continue;
                        candidates.add(new BlockPos(x, 64, z));
                    }
                }

                // 中圈（500~1200格）：散布更广
                int[] mid = {500, 600, 700, 800, 900, 1000, 1100, 1200};
                for (int dist : mid) {
                    for (int q = 0; q < 6; q++) {
                        if (r.nextDouble() > 0.40) continue;
                        double angle = q * (Math.PI / 3) + (r.nextDouble() - 0.5) * (Math.PI / 4);
                        int x = (int) Math.round(Math.cos(angle) * dist) + r.nextInt(41) - 20;
                        int z = (int) Math.round(Math.sin(angle) * dist) + r.nextInt(41) - 20;
                        candidates.add(new BlockPos(x, 64, z));
                    }
                }

                // 远圈（1400~2500格）：分布探路
                int[] far = {1400, 1600, 1800, 2000, 2200, 2500};
                for (int dist : far) {
                    for (int q = 0; q < 8; q++) {
                        if (r.nextDouble() > 0.30) continue;
                        double angle = q * (Math.PI / 4) + (r.nextDouble() - 0.5) * (Math.PI / 5);
                        int x = (int) Math.round(Math.cos(angle) * dist) + r.nextInt(61) - 30;
                        int z = (int) Math.round(Math.sin(angle) * dist) + r.nextInt(61) - 30;
                        candidates.add(new BlockPos(x, 64, z));
                    }
                }

                campManager.addPendingCamps(candidates);
                ShadeMod.LOGGER.info("[shadecamp] 已添加 {} 个据点候选覆盖世界", candidates.size());
            } catch (Exception e) {
                ShadeMod.LOGGER.error("[shadecamp] 自动生成异常", e);
            }
        }
    }

    private static void onServerTick(MinecraftServer server) {
        try {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) continue;

                CampManager campManager = CampManager.getInstance(level);
                int before = campManager.getCampCount();
                campManager.tick();
                int after = campManager.getCampCount();

                if (before == 0 && after > 0) {
                    ShadeMod.LOGGER.info("[shadecamp] 首批 {} 个据点已生成", after);
                }

                // 每 10 tick 清理一次自然生成的敌对怪物
                if (level.getGameTime() % 10 == 0) {
                    purgeNaturalMobs(level, campManager);
                }
            }
        } catch (Exception e) {
            ShadeMod.LOGGER.error("[shadecamp] tick 异常", e);
        }
    }

    /**
     * 移除所有非据点追踪的敌对怪物
     * <p>
     * 原版 Minecraft 每晚会在暗处自然生成僵尸、骷髅等。
     * 此方法确保世界上只存在据点系统生成的怪物。
     */
    private static void purgeNaturalMobs(ServerLevel level, CampManager campManager) {
        int removed = 0;

        for (Entity entity : level.getAllEntities()) {
            // 跳过非敌对生物（动物、村民等）
            if (!(entity instanceof Enemy)) continue;

            // 跳过 Boss（末影龙、凋灵）
            EntityType<?> type = entity.getType();
            if (type == EntityType.ENDER_DRAGON || type == EntityType.WITHER) continue;

            // 跳过据点追踪的怪物
            if (campManager.isCampMob(entity.getUUID())) continue;

            // 移除自然生成的敌对怪物
            entity.remove(Entity.RemovalReason.DISCARDED);
            removed++;
        }

        // 偶尔输出清理统计
        if (removed > 0 && level.getGameTime() % 200 == 0) {
            ShadeMod.LOGGER.debug("[shadecamp] 清理 {} 只自然生成敌对怪物", removed);
        }
    }

    private static void onServerStopping(MinecraftServer server) {
        ShadeMod.LOGGER.info("[shadecamp] 保存数据...");
        CampManager.cleanupAll();
    }
}
