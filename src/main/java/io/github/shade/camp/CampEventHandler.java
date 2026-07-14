package io.github.shade.camp;

import io.github.shade.ShadeMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Camp 生命周期事件入口 */
public class CampEventHandler {

    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;

        ShadeMod.LOGGER.debug("[shadecamp] ===== 注册事件监听器 =====");
        ServerLifecycleEvents.SERVER_STARTED.register(CampEventHandler::onServerStarted);
        ServerTickEvents.END_SERVER_TICK.register(CampEventHandler::onServerTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(CampEventHandler::onServerStopping);
    }

    private static void onServerStarted(MinecraftServer server) {
        ShadeMod.LOGGER.debug("[shadecamp] ===== 关闭原版刷怪，启用据点系统 =====");

        for (ServerLevel level : server.getAllLevels()) {
            try {
                if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) continue;

                // 关闭原版自然刷怪
                level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false, server);
                ShadeMod.LOGGER.debug("[shadecamp] doMobSpawning=false (原版刷怪已禁用)");

                CampManager campManager = CampManager.getInstance(level);
                ShadeMod.LOGGER.debug("[shadecamp] 种子={}", level.getSeed());

                // 生成据点候选
                List<BlockPos> candidates = new ArrayList<>();
                Random r = new Random(level.getSeed() ^ 0xCAFE_BABE_DEAD_BEEFL);

                int[] inner = {100, 130, 160, 190, 220, 250, 300, 350, 400};
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
                ShadeMod.LOGGER.debug("[shadecamp] 已添加 {} 个据点候选", candidates.size());
            } catch (Exception e) {
                ShadeMod.LOGGER.error("[shadecamp] 初始化异常", e);
            }
        }
    }

    private static void onServerTick(MinecraftServer server) {
        try {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) continue;

                // 每 5 秒强制确保原版刷怪关闭
                if (level.getGameTime() % 100 == 0) {
                    var rule = level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING);
                    if (rule.get()) rule.set(false, server);
                }

                // 每秒更新 TAB 头部显示世界等级
                if (level.getGameTime() % 20 == 0) {
                    io.github.shade.worldlevel.WorldLevel.updateTabHeader(server, level);
                }

                CampManager campManager = CampManager.getInstance(level);
                int before = campManager.getCampCount();
                campManager.tick();
                int after = campManager.getCampCount();
                if (before == 0 && after > 0) {
                    ShadeMod.LOGGER.debug("[shadecamp] 首批 {} 个据点已创建", after);
                }
            }
        } catch (Exception e) {
            ShadeMod.LOGGER.error("[shadecamp] tick 异常", e);
        }
    }

    private static void onServerStopping(MinecraftServer server) {
        ShadeMod.LOGGER.info("[shadecamp] 保存数据...");
        CampManager.cleanupAll();
    }
}
