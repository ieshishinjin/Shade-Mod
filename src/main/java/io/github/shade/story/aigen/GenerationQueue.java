package io.github.shade.story.aigen;

import io.github.shade.ShadeMod;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 生成队列 — 异步管理生成请求，避免并发调用
 *
 * 功能：
 * 1. 每个玩家只能有一个进行中的生成请求
 * 2. 请求排队等待，防止同时调用 AI API
 * 3. 节流控制，防止过于频繁调用
 */
public class GenerationQueue {

    private static GenerationQueue INSTANCE;

    /** 进行中的生成请求 */
    private final Map<UUID, CompletableFuture<?>> pendingRequests = new ConcurrentHashMap<>();

    /** 请求队列（FIFO） */
    private final Map<UUID, QueuedRequest> queue = new LinkedHashMap<>();

    /** 当前是否正在处理队列 */
    private boolean processing = false;

    /** 两次生成之间的最小间隔（毫秒） */
    private static final long MIN_INTERVAL_MS = 30000; // 30秒

    private GenerationQueue() {}

    public static GenerationQueue getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new GenerationQueue();
        }
        return INSTANCE;
    }

    // ==================== 队列管理 ====================

    /**
     * 提交一个生成请求
     *
     * @param player  目标玩家
     * @param task    实际的生成任务（CompletableFuture）
     * @param reason  触发原因（用于日志）
     * @return true 如果请求被接受，false 如果已被节流
     */
    public boolean submit(ServerPlayer player, CompletableFuture<?> task, String reason) {
        UUID uuid = player.getUUID();

        // 检查是否已有进行中的请求
        if (pendingRequests.containsKey(uuid)) {
            ShadeMod.LOGGER.debug("[ai-queue] 玩家 {} 已有进行中的请求，跳过", player.getName().getString());
            return false;
        }

        // 检查最小间隔
        long now = System.currentTimeMillis();

        // 加入队列
        queue.put(uuid, new QueuedRequest(task, reason, now));
        ShadeMod.LOGGER.debug("[ai-queue] 玩家 {} 加入生成队列: {}",
                player.getName().getString(), reason, queue.size());

        processQueue();
        return true;
    }

    /**
     * 处理队列
     */
    private synchronized void processQueue() {
        if (processing || queue.isEmpty()) return;
        processing = true;

        Map.Entry<UUID, QueuedRequest> entry = queue.entrySet().iterator().next();
        UUID uuid = entry.getKey();
        QueuedRequest request = entry.getValue();
        queue.remove(uuid);

        // 标记为进行中
        pendingRequests.put(uuid, request.task);

        request.task.thenRun(() -> {
            pendingRequests.remove(uuid);
            processing = false;
            // 继续处理下一个请求
            processQueue();
        }).exceptionally(e -> {
            ShadeMod.LOGGER.error("[ai-queue] 生成失败: {}", e.getMessage());
            pendingRequests.remove(uuid);
            processing = false;
            processQueue();
            return null;
        });
    }

    /**
     * 是否有进行中的请求
     */
    public boolean hasPending(UUID uuid) {
        return pendingRequests.containsKey(uuid);
    }

    /**
     * 队列是否为空
     */
    public boolean isEmpty() {
        return queue.isEmpty() && pendingRequests.isEmpty();
    }

    /**
     * 清理
     */
    public static void cleanup() {
        if (INSTANCE != null) {
            INSTANCE.queue.clear();
            INSTANCE.pendingRequests.clear();
        }
        INSTANCE = null;
    }

    // ==================== 内部类 ====================

    private record QueuedRequest(CompletableFuture<?> task, String reason, long submittedAt) {}
}
