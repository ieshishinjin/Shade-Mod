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
 * OpenAI 兼容 API 提供者 — 支持任意 OpenAI 兼容端点
 *
 * 用于自定义 AI 提供商和推荐服务商（智谱、讯飞星火、Groq 等）。
 * 所有 OpenAI 兼容格式的 API 都能通过此提供者调用。
 *
 * API 格式：POST /chat/completions
 * 请求：{ model, messages: [{role, content}], temperature, max_tokens }
 * 响应：{ choices: [{ message: { content: "..." } }] }
 */
public class OpenAiCompatibleProvider implements AiProvider {

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String getName() {
        return "Custom";
    }

    @Override
    public CompletableFuture<String> generate(String systemPrompt, String userPrompt, AiConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String endpoint = config.getCustomEndpoint();
                String apiKey = config.getCustomApiKey();
                String model = config.getCustomModel();
                double temp = config.getTemperature();
                int maxTokens = config.getMaxTokens();

                if (endpoint == null || endpoint.isEmpty()) {
                    throw new IllegalStateException("API 端点未配置");
                }
                if (apiKey == null || apiKey.isEmpty()) {
                    throw new IllegalStateException("API 密钥未配置");
                }

                // 构建 OpenAI 兼容请求体
                JsonObject body = new JsonObject();
                body.addProperty("model", model);
                body.addProperty("temperature", temp);
                body.addProperty("max_tokens", maxTokens);

                JsonArray messages = new JsonArray();

                JsonObject systemMsg = new JsonObject();
                systemMsg.addProperty("role", "system");
                systemMsg.addProperty("content", systemPrompt);
                messages.add(systemMsg);

                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", userPrompt);
                messages.add(userMsg);

                body.add("messages", messages);

                // 发送请求
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .timeout(Duration.ofSeconds(60))
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new RuntimeException("API 返回错误 " + response.statusCode()
                            + ": " + response.body());
                }

                // 解析 OpenAI 兼容响应
                JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                String content = json.getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .get("message").getAsJsonObject()
                        .get("content").getAsString();

                ShadeMod.LOGGER.debug("[ai] {} 返回 {} 字符", getName(), content.length());
                return content;

            } catch (Exception e) {
                ShadeMod.LOGGER.error("[ai] {} 请求失败", getName(), e);
                throw new RuntimeException("AI 生成失败: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> testConnection(AiConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String apiKey = config.getCustomApiKey();
                String endpoint = config.getCustomEndpoint();
                if (apiKey == null || apiKey.isEmpty()) return false;
                if (endpoint == null || endpoint.isEmpty()) return false;

                // 尝试通过聊天 API 发送简单请求来测试连接
                JsonObject body = new JsonObject();
                body.addProperty("model", config.getCustomModel());
                body.addProperty("max_tokens", 1);

                JsonArray messages = new JsonArray();
                JsonObject msg = new JsonObject();
                msg.addProperty("role", "user");
                msg.addProperty("content", "hi");
                messages.add(msg);
                body.add("messages", messages);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request,
                        HttpResponse.BodyHandlers.ofString());
                return response.statusCode() == 200;

            } catch (Exception e) {
                ShadeMod.LOGGER.error("[ai] {} 连接测试失败", getName(), e);
                return false;
            }
        });
    }
}
