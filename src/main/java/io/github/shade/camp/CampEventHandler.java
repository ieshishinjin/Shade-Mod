package io.github.shade.camp;

import io.github.shade.ShadeMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * 据点事件监听器
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

                List<net.minecraft.core.BlockPos> candidates = CampWorldGenerator.generateCandidates(level);
                campManager.addPendingCamps(candidates);
                ShadeMod.LOGGER.info("[shadecamp] 已添加 {} 个候选，将在区块加载后逐步定稿", candidates.size());
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

                // 第一次定稿时输出
                if (before == 0 && after > 0) {
                    ShadeMod.LOGGER.info("[shadecamp] 首批 {} 个据点已自动生成！请使用 /camp list 查看", after);
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
