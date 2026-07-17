package io.github.shade.story.aigen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.shade.ShadeMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * AI 生成器配置 — 持久化到世界存档目录
 *
 * 保存位置：<world>/data/shade/story/ai_config.json
 */
public class AiConfig {

    private static AiConfig INSTANCE;
    private static Path configPath;

    /** AI 提供者类型：deepseek / ollama / custom / none */
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

    /** 自定义/通用 OpenAI 兼容 API 端点 */
    private String customEndpoint = "https://api.openai.com/v1/chat/completions";

    /** 自定义/通用 API 模型名 */
    private String customModel = "gpt-4o-mini";

    /** 自定义/通用 API 密钥 */
    private String customApiKey = "";

    /** Claude (Anthropic) API 端点 */
    private String claudeEndpoint = "https://api.anthropic.com/v1/messages";

    /** Claude 模型名 */
    private String claudeModel = "claude-sonnet-4-20250514";

    /** Claude API 密钥 */
    private String claudeApiKey = "";

    /** 温度参数 (0.0~2.0) */
    private double temperature = 0.8;

    /** 最大生成长度 */
    private int maxTokens = 1024;

    /** 是否启用 AI 生成 */
    private boolean enabled = false;

    /** 是否在故事中遇到 AI_GENERATE 节点时自动触发 */
    private boolean autoGenerate = false;

    /** 缓存 Gson 实例（避免每次 save 创建新对象） */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 脏标记 + 最后保存时间（用于合并频繁写入） */
    private transient volatile boolean dirty = false;
    private static final long DEBOUNCE_MS = 500;

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
        if (INSTANCE != null) INSTANCE.saveSync();
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
            AiConfig config = GSON.fromJson(reader, AiConfig.class);
            if (config == null) config = new AiConfig();
            return config;
        } catch (IOException e) {
            ShadeMod.LOGGER.error("[ai] 加载配置失败", e);
            return new AiConfig();
        }
    }

    public void save() {
        if (configPath == null) return;
        dirty = true;
        // 延迟合并写入：500ms 内的多次 save() 只触发一次磁盘写入
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(DEBOUNCE_MS);
                if (!dirty) return; // 已被后续写入覆盖
                dirty = false;
                synchronized (configPath) {
                    Files.createDirectories(configPath.getParent());
                    try (Writer writer = Files.newBufferedWriter(configPath)) {
                        GSON.toJson(this, writer);
                    }
                }
            } catch (IOException | InterruptedException e) {
                ShadeMod.LOGGER.error("[ai] 保存配置失败", e);
            }
        });
    }

    /** 同步保存（服务器关闭时调用，确保数据落盘） */
    public void saveSync() {
        if (configPath == null) return;
        dirty = false;
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(this, writer);
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

    public String getCustomEndpoint() { return customEndpoint; }
    public void setCustomEndpoint(String endpoint) { this.customEndpoint = endpoint; save(); }

    public String getCustomModel() { return customModel; }
    public void setCustomModel(String model) { this.customModel = model; save(); }

    public String getCustomApiKey() { return customApiKey; }
    public void setCustomApiKey(String key) { this.customApiKey = key; save(); }

    public String getClaudeEndpoint() { return claudeEndpoint; }
    public void setClaudeEndpoint(String endpoint) { this.claudeEndpoint = endpoint; save(); }

    public String getClaudeModel() { return claudeModel; }
    public void setClaudeModel(String model) { this.claudeModel = model; save(); }

    public String getClaudeApiKey() { return claudeApiKey; }
    public void setClaudeApiKey(String key) { this.claudeApiKey = key; save(); }

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

    /** 获取当前提供者对应的 API Key（用于测试连接等通用逻辑） */
    public String getCurrentApiKey() {
        return switch (provider) {
            case "deepseek" -> apiKey;
            case "claude" -> claudeApiKey;
            case "custom" -> customApiKey;
            default -> "";
        };
    }

    public String getStatusString() {
        if (!enabled) return "§c已禁用";
        return switch (provider) {
            case "deepseek" -> "§aDeepSeek §7(" + deepseekModel + ") " + getMaskedApiKey();
            case "ollama" -> "§aOllama §7(" + ollamaModel + " @ " + ollamaEndpoint + ")";
            case "claude" -> "§aClaude §7(" + claudeModel + ") " + (claudeApiKey.isEmpty() ? "§c未设置 Key" : claudeApiKey.substring(0, Math.min(4, claudeApiKey.length())) + "****");
            case "custom" -> "§a自定义 §7(" + customModel + " @ " + customEndpoint + ") " + (customApiKey.isEmpty() ? "§c未设置 Key" : customApiKey.substring(0, Math.min(4, customApiKey.length())) + "****");
            default -> "§7未配置";
        };
    }
}
