package io.github.shade.story.aigen;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.shade.ShadeMod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Claude (Anthropic) AI 提供者 — 通过 Anthropic Messages API 调用 Claude
 *
 * Anthropic API 格式与 OpenAI 不同：
 * - 认证：Header x-api-key（而非 Bearer token）
 * - 版本头：anthropic-version
 * - System prompt 在顶层字段中（不在 messages 数组里）
 * - Messages content 为 content block 数组
 * - 响应 content[0].text（而非 choices[0].message.content）
 *
 * API 文档：https://docs.anthropic.com/en/api/messages
 */
public class ClaudeProvider implements AiProvider {

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String getName() {
        return "Claude";
    }

    @Override
    public CompletableFuture<String> generate(String systemPrompt, String userPrompt, AiConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String endpoint = config.getClaudeEndpoint();
                String apiKey = config.getClaudeApiKey();
                String model = config.getClaudeModel();
                double temp = config.getTemperature();
                int maxTokens = config.getMaxTokens();

                if (apiKey == null || apiKey.isEmpty()) {
                    throw new IllegalStateException("Claude API 密钥未配置");
                }

                // 构建 Anthropic Messages API 请求体
                JsonObject body = new JsonObject();
                body.addProperty("model", model);
                body.addProperty("max_tokens", maxTokens);
                body.addProperty("temperature", temp);

                // System prompt 在顶层字段（不是 messages 数组的一部分）
                if (systemPrompt != null && !systemPrompt.isEmpty()) {
                    JsonArray systemArray = new JsonArray();
                    JsonObject sysBlock = new JsonObject();
                    sysBlock.addProperty("type", "text");
                    sysBlock.addProperty("text", systemPrompt);
                    systemArray.add(sysBlock);
                    body.add("system", systemArray);
                }

                // Messages 数组（只包含 user/assistant 轮次）
                JsonArray messages = new JsonArray();
                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                JsonArray contentArray = new JsonArray();
                JsonObject textBlock = new JsonObject();
                textBlock.addProperty("type", "text");
                textBlock.addProperty("text", userPrompt);
                contentArray.add(textBlock);
                userMsg.add("content", contentArray);
                messages.add(userMsg);

                body.add("messages", messages);

                // 发送请求
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .timeout(Duration.ofSeconds(60))
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new RuntimeException("Claude API 返回错误 " + response.statusCode()
                            + ": " + response.body());
                }

                // 解析 Anthropic 响应
                JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                JsonArray content = json.getAsJsonArray("content");
                if (content == null || content.size() == 0) {
                    throw new RuntimeException("Claude API 返回空内容");
                }
                String result = content.get(0).getAsJsonObject().get("text").getAsString();

                ShadeMod.LOGGER.debug("[ai] Claude 返回 {} 字符", result.length());
                return result;

            } catch (Exception e) {
                ShadeMod.LOGGER.error("[ai] Claude 请求失败", e);
                throw new RuntimeException("AI 生成失败: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> testConnection(AiConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String apiKey = config.getClaudeApiKey();
                if (apiKey == null || apiKey.isEmpty()) return false;

                // 通过简单 messages 请求测试连接
                JsonObject body = new JsonObject();
                body.addProperty("model", config.getClaudeModel());
                body.addProperty("max_tokens", 1);

                JsonArray messages = new JsonArray();
                JsonObject msg = new JsonObject();
                msg.addProperty("role", "user");
                JsonArray content = new JsonArray();
                JsonObject text = new JsonObject();
                text.addProperty("type", "text");
                text.addProperty("text", "hi");
                content.add(text);
                msg.add("content", content);
                messages.add(msg);
                body.add("messages", messages);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(config.getClaudeEndpoint()))
                        .header("Content-Type", "application/json")
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request,
                        HttpResponse.BodyHandlers.ofString());
                return response.statusCode() == 200;

            } catch (Exception e) {
                ShadeMod.LOGGER.error("[ai] Claude 连接测试失败", e);
                return false;
            }
        });
    }
}
