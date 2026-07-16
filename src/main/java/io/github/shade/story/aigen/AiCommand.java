package io.github.shade.story.aigen;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.shade.story.StoryEngine;
import io.github.shade.story.model.StoryNode;
import io.github.shade.story.network.StoryPayloads;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;

/**
 * /story ai 命令 — AI 剧情生成器的配置和使用
 *
 * 命令列表：
 *   /story ai status                          — 查看 AI 配置状态
 *   /story ai enable                          — 启用 AI 生成
 *   /story ai disable                         — 禁用 AI 生成
 *   /story ai provider deepseek|ollama        — 设置 AI 提供者
 *   /story ai key <api_key>                   — 设置 DeepSeek API 密钥
 *   /story ai model <model>                   — 设置模型名
 *   /story ai endpoint <url>                  — 设置 API 端点
 *   /story ai temperature <0.0~2.0>           — 设置生成温度
 *   /story ai maxtokens <number>              — 设置最大生成长度
 *   /story ai test                            — 测试 AI 连接
 *   /story ai generate [prompt]               — 手动触发 AI 生成
 *   /story ai autogen                         — 切换自动生成模式
 *   /story ai recommend                       — 查看免费/低成本 AI 推荐
 *   /story ai recommend <id>                  — 选择推荐服务商并填入配置
 *   /story ai recommend <id> open             — 打开官网注册地址（可点击）
 */
public class AiCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("story")
                        .then(literal("ai")
                                .then(literal("status")
                                        .executes(AiCommand::executeStatus))
                                .then(literal("enable")
                                        .requires(s -> s.hasPermission(2))
                                        .executes(AiCommand::executeEnable))
                                .then(literal("disable")
                                        .requires(s -> s.hasPermission(2))
                                        .executes(AiCommand::executeDisable))
                                .then(literal("provider")
                                        .requires(s -> s.hasPermission(2))
                                        .then(argument("name", StringArgumentType.word())
                                                .suggests(AiCommand::suggestProviders)
                                                .executes(AiCommand::executeProvider)))
                                .then(literal("key")
                                        .requires(s -> s.hasPermission(2))
                                        .then(argument("key", StringArgumentType.greedyString())
                                                .executes(AiCommand::executeKey)))
                                .then(literal("model")
                                        .requires(s -> s.hasPermission(2))
                                        .then(argument("model", StringArgumentType.word())
                                                .executes(AiCommand::executeModel)))
                                .then(literal("endpoint")
                                        .requires(s -> s.hasPermission(2))
                                        .then(argument("url", StringArgumentType.greedyString())
                                                .executes(AiCommand::executeEndpoint)))
                                .then(literal("temperature")
                                        .requires(s -> s.hasPermission(2))
                                        .then(argument("value", DoubleArgumentType.doubleArg(0.0, 2.0))
                                                .executes(AiCommand::executeTemperature)))
                                .then(literal("maxtokens")
                                        .requires(s -> s.hasPermission(2))
                                        .then(argument("count", IntegerArgumentType.integer(64, 4096))
                                                .executes(AiCommand::executeMaxTokens)))
                                .then(literal("test")
                                        .executes(AiCommand::executeTest))
                                .then(literal("generate")
                                        .executes(ctx -> executeGenerate(ctx, null))
                                        .then(argument("prompt", StringArgumentType.greedyString())
                                                .executes(ctx -> executeGenerate(ctx,
                                                        StringArgumentType.getString(ctx, "prompt")))))
                                .then(literal("autogen")
                                        .requires(s -> s.hasPermission(2))
                                        .executes(AiCommand::executeAutoGen))
                                .then(literal("recommend")
                                        .executes(AiCommand::executeRecommendList)
                                        .then(argument("provider", StringArgumentType.word())
                                                .suggests(AiCommand::suggestRecommends)
                                                .executes(ctx -> executeRecommendSelect(
                                                        ctx, StringArgumentType.getString(ctx, "provider")))
                                                .then(literal("open")
                                                        .executes(ctx -> executeRecommendOpen(
                                                                ctx, StringArgumentType.getString(ctx, "provider"))))))
                        )
        );
    }

    // ==================== 配置命令 ====================

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        AiConfig config = AiConfig.getInstance(player.serverLevel());

        ctx.getSource().sendSuccess(() -> Component.literal("§6=== AI 剧情生成配置 ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "  " + (config.isEnabled() ? "§a已启用" : "§c已禁用")), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "  提供者: " + config.getStatusString()), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "  模型: §f" + getProviderModel(config)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "  温度: §f" + config.getTemperature()), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "  最大长度: §f" + config.getMaxTokens()), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "  自动生成: " + (config.isAutoGenerate() ? "§a开启" : "§7关闭")), false);
        return 1;
    }

    private static int executeEnable(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        AiConfig config = AiConfig.getInstance(player.serverLevel());
        config.setEnabled(true);
        ctx.getSource().sendSuccess(() ->
                Component.literal("§a✔ AI 生成已启用"), true);
        return 1;
    }

    private static int executeDisable(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        AiConfig config = AiConfig.getInstance(player.serverLevel());
        config.setEnabled(false);
        ctx.getSource().sendSuccess(() ->
                Component.literal("§cAI 生成已禁用"), true);
        return 1;
    }

    private static int executeProvider(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name").toLowerCase();
        if (!name.equals("deepseek") && !name.equals("ollama") && !name.equals("custom") && !name.equals("claude")) {
            ctx.getSource().sendFailure(Component.literal("§c支持: deepseek, ollama, claude, custom"));
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        AiConfig config = AiConfig.getInstance(player.serverLevel());
        config.setProvider(name);
        ctx.getSource().sendSuccess(() ->
                Component.literal("§a✔ 已设置提供者: " + name), true);
        return 1;
    }

    private static int executeKey(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String key = StringArgumentType.getString(ctx, "key");
        if (key.length() < 8) {
            ctx.getSource().sendFailure(Component.literal("§cAPI Key 长度不足"));
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        AiConfig config = AiConfig.getInstance(player.serverLevel());
        config.setApiKey(key);
        ctx.getSource().sendSuccess(() ->
                Component.literal("§a✔ API Key 已设置"), true);
        return 1;
    }

    private static int executeModel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String model = StringArgumentType.getString(ctx, "model");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        AiConfig config = AiConfig.getInstance(player.serverLevel());
        switch (config.getProvider()) {
            case "deepseek" -> config.setDeepseekModel(model);
            case "ollama" -> config.setOllamaModel(model);
            case "claude" -> config.setClaudeModel(model);
            case "custom" -> config.setCustomModel(model);
        }
        ctx.getSource().sendSuccess(() ->
                Component.literal("§a✔ 已设置模型: " + model), true);
        return 1;
    }

    private static int executeEndpoint(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String url = StringArgumentType.getString(ctx, "url");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        AiConfig config = AiConfig.getInstance(player.serverLevel());
        switch (config.getProvider()) {
            case "deepseek" -> config.setDeepseekEndpoint(url);
            case "ollama" -> config.setOllamaEndpoint(url);
            case "claude" -> config.setClaudeEndpoint(url);
            case "custom" -> config.setCustomEndpoint(url);
        }
        ctx.getSource().sendSuccess(() ->
                Component.literal("§a✔ 已设置端点: " + url), true);
        return 1;
    }

    private static int executeTemperature(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        double temp = DoubleArgumentType.getDouble(ctx, "value");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        AiConfig config = AiConfig.getInstance(player.serverLevel());
        config.setTemperature(temp);
        ctx.getSource().sendSuccess(() ->
                Component.literal("§a✔ 已设置温度: " + temp), true);
        return 1;
    }

    private static int executeMaxTokens(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int count = IntegerArgumentType.getInteger(ctx, "count");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        AiConfig config = AiConfig.getInstance(player.serverLevel());
        config.setMaxTokens(count);
        ctx.getSource().sendSuccess(() ->
                Component.literal("§a✔ 已设置最大长度: " + count), true);
        return 1;
    }

    // ==================== 操作命令 ====================

    private static int executeTest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        AiConfig config = AiConfig.getInstance(player.serverLevel());

        if (!config.isEnabled()) {
            ctx.getSource().sendFailure(Component.literal("§cAI 未启用，请先使用 /story ai enable"));
            return 0;
        }

        ctx.getSource().sendSuccess(() ->
                Component.literal("§e⟳ 正在测试 AI 连接..."), false);

        StoryAiGenerator generator = StoryAiGenerator.getInstance();
        AiProvider provider = generator.getProvider(config);

        if (provider == null) {
            ctx.getSource().sendFailure(Component.literal("§c未配置 AI 提供者"));
            return 0;
        }

        provider.testConnection(config).thenAccept(ok -> {
            player.server.execute(() -> {
                if (ok) {
                    player.sendSystemMessage(Component.literal("§a✔ AI 连接正常！"));
                } else {
                    player.sendSystemMessage(Component.literal("§c✘ AI 连接失败，请检查配置"));
                }
            });
        });

        return 1;
    }

    private static int executeGenerate(CommandContext<CommandSourceStack> ctx, String userPrompt)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel world = player.serverLevel();
        StoryEngine engine = StoryEngine.getInstance(world);
        AiConfig config = AiConfig.getInstance(world);

        if (!config.isEnabled()) {
            ctx.getSource().sendFailure(Component.literal("§cAI 未启用"));
            return 0;
        }

        // 不在剧情中时自动开始一个临时剧情
        if (!engine.isInStory(player)) {
            ctx.getSource().sendSuccess(() ->
                    Component.literal("§e✦ 自动创建临时剧情场景..."), false);
        }

        ctx.getSource().sendSuccess(() ->
                Component.literal("§e✦ AI 正在生成剧情..."), false);

        StoryAiGenerator generator = StoryAiGenerator.getInstance();

        generator.generate(player, engine, config, userPrompt)
                .thenAccept(result -> {
                    player.server.execute(() -> {
                        if (result.isError()) {
                            player.sendSystemMessage(Component.literal(
                                    "§c✘ " + result.getError()));
                            return;
                        }

                        // 注入生成的节点
                        StoryNode firstNode = generator.injectNodes(player, engine, result);
                        if (firstNode != null) {
                            // 解析并发送到客户端
                            StoryNode displayNode = engine.resolveAndGetDisplayNode(player);
                            if (displayNode != null) {
                                StoryPayloads.sendNodeToClient(player, engine, displayNode);
                            }
                            player.sendSystemMessage(Component.literal(
                                    "§a✔ AI 生成完成！"));
                        }
                    });
                });

        return 1;
    }

    private static int executeAutoGen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        AiConfig config = AiConfig.getInstance(player.serverLevel());
        boolean newState = !config.isAutoGenerate();
        config.setAutoGenerate(newState);
        ctx.getSource().sendSuccess(() -> Component.literal(
                newState ? "§a✔ AI 自动生成已开启" : "§cAI 自动生成已关闭"), true);
        return 1;
    }

    // ==================== 工具 ====================

    private static String getProviderModel(AiConfig config) {
        return switch (config.getProvider()) {
            case "deepseek" -> config.getDeepseekModel();
            case "ollama" -> config.getOllamaModel();
            case "claude" -> config.getClaudeModel();
            case "custom" -> config.getCustomModel();
            default -> "—";
        };
    }

    // ==================== 推荐服务商 ====================

    /**
     * 列出所有推荐服务商
     */
    private static int executeRecommendList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var providers = FreeAiProviders.getBuiltInProviders();

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§6=== 免费/低成本 AI 服务商推荐 ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "  §7更新日期: " + FreeAiProviders.UPDATE_DATE), false);
        ctx.getSource().sendSuccess(() -> Component.literal(""), false);

        for (var p : providers) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "  " + p.toDisplayLine()), false);
        }

        ctx.getSource().sendSuccess(() -> Component.literal(""), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§7使用 §e/story ai recommend <id>§7 选择并自动填入配置"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§7使用 §e/story ai recommend <id> open§7 查看注册链接"), false);

        return providers.size();
    }

    /**
     * 选择推荐服务商，自动填入 API 地址和模型名
     */
    private static int executeRecommendSelect(CommandContext<CommandSourceStack> ctx, String providerId)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var provider = FreeAiProviders.findById(providerId);

        if (provider == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "§c未找到服务商 '" + providerId + "'，使用 /story ai recommend 查看列表"));
            return 0;
        }

        AiConfig config = AiConfig.getInstance(player.serverLevel());

        // 自动填入配置
        if (providerId.equals("deepseek")) {
            config.setProvider("deepseek");
            config.setDeepseekEndpoint(provider.apiEndpoint());
            config.setDeepseekModel(provider.modelName());
        } else {
            // 其他服务商（智谱、星火、Groq 等）→ 存入自定义字段
            config.setProvider("custom");
            config.setCustomEndpoint(provider.apiEndpoint());
            config.setCustomModel(provider.modelName());
            // API Key 仍用通用字段
        }

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a✔ 已选择: " + provider.name()), true);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "  §7API 地址: §f" + provider.apiEndpoint()), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "  §7推荐模型: §f" + provider.modelName()), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "  §7费用: " + (provider.completelyFree() ? "§a完全免费" : "§e" + provider.freeTag())), false);

        // 显示费用详情（可悬停）
        ctx.getSource().sendSuccess(() -> Component.literal(
                "  §7[ℹ️] " + provider.costDetails()), false);

        // 提示下一步
        ctx.getSource().sendSuccess(() -> Component.literal(""), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§7下一步：使用 §e/story ai key <你的API密钥>§7 设置 API Key"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§7  " + provider.apiKeyHelp()), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§7注册地址：§b" + provider.registrationUrl()), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§7然后使用 §e/story ai enable§7 启用 AI 生成"), false);

        return 1;
    }

    /**
     * 显示官网注册链接（可点击打开）
     */
    private static int executeRecommendOpen(CommandContext<CommandSourceStack> ctx, String providerId)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var provider = FreeAiProviders.findById(providerId);

        if (provider == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "§c未找到服务商 '" + providerId + "'"));
            return 0;
        }

        // 发送可点击的链接
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§6" + provider.name() + " §7官网注册地址:"), false);
        ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "§b" + provider.registrationUrl())
                .withStyle(style -> style
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.OPEN_URL,
                                provider.registrationUrl()))
                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                Component.literal("点击打开 " + provider.name() + " 官网"))))
                , false);

        ctx.getSource().sendSuccess(() -> Component.literal(
                "  §7API Key 获取: §f" + provider.apiKeyHelp()), false);

        return 1;
    }

    // ==================== Tab 补全 ====================

    private static CompletableFuture<Suggestions> suggestProviders(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                java.util.List.of("deepseek", "ollama", "claude", "custom"), builder);
    }

    private static CompletableFuture<Suggestions> suggestRecommends(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                java.util.List.of("zhipu", "xunfei", "deepseek", "mistral", "groq", "huggingface"), builder);
    }
}
