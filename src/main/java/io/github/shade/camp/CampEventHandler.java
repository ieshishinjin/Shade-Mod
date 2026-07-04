package io.github.shade.camp;

import io.github.shade.ShadeMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * 据点事件监听器
 * <p>
 * 通过 Fabric API 事件系统监听服务器生命周期和 Tick 事件，
 * 驱动据点系统的状态更新。
 * 不依赖任何 Mixin。
 */
public class CampEventHandler {

    private static boolean registered = false;
    private static boolean tickWarningLogged = false;

    /**
     * 注册所有事件监听器
     * 应在 ModInitializer#onInitialize() 中调用
     */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        ShadeMod.LOGGER.info("[shadecamp] 注册事件监听器...");

        // 1. 服务器 Tick 结束事件 — 驱动据点主循环
        ServerTickEvents.END_SERVER_TICK.register(CampEventHandler::onServerTick);

        // 2. 服务器停止事件 — 保存数据
        ServerLifecycleEvents.SERVER_STOPPING.register(CampEventHandler::onServerStopping);

        // 3. 世界保存事件 — 确保数据落盘
        ServerLifecycleEvents.AFTER_SAVE.register((server, flush, force) -> {
            for (ServerLevel level : server.getAllLevels()) {
                CampManager mgr = CampManager.INSTANCES.get(level);
                if (mgr != null) {
                    mgr.save();
                }
            }
        });

        ShadeMod.LOGGER.info("[shadecamp] 事件监听器注册完成");
    }

    /**
     * 服务器每 Tick 调用
     */
    private static void onServerTick(MinecraftServer server) {
        int campCount = 0;
        for (ServerLevel level : server.getAllLevels()) {
            try {
                CampManager campManager = CampManager.getInstance(level);
                campManager.tick();
                campCount += campManager.getCampCount();
            } catch (Exception e) {
                ShadeMod.LOGGER.error("[shadecamp] tick 处理异常", e);
            }
        }

        // 首次运行时输出调试信息
        if (!tickWarningLogged && campCount > 0) {
            ShadeMod.LOGGER.info("[shadecamp] 据点系统运行中，当前 {} 个据点", campCount);
            tickWarningLogged = true;
        }

        // 每 5 秒重置一次让日志能持续输出（如果据点数变化的话）
        if (tickWarningLogged && campCount == 0) {
            tickWarningLogged = false;
        }
    }

    /**
     * 服务器停止时保存所有数据
     */
    private static void onServerStopping(MinecraftServer server) {
        ShadeMod.LOGGER.info("[shadecamp] 服务器关闭，保存据点数据...");
        CampManager.cleanupAll();
    }
}
