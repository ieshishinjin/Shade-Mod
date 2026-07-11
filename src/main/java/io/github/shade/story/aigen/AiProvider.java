package io.github.shade.story.aigen;

import java.util.concurrent.CompletableFuture;

/**
 * AI 提供者接口 — 抽象不同的 AI 后端
 *
 * 实现类：
 *   DeepSeekProvider — 通过 HTTP 调用 DeepSeek API
 *   OllamaProvider   — 通过 HTTP 调用本地 Ollama 实例
 */
public interface AiProvider {

    /**
     * 发送聊天请求到 AI，返回生成的文本
     *
     * @param systemPrompt  系统提示词（设定角色、约束）
     * @param userPrompt    用户提示词（具体任务）
     * @param config        生成配置
     * @return 异步返回 AI 生成的文本
     */
    CompletableFuture<String> generate(String systemPrompt, String userPrompt, AiConfig config);

    /**
     * 测试连接是否正常
     */
    CompletableFuture<Boolean> testConnection(AiConfig config);

    /**
     * 获取提供者名称
     */
    String getName();
}
