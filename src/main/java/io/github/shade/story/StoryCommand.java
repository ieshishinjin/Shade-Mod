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
import io.github.shade.story.network.StoryPayloads;
import io.github.shade.story.quest.QuestManager;
import io.github.shade.story.quest.RuntimeQuest;
import io.github.shade.story.trigger.StoryTrigger;
import io.github.shade.story.trigger.TriggerManager;
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
 *   /story start <script>           — 开始指定剧情
 *   /story status                   — 查看当前剧情状态
 *   /story list                     — 列出所有可用的脚本
 *   /story advance                  — 推进到下一节点
 *   /story choose <index>           — 选择选项
 *   /story complete                 — 强制完成当前 Quest
 *   /story flag set <key> <value>   — 设置剧情 Flag
 *   /story reset                    — 重置所有剧情进度
 *   /story reload                   — 热加载所有脚本（OP）
 *   /story trigger list             — 列出所有触发器
 *   /story trigger add zone <name> <script> <x1> <z1> <x2> <z2>
 *   /story trigger add npc <name> <script> <entity>
 *   /story trigger add item <name> <script> <item>
 *   /story trigger remove <name>
 */
public class StoryCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var trigger = literal("trigger")
                .then(literal("list")
                        .executes(StoryCommand::executeTriggerList))
                .then(literal("remove")
                        .then(argument("name", StringArgumentType.word())
                                .suggests(StoryCommand::suggestTriggers)
                                .executes(StoryCommand::executeTriggerRemove)))
                .then(literal("add")
                        .then(literal("zone")
                                .then(argument("name", StringArgumentType.word())
                                        .then(argument("script", StringArgumentType.word())
                                                .suggests(StoryCommand::suggestScripts)
                                                .then(argument("x1", IntegerArgumentType.integer())
                                                        .then(argument("z1", IntegerArgumentType.integer())
                                                                .then(argument("x2", IntegerArgumentType.integer())
                                                                        .then(argument("z2", IntegerArgumentType.integer())
                                                                                .executes(StoryCommand::executeTriggerAddZone))))))))
                        .then(literal("npc")
                                .then(argument("name", StringArgumentType.word())
                                        .then(argument("script", StringArgumentType.word())
                                                .suggests(StoryCommand::suggestScripts)
                                                .then(argument("entity", StringArgumentType.word())
                                                        .executes(StoryCommand::executeTriggerAddNpc)))))
                        .then(literal("item")
                                .then(argument("name", StringArgumentType.word())
                                        .then(argument("script", StringArgumentType.word())
                                                .suggests(StoryCommand::suggestScripts)
                                                .then(argument("item", StringArgumentType.word())
                                                        .executes(StoryCommand::executeTriggerAddItem))))));

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
                                        .suggests(StoryCommand::suggestChoices)
                                        .executes(StoryCommand::executeChoose))
                        )
                        .then(literal("complete")
                                .executes(StoryCommand::executeComplete)
                        )                        .then(literal("choose_reward")
                                .then(argument("key", StringArgumentType.word())
                                        .executes(StoryCommand::executeChooseReward)))

                        .then(literal("flag")
                                .then(literal("set")
                                        .then(argument("key", StringArgumentType.word())
                                                .then(argument("value", StringArgumentType.word())
                                                        .executes(StoryCommand::executeFlagSet))))
                        )
                        .then(literal("reset")
                                .executes(StoryCommand::executeReset)
                        )
                        .then(literal("quest")
                                .then(literal("list")
                                        .executes(StoryCommand::executeQuestList))
                                .then(literal("abort")
                                        .then(argument("questId", StringArgumentType.word())
                                                .suggests(StoryCommand::suggestActiveQuests)
                                                .executes(StoryCommand::executeQuestAbort)))
                        )
                        .then(literal("reload")
                                .requires(src -> src.hasPermission(2))
                                .executes(StoryCommand::executeReload)
                        )
                        .then(trigger)
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

        // 发送 GUI 包 + 聊天回退
        StoryPayloads.sendNodeToClient(player, engine, startNode);
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
            StoryPayloads.sendNodeToClient(player, engine, null);
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("shade.story.end"), false);
            return 1;
        }

        StoryPayloads.sendNodeToClient(player, engine, nextNode);
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
            StoryPayloads.sendNodeToClient(player, engine, null);
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("shade.story.end"), false);
            return 1;
        }

        StoryPayloads.sendNodeToClient(player, engine, nextNode);
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

    // ==================== 增强命令 ====================

    private static int executeComplete(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        QuestManager qm = QuestManager.getInstance(player.serverLevel());
        List<RuntimeQuest> quests = qm.getActiveQuests(player);

        if (quests.isEmpty()) {
            ctx.getSource().sendSuccess(() ->
                    Component.literal("§7当前没有活跃的 Quest"), false);
            return 0;
        }

        for (RuntimeQuest q : quests) {
            for (var obj : q.getObjectives()) {
                obj.setProgress(obj.getTargetCount());
            }
        }
        ctx.getSource().sendSuccess(() ->
                Component.literal("§a✔ 已强制完成所有活跃 Quest"), true);
        return 1;
    }

    private static int executeFlagSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String key = StringArgumentType.getString(ctx, "key");
        String value = StringArgumentType.getString(ctx, "value");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StoryManager manager = StoryManager.getInstance(player.serverLevel());
        manager.setFlag(player, key, value);
        ctx.getSource().sendSuccess(() ->
                Component.literal("§a✔ 设置 Flag §e" + key + "§7 = §f" + value), false);
        return 1;
    }

    // ==================== 触发器命令 ====================

    private static int executeTriggerList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TriggerManager tm = TriggerManager.getInstance(player.serverLevel());
        var triggers = tm.getAllTriggers();

        if (triggers.isEmpty()) {
            ctx.getSource().sendSuccess(() ->
                    Component.literal("§7没有已配置的触发器"), false);
            return 0;
        }

        ctx.getSource().sendSuccess(() ->
                Component.literal("§6=== 触发器列表 (§e" + triggers.size() + "§6) ==="), false);
        for (StoryTrigger t : triggers) {
            ctx.getSource().sendSuccess(() ->
                    Component.literal("  " + t.toString()), false);
        }
        return triggers.size();
    }

    private static int executeTriggerRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TriggerManager tm = TriggerManager.getInstance(player.serverLevel());
        if (tm.getTrigger(name) == null) {
            ctx.getSource().sendFailure(Component.literal("§c触发器 '" + name + "' 不存在"));
            return 0;
        }
        tm.removeTrigger(name);
        ctx.getSource().sendSuccess(() ->
                Component.literal("§c已移除触发器: " + name), true);
        return 1;
    }

    private static int executeTriggerAddZone(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        String script = StringArgumentType.getString(ctx, "script");
        int x1 = IntegerArgumentType.getInteger(ctx, "x1");
        int z1 = IntegerArgumentType.getInteger(ctx, "z1");
        int x2 = IntegerArgumentType.getInteger(ctx, "x2");
        int z2 = IntegerArgumentType.getInteger(ctx, "z2");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TriggerManager tm = TriggerManager.getInstance(player.serverLevel());
        tm.addTrigger(StoryTrigger.zoneTrigger(name, script, x1, z1, x2, z2));
        ctx.getSource().sendSuccess(() ->
                Component.literal("§a✔ 已添加区域触发器: §e" + name
                        + "§7 [" + x1 + "," + z1 + "] → [" + x2 + "," + z2 + "] §7→ §e" + script), true);
        return 1;
    }

    private static int executeTriggerAddNpc(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        String script = StringArgumentType.getString(ctx, "script");
        String entity = StringArgumentType.getString(ctx, "entity");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String fullId = entity.contains(":") ? entity : "minecraft:" + entity;
        TriggerManager tm = TriggerManager.getInstance(player.serverLevel());
        tm.addTrigger(StoryTrigger.npcTrigger(name, script, fullId));
        ctx.getSource().sendSuccess(() ->
                Component.literal("§a✔ 已添加 NPC 触发器: §e" + name
                        + "§7 (" + fullId + ") → §e" + script), true);
        return 1;
    }

    private static int executeTriggerAddItem(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        String script = StringArgumentType.getString(ctx, "script");
        String item = StringArgumentType.getString(ctx, "item");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String fullId = item.contains(":") ? item : "minecraft:" + item;
        TriggerManager tm = TriggerManager.getInstance(player.serverLevel());
        tm.addTrigger(StoryTrigger.itemTrigger(name, script, fullId));
        ctx.getSource().sendSuccess(() ->
                Component.literal("§a✔ 已添加物品触发器: §e" + name
                        + "§7 (" + fullId + ") → §e" + script), true);
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

    // ==================== Quest 命令 ====================

    /**
     * 列出当前活跃的 Quest
     */
    private static int executeQuestList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        QuestManager qm = QuestManager.getInstance(player.serverLevel());
        List<RuntimeQuest> quests = qm.getActiveQuests(player);

        if (quests.isEmpty()) {
            ctx.getSource().sendSuccess(() ->
                    Component.literal("§7当前没有活跃的 Quest"), false);
            return 0;
        }

        ctx.getSource().sendSuccess(() ->
                Component.literal("§6=== 活跃 Quest (§e" + quests.size() + "§6) ==="), false);
        for (RuntimeQuest q : quests) {
            if (q.getState() != RuntimeQuest.QuestState.ACTIVE) continue;
            ctx.getSource().sendSuccess(() ->
                    Component.literal("  §6✦ §e" + q.getQuestName()
                            + " §7(" + q.getQuestId() + ")"), false);
            int done = (int) q.getObjectives().stream().filter(RuntimeObjective::isCompleted).count();
            ctx.getSource().sendSuccess(() ->
                    Component.literal("    §7进度: §e" + done + "§7/§e" + q.getObjectives().size()), false);
        }
        return quests.size();
    }

    /**
     * 放弃一个活跃的 Quest
     */
    private static int executeQuestAbort(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String questId = StringArgumentType.getString(ctx, "questId");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        QuestManager qm = QuestManager.getInstance(player.serverLevel());

        if (qm.failQuest(player, questId)) {
            ctx.getSource().sendSuccess(() ->
                    Component.literal("§c✔ 已放弃 Quest: " + questId), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal("§c未找到活跃的 Quest: " + questId));
            return 0;
        }
    }

    // ==================== 补全 ====================

    private static CompletableFuture<Suggestions> suggestChoices(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            StoryEngine engine = StoryEngine.getInstance(player.serverLevel());
            if (!engine.isInStory(player)) return Suggestions.empty();
            StoryNode node = engine.getCurrentNode(player);
            if (node == null || node.getOptions() == null) return Suggestions.empty();
            for (int i = 0; i < node.getOptions().size(); i++) {
                builder.suggest(String.valueOf(i), Component.literal(node.getOptions().get(i).getLabel()));
            }
        } catch (Exception ignored) {}
        return builder.buildFuture();
    }


    private static int executeChooseReward(CommandContext<CommandSourceStack> ctx) {
        String key = StringArgumentType.getString(ctx, "key");
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            QuestManager.getInstance(player.serverLevel()).deliverChoiceReward(player, key);
        } catch (CommandSyntaxException e) {}
        return 1;
    }

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

    private static CompletableFuture<Suggestions> suggestActiveQuests(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            QuestManager qm = QuestManager.getInstance(player.serverLevel());
            return SharedSuggestionProvider.suggest(
                    qm.getActiveQuests(player).stream()
                            .filter(q -> q.getState() == RuntimeQuest.QuestState.ACTIVE)
                            .map(RuntimeQuest::getQuestId), builder);
        } catch (CommandSyntaxException e) {
            return Suggestions.empty();
        }
    }

    private static CompletableFuture<Suggestions> suggestTriggers(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            TriggerManager tm = TriggerManager.getInstance(player.serverLevel());
            return SharedSuggestionProvider.suggest(
                    tm.getAllTriggers().stream().map(StoryTrigger::getId), builder);
        } catch (CommandSyntaxException e) {
            return Suggestions.empty();
        }
    }
}
