package io.github.shade.story.aigen;

import io.github.shade.ShadeMod;
import io.github.shade.story.StoryEngine;
import io.github.shade.story.StoryManager;
import io.github.shade.story.model.*;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI 剧情生成器协调器 — 对应设计文档 §五 "AI 剧情生成器"
 *
 * 这是整个 AI 生成功能的入口。协调：
 * 1. AiProvider — AI 后端调用
 * 2. PromptBuilder — 提示词构建
 * 3. ResponseParser — 响应解析
 *
 * 生成流程（§5.4）：
 * 收集状态 → 构建 Prompt → 调用 AI → 解析校验 → 插入剧情流 → 展示
 */
public class StoryAiGenerator {

    private static StoryAiGenerator INSTANCE;
    private AiProvider currentProvider;

    private StoryAiGenerator() {}

    public static StoryAiGenerator getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new StoryAiGenerator();
        }
        return INSTANCE;
    }

    /**
     * 获取当前配置的 AI 提供者
     */
    public AiProvider getProvider(AiConfig config) {
        String providerType = config.getProvider();
        if (currentProvider != null) {
            // 检查是否切换了提供者
            String currentName = currentProvider.getName().toLowerCase();
            if (currentName.equals(providerType)) {
                return currentProvider;
            }
        }

        currentProvider = switch (providerType) {
            case "deepseek" -> new DeepSeekProvider();
            case "ollama" -> new OllamaProvider();
            default -> null;
        };

        return currentProvider;
    }

    /**
     * 异步生成剧情节点
     *
     * @param player    目标玩家
     * @param engine    剧情引擎（用于获取上下文）
     * @param config    AI 配置
     * @param userInput 玩家输入（可选）
     * @return 异步返回生成结果
     */
    public CompletableFuture<GenerateResult> generate(ServerPlayer player,
                                                       StoryEngine engine,
                                                       AiConfig config,
                                                       String userInput) {
        CompletableFuture<GenerateResult> result = new CompletableFuture<>();

        if (!config.isEnabled()) {
            result.complete(GenerateResult.error("AI 生成未启用，请先使用 /story ai 配置"));
            return result;
        }

        AiProvider provider = getProvider(config);
        if (provider == null) {
            result.complete(GenerateResult.error("未配置 AI 提供者 (deepseek/ollama)"));
            return result;
        }

        // 构建提示词
        String systemPrompt = PromptBuilder.buildSystemPrompt();
        String userPrompt = PromptBuilder.buildUserPrompt(player, engine, userInput);

        ShadeMod.LOGGER.info("[ai] 开始 AI 生成... (provider={}, input={})",
                provider.getName(), userInput != null ? userInput.substring(0, Math.min(20, userInput.length())) : "(none)");

        // 异步调用 AI
        provider.generate(systemPrompt, userPrompt, config)
                .thenApply(aiResponse -> {
                    ShadeMod.LOGGER.debug("[ai] AI 原始响应:\n{}", aiResponse);

                    // 解析响应
                    ResponseParser.ParseResult parseResult = ResponseParser.parse(aiResponse);
                    if (parseResult.isError()) {
                        return GenerateResult.error("AI 解析失败: " + parseResult.getError());
                    }

                    // 应用 Flag
                    StoryManager manager = StoryManager.getInstance(player.serverLevel());
                    var progress = manager.getProgress(player);
                    Map<String, Object> flags = parseResult.getFlagsToSet();
                    if (flags != null && !flags.isEmpty()) {
                        progress.getFlags().putAll(flags);
                        manager.save(player);
                    }

                    return GenerateResult.success(
                            parseResult.getNodes(),
                            parseResult.getStartNode()
                    );
                })
                .exceptionally(e -> {
                    ShadeMod.LOGGER.error("[ai] 生成失败", e);
                    return GenerateResult.error("AI 生成失败: " + e.getMessage());
                })
                .thenAccept(result::complete);

        return result;
    }

    /**
     * 将生成的节点注入到当前剧情脚本中
     *
     * @param player     玩家
     * @param engine     剧情引擎
     * @param generateResult 生成结果
     * @return 注入后的起始节点，用于显示
     */
    public StoryNode injectNodes(ServerPlayer player, StoryEngine engine,
                                  GenerateResult generateResult) {
        if (!generateResult.isSuccess() || generateResult.getNodes() == null) {
            return null;
        }

        String scriptId = engine.getActiveScriptId(player);
        if (scriptId == null) return null;

        StoryScript existingScript = engine.getScript(scriptId);
        if (existingScript == null) return null;

        // 生成唯一前缀避免 ID 冲突
        String prefix = "ai_" + System.currentTimeMillis() % 10000 + "_";

        // 创建标注了 AI 生成的新节点
        StoryNode firstNode = null;
        for (StoryNode node : generateResult.getNodes()) {
            // 重写 ID 避免冲突
            String newId = prefix + node.getId();
            node.setId(newId);

            // 重写所有引用 next
            if (node.getNext() != null && !node.getNext().isEmpty()) {
                if (existingScript.getNode(node.getNext()) == null) {
                    node.setNext(prefix + node.getNext());
                }
            }

            // 重写选项中的 next
            if (node.getOptions() != null) {
                for (StoryChoice choice : node.getOptions()) {
                    if (existingScript.getNode(choice.getNext()) == null) {
                        choice.setNext(prefix + choice.getNext());
                    }
                }
            }

            // 重写条件中的跳转
            if (node.getCondition() != null) {
                if (existingScript.getNode(node.getCondition().getNextIfTrue()) == null) {
                    node.getCondition().setNextIfTrue(prefix + node.getCondition().getNextIfTrue());
                }
                if (existingScript.getNode(node.getCondition().getNextIfFalse()) == null) {
                    node.getCondition().setNextIfFalse(prefix + node.getCondition().getNextIfFalse());
                }
            }

            // 注入到脚本
            existingScript.getNodes().put(node.getId(), node);

            if (firstNode == null) {
                firstNode = node;
            }
        }

        // 将对新节点的引用放入引擎状态
        if (firstNode != null) {
            String startId = prefix + generateResult.getStartNode();
            if (existingScript.getNode(startId) != null) {
                engine.setCurrentNode(player, startId);
            } else {
                engine.setCurrentNode(player, firstNode.getId());
            }
        }

        ShadeMod.LOGGER.debug("[ai] 注入 {} 个 AI 生成节点到脚本 '{}'",
                generateResult.getNodes().size(), scriptId);
        return firstNode;
    }

    // ==================== 内部类 ====================

    public static class GenerateResult {
        private final List<StoryNode> nodes;
        private final String startNode;
        private final String error;

        private GenerateResult(List<StoryNode> nodes, String startNode, String error) {
            this.nodes = nodes;
            this.startNode = startNode;
            this.error = error;
        }

        public static GenerateResult success(List<StoryNode> nodes, String startNode) {
            return new GenerateResult(nodes, startNode, null);
        }

        public static GenerateResult error(String msg) {
            return new GenerateResult(null, null, msg);
        }

        public boolean isSuccess() { return error == null; }
        public boolean isError() { return error != null; }
        public String getError() { return error; }
        public List<StoryNode> getNodes() { return nodes; }
        public String getStartNode() { return startNode; }
    }
}
