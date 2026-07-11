package io.github.shade.story.gallery;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Set;

import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;

/**
 * /story gallery 命令 — CG 画廊和结局收集系统
 *
 * 命令列表：
 *   /story gallery                — 画廊首页（进度概览）
 *   /story gallery list           — 列出所有条目
 *   /story gallery cg             — 仅显示 CG
 *   /story gallery endings        — 仅显示结局
 *   /story gallery view <id>      — 查看条目详情
 */
public class GalleryCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("story")
                        .then(literal("gallery")
                                .executes(GalleryCommand::executeOverview)
                                .then(literal("list")
                                        .executes(GalleryCommand::executeListAll))
                                .then(literal("cg")
                                        .executes(GalleryCommand::executeListCg))
                                .then(literal("endings")
                                        .executes(GalleryCommand::executeListEndings))
                                .then(literal("view")
                                        .then(argument("id", StringArgumentType.word())
                                                .executes(GalleryCommand::executeView)))
                        )
        );
    }

    private static int executeOverview(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        GalleryManager mgr = GalleryManager.getInstance(player.serverLevel());
        var data = mgr.getDisplayData(player);

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§6✦ 画廊 §7(" + mgr.getUnlockedCount(player) + "/" + mgr.getTotalCount() + ")"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "  §bCG: §f" + countUnlocked(data.cgs(), data.unlockedIds())
                        + "§7/§f" + data.cgs().size()), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "  §d结局: §f" + countUnlocked(data.endings(), data.unlockedIds())
                        + "§7/§f" + data.endings().size()), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "  §7完成度: §f" + (mgr.getTotalCount() > 0
                        ? (mgr.getUnlockedCount(player) * 100 / mgr.getTotalCount()) : 0) + "%"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(""), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§7使用 §e/story gallery list§7 查看全部"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§7使用 §e/story gallery view <id>§7 查看详情"), false);
        return 1;
    }

    private static int executeListAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return listEntries(ctx, null);
    }

    private static int executeListCg(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return listEntries(ctx, "CG");
    }

    private static int executeListEndings(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return listEntries(ctx, "ENDING");
    }

    private static int listEntries(CommandContext<CommandSourceStack> ctx, String filterType)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        GalleryManager mgr = GalleryManager.getInstance(player.serverLevel());
        var entries = mgr.getAllEntries();

        String title = filterType == null ? "全部" : filterType.equals("CG") ? "CG" : "结局";
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§6=== 画廊 — " + title + " ==="), false);

        for (GalleryEntry entry : entries) {
            if (filterType != null && !filterType.equals(entry.getType())) continue;
            boolean unlocked = mgr.isUnlocked(player, entry.getId());
            ctx.getSource().sendSuccess(() -> Component.literal(
                    entry.getDisplayLine(unlocked)), false);
        }
        return entries.size();
    }

    private static int executeView(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "id");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        GalleryManager mgr = GalleryManager.getInstance(player.serverLevel());
        GalleryEntry entry = mgr.getEntry(id);

        if (entry == null) {
            ctx.getSource().sendFailure(Component.literal("§c画廊条目 '" + id + "' 不存在"));
            return 0;
        }

        boolean unlocked = mgr.isUnlocked(player, id);
        String[] lines = entry.getDetailLines(unlocked).split("\n");
        for (String line : lines) {
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int countUnlocked(List<GalleryEntry> entries, Set<String> unlockedIds) {
        return (int) entries.stream().filter(e -> unlockedIds.contains(e.getId())).count();
    }
}
