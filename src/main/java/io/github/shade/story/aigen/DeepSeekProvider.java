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
 * DeepSeek API 提供者 — 通过 OpenAI 兼容接口调用 DeepSeek
 *
 * 使用 Java 21 内置的 HttpClient，无需额外依赖。
 * API 文档：https://api-docs.deepseek.com/
 */
public class DeepSeekProvider implements AiProvider {

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String getName() {
        return "DeepSeek";
    }

    @Override
    public CompletableFuture<String> generate(String systemPrompt, String userPrompt, AiConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String endpoint = config.getDeepseekEndpoint();
                String apiKey = config.getApiKey();
                String model = config.getDeepseekModel();
                double temp = config.getTemperature();
                int maxTokens = config.getMaxTokens();

                if (apiKey == null || apiKey.isEmpty()) {
                    throw new IllegalStateException("DeepSeek API 密钥未配置");
                }

                // 构建请求体
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
                    throw new RuntimeException("DeepSeek API 返回错误 " + response.statusCode()
                            + ": " + response.body());
                }

                // 解析响应
                JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                String content = json.getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .get("message").getAsJsonObject()
                        .get("content").getAsString();

                ShadeMod.LOGGER.debug("[ai] DeepSeek 返回 {} 字符", content.length());
                return content;

            } catch (Exception e) {
                ShadeMod.LOGGER.error("[ai] DeepSeek 请求失败", e);
                throw new RuntimeException("AI 生成失败: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> testConnection(AiConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String apiKey = config.getApiKey();
                if (apiKey == null || apiKey.isEmpty()) return false;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(config.getDeepseekEndpoint()
                                .replace("/chat/completions", "/models")))
                        .header("Authorization", "Bearer " + apiKey)
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request,
                        HttpResponse.BodyHandlers.ofString());
                return response.statusCode() == 200;

            } catch (Exception e) {
                ShadeMod.LOGGER.error("[ai] DeepSeek 连接测试失败", e);
                return false;
            }
        });
    }
}
