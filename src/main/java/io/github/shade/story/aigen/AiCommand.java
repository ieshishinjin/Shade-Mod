package io.github.shade.story.aigen;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.shade.story.StoryEngine;
import io.github.shade.story.model.StoryNode;
import io.github.shade.story.network.StoryPayloads;
import net.minecraft.commands.CommandSourceStack;
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
        if (!name.equals("deepseek") && !name.equals("ollama")) {
            ctx.getSource().sendFailure(Component.literal("§c支持: deepseek, ollama"));
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
            default -> "—";
        };
    }
}
