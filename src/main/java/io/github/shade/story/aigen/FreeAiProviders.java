package io.github.shade.story.aigen;

import java.util.List;

/**
 * 免费 / 低成本 AI 模型推荐 — 对应设计文档 §5.8
 *
 * 内置有免费额度或完全免费的 AI 服务商列表。
 * 玩家可通过 /story ai recommend 查看和选择。
 * 列表可通过配置文件扩展，无需更新模组版本。
 */
public class FreeAiProviders {

    private FreeAiProviders() {}

    /** 更新日期 */
    public static final String UPDATE_DATE = "2026-07-11";

    /**
     * 内置推荐服务商列表
     */
    public static List<ProviderInfo> getBuiltInProviders() {
        return List.of(
                new ProviderInfo(
                        "zhipu",
                        "智谱 AI",
                        "https://open.bigmodel.cn/api/paas/v4/chat/completions",
                        "glm-4-flash",
                        "完全免费",
                        "GLM-4-Flash 模型完全免费调用，无需付费",
                        "https://open.bigmodel.cn/",
                        "注册后进入 API 密钥页面创建 API key",
                        true
                ),
                new ProviderInfo(
                        "xunfei",
                        "讯飞星火",
                        "https://spark-api-open.xf-yun.com/v1/chat/completions",
                        "lite",
                        "完全免费",
                        "Lite 模型完全免费，无限制调用",
                        "https://xinghuo.xfyun.cn/",
                        "注册后创建应用，获取 app_id 和 api_key",
                        true
                ),
                new ProviderInfo(
                        "deepseek",
                        "DeepSeek",
                        "https://api.deepseek.com/v1/chat/completions",
                        "deepseek-chat",
                        "新用户赠额",
                        "新用户注册赠送 500 万 tokens（约 5 元），之后按量计费：输入 0.5元/百万tokens，输出 2元/百万tokens",
                        "https://platform.deepseek.com/",
                        "注册后在 API Keys 页面创建 key",
                        false
                ),
                new ProviderInfo(
                        "mistral",
                        "Mistral AI",
                        "https://api.mistral.ai/v1/chat/completions",
                        "mistral-tiny",
                        "免费额度",
                        "每月约 10 亿 tokens 免费额度，无需绑定信用卡。超出后按量计费",
                        "https://console.mistral.ai/",
                        "注册后在 API Keys 页面创建 key",
                        false
                ),
                new ProviderInfo(
                        "groq",
                        "Groq",
                        "https://api.groq.com/openai/v1/chat/completions",
                        "llama3-70b-8192",
                        "免费额度",
                        "免费层提供速率限制（约 30请求/分钟），推理速度极快。适合开发和测试",
                        "https://console.groq.com/",
                        "注册后在 API Keys 页面创建 key",
                        false
                ),
                new ProviderInfo(
                        "huggingface",
                        "Hugging Face",
                        "https://api-inference.huggingface.co/v1/chat/completions",
                        "microsoft/DialoGPT-medium",
                        "免费额度",
                        "免费层每月 50 万 tokens。可选模型：Qwen2.5-72B、Llama-3.1-70B 等",
                        "https://huggingface.co/settings/tokens",
                        "注册后在 Settings → Access Tokens 创建 token",
                        false
                )
        );
    }

    /**
     * 根据 ID 查找服务商
     */
    public static ProviderInfo findById(String id) {
        for (ProviderInfo p : getBuiltInProviders()) {
            if (p.id().equals(id)) return p;
        }
        return null;
    }

    /**
     * 服务商信息记录
     *
     * @param id                唯一标识（用于命令引用）
     * @param name              显示名称（中文）
     * @param apiEndpoint       API 完整地址
     * @param modelName         推荐模型名
     * @param freeTag           免费标签文字（如 "完全免费"）
     * @param costDetails       详细费用说明
     * @param registrationUrl   官网注册地址
     * @param apiKeyHelp        获取 API Key 的帮助文字
     * @param completelyFree    是否完全免费（true=完全免费, false=有免费额度）
     */
    public record ProviderInfo(
            String id,
            String name,
            String apiEndpoint,
            String modelName,
            String freeTag,
            String costDetails,
            String registrationUrl,
            String apiKeyHelp,
            boolean completelyFree
    ) {
        /** 格式化显示的一行摘要 */
        public String toDisplayLine() {
            return String.format("§6[%s] §f%s §7- §e%s §7(%s)",
                    id, name, modelName, freeTag);
        }

        /** 格式化详情的多行文本 */
        public String[] toDetailLines() {
            return new String[]{
                    "§6=== " + name + " ===",
                    "  §7推荐模型: §f" + modelName,
                    "  §7API 地址: §f" + apiEndpoint,
                    "  §7费用: " + (completelyFree ? "§a完全免费" : "§e" + freeTag),
                    "  §7详情: §f" + costDetails,
                    "  §7获取 Key: §f" + apiKeyHelp,
                    "  §7注册地址: §b" + registrationUrl
            };
        }
    }
}
