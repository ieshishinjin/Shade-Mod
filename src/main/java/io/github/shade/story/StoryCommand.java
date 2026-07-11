package io.github.shade.story;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.shade.story.model.StoryChoice;
import io.github.shade.story.model.StoryNode;
import io.github.shade.story.model.StoryScript;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;

/**
 * /story 命令系统 — 对应设计文档 §Phase 5
 *
 * 命令列表：
 *   /story start <script>      — 开始指定剧情
 *   /story status              — 查看当前剧情状态
 *   /story list                — 列出所有可用的脚本
 *   /story advance             — 推进到下一节点
 *   /story choose <index>      — 选择选项
 *   /story reset               — 重置剧情状态
 *   /story reload              — 热加载所有脚本（OP）
 */
public class StoryCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("story")
                        .then(literal("start")
                                .then(argument("script", StringArgumentType.word())
                                        .suggests(StoryCommand::suggestScripts)
                                        .executes(StoryCommand::executeStart))
                        )
                        .then(literal("status")
                                .executes(StoryCommand::executeStatus)
                        )
                        .then(literal("list")
                                .executes(StoryCommand::executeList)
                        )
                        .then(literal("advance")
                                .executes(StoryCommand::executeAdvance)
                        )
                        .then(literal("choose")
                                .then(argument("index", IntegerArgumentType.integer(0))
                                        .executes(StoryCommand::executeChoose))
                        )
                        .then(literal("reset")
                                .executes(StoryCommand::executeReset)
                        )
                        .then(literal("reload")
                                .requires(src -> src.hasPermission(2))
                                .executes(StoryCommand::executeReload)
                        )
        );
    }

    // ==================== 命令执行 ====================

    private static int executeStart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String scriptId = StringArgumentType.getString(ctx, "script");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StoryEngine engine = StoryEngine.getInstance(player.serverLevel());

        StoryScript script = engine.getScript(scriptId);
        if (script == null) {
            ctx.getSource().sendFailure(
                    Component.translatable("shade.story.script.notfound", scriptId));
            return 0;
        }

        StoryNode startNode = engine.startScript(player, scriptId);
        if (startNode == null) {
            ctx.getSource().sendFailure(
                    Component.translatable("shade.story.start.failed", script.getTitle()));
            return 0;
        }

        // Phase 1 使用聊天消息显示，Phase 2 替换为 GUI 包
        displayNode(player, startNode);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("shade.story.start.success", script.getTitle()), false);
        return 1;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StoryEngine engine = StoryEngine.getInstance(player.serverLevel());

        if (!engine.isInStory(player)) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("shade.story.status.none"), false);
            return 0;
        }

        String scriptId = engine.getActiveScriptId(player);
        StoryScript script = engine.getScript(scriptId);
        StoryNode node = engine.getCurrentNode(player);

        ctx.getSource().sendSuccess(() ->
                Component.literal("§6✦ 当前剧情: §e" + (script != null ? script.getTitle() : scriptId)), false);
        if (node != null) {
            ctx.getSource().sendSuccess(() ->
                    Component.literal("§7  节点: §f" + node.getId() + " §7(" + node.getType() + ")"), false);
            if (node.getSpeaker() != null) {
                ctx.getSource().sendSuccess(() ->
                        Component.literal("§7  说话人: §f" + node.getSpeaker()), false);
            }
        }
        return 1;
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StoryEngine engine = StoryEngine.getInstance(player.serverLevel());
        var allScripts = engine.getAllScripts();
        StoryManager manager = StoryManager.getInstance(player.serverLevel());

        if (allScripts.isEmpty()) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("shade.story.list.none"), false);
            return 0;
        }

        ctx.getSource().sendSuccess(() ->
                Component.translatable("shade.story.list.header", allScripts.size()), false);

        for (StoryScript script : allScripts) {
            boolean completed = manager.isScriptCompleted(player, script.getId());
            String status = completed ? "§a✓" : "§7○";
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("  %s §e%s §7- %s",
                            status, script.getId(), script.getTitle())), false);
        }
        return allScripts.size();
    }

    private static int executeAdvance(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StoryEngine engine = StoryEngine.getInstance(player.serverLevel());

        if (!engine.isInStory(player)) {
            ctx.getSource().sendFailure(
                    Component.translatable("shade.story.advance.noactive"));
            return 0;
        }

        StoryNode nextNode = engine.advance(player);

        if (nextNode == null) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("shade.story.end"), false);
            return 1;
        }

        displayNode(player, nextNode);
        return 1;
    }

    private static int executeChoose(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int index = IntegerArgumentType.getInteger(ctx, "index");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StoryEngine engine = StoryEngine.getInstance(player.serverLevel());

        if (!engine.isInStory(player)) {
            ctx.getSource().sendFailure(
                    Component.translatable("shade.story.advance.noactive"));
            return 0;
        }

        StoryNode nextNode = engine.choose(player, index);
        if (nextNode == null) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("shade.story.end"), false);
            return 1;
        }

        displayNode(player, nextNode);
        return 1;
    }

    private static int executeReset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StoryManager manager = StoryManager.getInstance(player.serverLevel());
        StoryEngine engine = StoryEngine.getInstance(player.serverLevel());

        engine.endStory(player);
        manager.getProgress(player).reset();
        manager.save(player);

        ctx.getSource().sendSuccess(() ->
                Component.translatable("shade.story.reset.success"), true);
        return 1;
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StoryEngine engine = StoryEngine.getInstance(player.serverLevel());
        engine.loadScripts();

        ctx.getSource().sendSuccess(() ->
                Component.translatable("shade.story.reload.success"), true);
        return 1;
    }

    // ==================== 节点显示 ====================

    /**
     * 向玩家展示剧情节点（Phase 1 使用聊天消息，Phase 2 替换为 GUI）
     */
    private static void displayNode(ServerPlayer player, StoryNode node) {
        switch (node.getType()) {
            case DIALOG -> displayDialog(player, node);
            case CHOICE -> displayChoice(player, node);
            case QUEST_START -> {
                player.sendSystemMessage(Component.literal(
                        "§6✦ §l新任务: §r§e" + node.getQuest().getQuestName()));
                player.sendSystemMessage(Component.literal(
                        "§7" + node.getQuest().getQuestDescription()));
                if (node.getSpeaker() != null) {
                    displayDialog(player, node);
                }
            }
            case QUEST_UPDATE -> {
                player.sendSystemMessage(Component.literal(
                        "§b✦ 任务更新: §f" + node.getText()));
            }
            case QUEST_COMPLETE -> {
                player.sendSystemMessage(Component.literal(
                        "§a✦ §l任务完成!"));
                if (node.getText() != null && !node.getText().isEmpty()) {
                    player.sendSystemMessage(Component.literal("§f" + node.getText()));
                }
            }
            case END -> {
                player.sendSystemMessage(Component.literal(
                        "§7━━━━━━━━━━━━━━━━━"));
                player.sendSystemMessage(Component.literal(
                        "§e§l - 本章完 -"));
                player.sendSystemMessage(Component.literal(
                        "§7━━━━━━━━━━━━━━━━━"));
            }
            default -> {}
        }
    }

    private static void displayDialog(ServerPlayer player, StoryNode node) {
        String speaker = node.getSpeaker() != null ? node.getSpeaker() : "???";
        player.sendSystemMessage(Component.literal(
                "§7━━━━━━━━━━━━━━━━━"));
        player.sendSystemMessage(Component.literal(
                "§e§l" + speaker + "§r§7: "));
        player.sendSystemMessage(Component.literal(
                "§f" + node.getText()));
        player.sendSystemMessage(Component.literal(
                "§7━━━━━━━━━━━━━━━━━"));
        player.sendSystemMessage(
                Component.literal("§7[点击继续]")
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND, "/story advance"))
                                .withHoverEvent(new HoverEvent(
                                        HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("点击继续对话")))
                        )
        );
    }

    private static void displayChoice(ServerPlayer player, StoryNode node) {
        String speaker = node.getSpeaker() != null ? node.getSpeaker() : "???";
        player.sendSystemMessage(Component.literal(
                "§7━━━━━━━━━━━━━━━━━"));
        player.sendSystemMessage(Component.literal(
                "§e§l" + speaker + "§r§7: "));
        player.sendSystemMessage(Component.literal(
                "§f" + node.getText()));
        player.sendSystemMessage(Component.literal("§7"));

        List<StoryChoice> options = node.getOptions();
        if (options != null) {
            for (int i = 0; i < options.size(); i++) {
                int index = i;
                StoryChoice choice = options.get(i);
                MutableComponent optMsg = Component.literal(
                        String.format("§6[%d] §f%s", i + 1, choice.getLabel()))
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND, "/story choose " + index))
                                .withHoverEvent(new HoverEvent(
                                        HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("选择此选项")))
                        );
                player.sendSystemMessage(optMsg);
            }
        }
        player.sendSystemMessage(Component.literal("§7━━━━━━━━━━━━━━━━━"));
    }

    // ==================== 补全 ====================

    private static CompletableFuture<Suggestions> suggestScripts(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        // 从已加载的脚本列表补全（不需要 player）
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            StoryEngine engine = StoryEngine.getInstance(player.serverLevel());
            return SharedSuggestionProvider.suggest(
                    engine.getAllScripts().stream().map(StoryScript::getId), builder);
        } catch (CommandSyntaxException e) {
            return Suggestions.empty();
        }
    }
}
