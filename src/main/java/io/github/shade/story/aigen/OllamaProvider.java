package io.github.shade.story.aigen;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.shade.ShadeMod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Ollama 本地提供者 — 调用本地 Ollama 实例
 *
 * Ollama API 兼容 OpenAI 格式，支持 llama3、mistral、
 * qwen 等开源模型。
 */
public class OllamaProvider implements AiProvider {

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public String getName() {
        return "Ollama";
    }

    @Override
    public CompletableFuture<String> generate(String systemPrompt, String userPrompt, AiConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String endpoint = config.getOllamaEndpoint();
                String model = config.getOllamaModel();
                double temp = config.getTemperature();
                int maxTokens = config.getMaxTokens();

                // Ollama 使用不同的请求格式
                JsonObject body = new JsonObject();
                body.addProperty("model", model);
                body.addProperty("stream", false);
                body.addProperty("temperature", temp);
                body.addProperty("max_tokens", maxTokens);

                // Ollama 的 prompt 格式：系统提示 + 用户提示
                String fullPrompt = "【系统指令】\n" + systemPrompt + "\n\n【用户请求】\n" + userPrompt;
                body.addProperty("prompt", fullPrompt);

                // Ollama 的 options
                JsonObject options = new JsonObject();
                options.addProperty("temperature", temp);
                options.addProperty("num_predict", maxTokens);
                body.add("options", options);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(120))
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new RuntimeException("Ollama 返回错误 " + response.statusCode()
                            + ": " + response.body());
                }

                // 解析 Ollama 响应
                JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                String content = json.get("response").getAsString();

                ShadeMod.LOGGER.debug("[ai] Ollama 返回 {} 字符", content.length());
                return content;

            } catch (Exception e) {
                ShadeMod.LOGGER.error("[ai] Ollama 请求失败", e);
                throw new RuntimeException("AI 生成失败: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> testConnection(AiConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(config.getOllamaEndpoint()
                                .replace("/api/chat", "/api/tags")))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request,
                        HttpResponse.BodyHandlers.ofString());
                return response.statusCode() == 200;

            } catch (Exception e) {
                return false;
            }
        });
    }
}
