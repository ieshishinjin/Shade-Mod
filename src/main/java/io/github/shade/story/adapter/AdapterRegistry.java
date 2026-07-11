package io.github.shade.story.adapter;

import io.github.shade.ShadeMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * 适配器注册表 — 管理所有已注册的游戏系统适配器
 *
 * 职责：
 * 1. 注册/注销系统适配器
 * 2. 根据 Objective 类型查找能处理的适配器
 * 3. 提供适配器事件总线（游戏事件 → Quest 进度更新）
 * 4. 收集所有系统状态摘要（供 AI 生成器使用）
 */
public class AdapterRegistry {

    private static final Map<String, SystemAdapter> ADAPTERS = new ConcurrentHashMap<>();

    /** 进度更新监听器：(player, ObjectiveProgressEvent) */
    private static final List<BiConsumer<ServerPlayer, ObjectiveProgressEvent>> PROGRESS_LISTENERS =
            Collections.synchronizedList(new ArrayList<>());

    private AdapterRegistry() {}

    static {
        // 注册默认的被动适配器（不依赖外部系统的检测）
        register(new PassiveAdapter());
    }

    // ==================== 注册/注销 ====================

    /**
     * 注册一个系统适配器
     */
    public static void register(SystemAdapter adapter) {
        ADAPTERS.put(adapter.getSystemId(), adapter);
        ShadeMod.LOGGER.info("[adapter] 已注册适配器: {} (类型: {})",
                adapter.getSystemId(),
                String.join(", ", adapter.getSupportedObjectiveTypes()));
    }

    /**
     * 注销一个系统适配器
     */
    public static void unregister(String systemId) {
        ADAPTERS.remove(systemId);
        ShadeMod.LOGGER.info("[adapter] 已注销适配器: {}", systemId);
    }

    /**
     * 获取指定系统适配器
     */
    public static SystemAdapter getAdapter(String systemId) {
        return ADAPTERS.get(systemId);
    }

    /**
     * 获取所有已注册的适配器
     */
    public static Collection<SystemAdapter> getAllAdapters() {
        return ADAPTERS.values();
    }

    // ==================== 进度查询 ====================

    /**
     * 根据 Objective 类型查找能处理它的适配器，并获取进度
     *
     * @return 当前进度值（未找到适配器时返回 0）
     */
    public static int getProgress(ServerPlayer player, String objectiveType, String targetId) {
        for (SystemAdapter adapter : ADAPTERS.values()) {
            if (adapter.getSupportedObjectiveTypes().contains(objectiveType)) {
                return adapter.getProgress(player, objectiveType, targetId);
            }
        }
        return 0;
    }

    /**
     * 获取 Objective 的最大进度值
     */
    public static int getMaxProgress(String objectiveType, String targetId) {
        for (SystemAdapter adapter : ADAPTERS.values()) {
            if (adapter.getSupportedObjectiveTypes().contains(objectiveType)) {
                return adapter.getMaxProgress(objectiveType, targetId);
            }
        }
        return 1;
    }

    /**
     * 检查条件（遍历所有适配器，找到能处理该条件类型的）
     */
    public static boolean checkCondition(ServerPlayer player, String condition, String targetId, int value, String operator) {
        for (SystemAdapter adapter : ADAPTERS.values()) {
            if (adapter.checkCondition(player, condition, targetId, value, operator)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 执行操作（遍历所有适配器，找到能处理的）
     */
    public static boolean executeAction(ServerLevel world, String action, String targetId, Map<String, Object> params) {
        for (SystemAdapter adapter : ADAPTERS.values()) {
            if (adapter.executeAction(world, action, targetId, params)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取所有系统的状态摘要
     */
    public static String getAllStatusSummaries(ServerLevel world) {
        StringBuilder sb = new StringBuilder();
        for (SystemAdapter adapter : ADAPTERS.values()) {
            try {
                String summary = adapter.getStatusSummary(world);
                if (summary != null && !summary.isEmpty()) {
                    sb.append("[").append(adapter.getSystemId()).append("] ")
                            .append(summary).append("\n");
                }
            } catch (Exception e) {
                sb.append("[").append(adapter.getSystemId()).append("] (unavailable)\n");
            }
        }
        return sb.toString();
    }

    // ==================== 事件总线 ====================

    /**
     * 注册进度更新监听器（供 QuestManager 使用）
     */
    public static void registerProgressListener(BiConsumer<ServerPlayer, ObjectiveProgressEvent> listener) {
        PROGRESS_LISTENERS.add(listener);
    }

    /**
     * 通知进度更新（由适配器或游戏事件触发）
     */
    public static void fireProgressEvent(ServerPlayer player, ObjectiveProgressEvent event) {
        synchronized (PROGRESS_LISTENERS) {
            for (BiConsumer<ServerPlayer, ObjectiveProgressEvent> listener : PROGRESS_LISTENERS) {
                try {
                    listener.accept(player, event);
                } catch (Exception e) {
                    ShadeMod.LOGGER.error("[adapter] 进度监听器异常", e);
                }
            }
        }
    }

    /**
     * 触发 Objective 进度更新（简化版，由游戏事件调用）
     */
    public static void notifyProgress(ServerPlayer player, String objectiveType, String targetId, int delta) {
        fireProgressEvent(player, new ObjectiveProgressEvent(objectiveType, targetId, delta));
    }

    // ==================== 内部类 ====================

    /**
     * Objective 进度变更事件
     */
    public record ObjectiveProgressEvent(
            String objectiveType,
            String targetId,
            int delta
    ) {}

    /**
     * 被动适配器 — 处理不需要适配器主动检测的 Objective 类型
     * 这些类型的进度完全由外部事件驱动，适配器只做进度查询。
     */
    private static class PassiveAdapter implements SystemAdapter {
        @Override
        public String getSystemId() { return "builtin"; }

        @Override
        public List<String> getSupportedObjectiveTypes() {
            return List.of("CUSTOM", "REACH_LOCATION", "REACH_LEVEL");
        }

        @Override
        public int getProgress(ServerPlayer player, String objectiveType, String targetId) {
            return switch (objectiveType) {
                case "REACH_LEVEL" -> player.experienceLevel;
                case "REACH_LOCATION" -> 0; // 由事件驱动
                default -> 0;
            };
        }

        @Override
        public int getMaxProgress(String objectiveType, String targetId) {
            return switch (objectiveType) {
                case "REACH_LEVEL" -> Integer.MAX_VALUE;
                case "REACH_LOCATION" -> 1;
                default -> 1;
            };
        }

        @Override
        public boolean checkCondition(ServerPlayer player, String condition, String targetId, int value, String operator) {
            return false; // 不做条件检查
        }

        @Override
        public boolean executeAction(ServerLevel world, String action, String targetId, Map<String, Object> params) {
            return false;
        }

        @Override
        public String getStatusSummary(ServerLevel world) {
            return "Built-in passive adapter active";
        }
    }
}
