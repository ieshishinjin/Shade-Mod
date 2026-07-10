package io.github.shade.worldlevel;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import static net.minecraft.commands.Commands.literal;

/**
 * /worldlevel 命令 — 查看当前世界等级
 */
public class WorldLevelCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("worldlevel")
                        .executes(WorldLevelCommand::execute)
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        ServerLevel world = ctx.getSource().getLevel();
        int wl = WorldLevel.getLevel(world);
        int maxP = WorldLevel.getMaxPlayerLevel(world);

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§d✦ 世界等级: WL." + wl + "  §7(最高玩家等级: " + maxP + ")"
        ), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "  §7怪物血量: §f+" + (wl * 20) + "%  §7宝箱品质: §f" + WorldLevel.chestTierName(wl)
        ), false);
        return 1;
    }
}
