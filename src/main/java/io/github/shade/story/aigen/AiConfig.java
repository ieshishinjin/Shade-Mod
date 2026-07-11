package io.github.shade.story.aigen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.shade.ShadeMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * AI 生成器配置 — 持久化到世界存档目录
 *
 * 保存位置：<world>/data/shade/story/ai_config.json
 */
public class AiConfig {

    private static AiConfig INSTANCE;
    private static Path configPath;

    /** AI 提供者类型：deepseek / ollama / none */
    private String provider = "none";

    /** DeepSeek API 密钥 */
    private String apiKey = "";

    /** DeepSeek API 端点 */
    private String deepseekEndpoint = "https://api.deepseek.com/v1/chat/completions";

    /** DeepSeek 模型名 */
    private String deepseekModel = "deepseek-chat";

    /** Ollama 端点 */
    private String ollamaEndpoint = "http://localhost:11434/api/chat";

    /** Ollama 模型名 */
    private String ollamaModel = "llama3";

    /** 温度参数 (0.0~2.0) */
    private double temperature = 0.8;

    /** 最大生成长度 */
    private int maxTokens = 1024;

    /** 是否启用 AI 生成 */
    private boolean enabled = false;

    /** 是否在故事中遇到 AI_GENERATE 节点时自动触发 */
    private boolean autoGenerate = false;

    private AiConfig() {}

    // ==================== 单例 ====================

    public static AiConfig getInstance(ServerLevel world) {
        if (INSTANCE == null || configPath == null) {
            configPath = world.getServer()
                    .getWorldPath(LevelResource.ROOT)
                    .resolve("data/shade/story/ai_config.json");
            INSTANCE = loadFromDisk();
        }
        return INSTANCE;
    }

    public static void cleanup() {
        if (INSTANCE != null) INSTANCE.save();
        INSTANCE = null;
    }

    // ==================== 持久化 ====================

    private static AiConfig loadFromDisk() {
        if (configPath == null) return new AiConfig();
        if (!Files.exists(configPath)) {
            AiConfig config = new AiConfig();
            config.save();
            return config;
        }
        try (Reader reader = Files.newBufferedReader(configPath)) {
            AiConfig config = new Gson().fromJson(reader, AiConfig.class);
            if (config == null) config = new AiConfig();
            return config;
        } catch (IOException e) {
            ShadeMod.LOGGER.error("[ai] 加载配置失败", e);
            return new AiConfig();
        }
    }

    public void save() {
        if (configPath == null) return;
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(this, writer);
            }
        } catch (IOException e) {
            ShadeMod.LOGGER.error("[ai] 保存配置失败", e);
        }
    }

    // ==================== Getters & Setters ====================

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; save(); }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; save(); }

    public String getDeepseekEndpoint() { return deepseekEndpoint; }
    public void setDeepseekEndpoint(String endpoint) { this.deepseekEndpoint = endpoint; save(); }

    public String getDeepseekModel() { return deepseekModel; }
    public void setDeepseekModel(String model) { this.deepseekModel = model; save(); }

    public String getOllamaEndpoint() { return ollamaEndpoint; }
    public void setOllamaEndpoint(String endpoint) { this.ollamaEndpoint = endpoint; save(); }

    public String getOllamaModel() { return ollamaModel; }
    public void setOllamaModel(String model) { this.ollamaModel = model; save(); }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temp) { this.temperature = temp; save(); }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int tokens) { this.maxTokens = tokens; save(); }

    public boolean isEnabled() { return enabled && !provider.equals("none"); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; save(); }

    public boolean isAutoGenerate() { return autoGenerate; }
    public void setAutoGenerate(boolean autoGen) { this.autoGenerate = autoGen; save(); }

    /** 安全显示 API Key（只显示前4位） */
    public String getMaskedApiKey() {
        if (apiKey == null || apiKey.length() <= 4) return "****";
        return apiKey.substring(0, 4) + "****";
    }

    public String getStatusString() {
        if (!enabled) return "§c已禁用";
        return switch (provider) {
            case "deepseek" -> "§aDeepSeek §7(" + deepseekModel + ") " + getMaskedApiKey();
            case "ollama" -> "§aOllama §7(" + ollamaModel + " @ " + ollamaEndpoint + ")";
            default -> "§7未配置";
        };
    }
}
