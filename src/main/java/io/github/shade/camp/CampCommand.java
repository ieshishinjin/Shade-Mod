package io.github.shade.camp;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;

/**
 * 据点命令系统
 * <p>
 * 提供 "/camp" 命令的所有子命令，管理员可用。
 */
public class CampCommand {

    private static final int ADMIN_LEVEL = 2; // 需要管理员权限

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("camp")
                        // 不设全局权限限制，基础命令所有人都能用
                        // 危险命令（delete/clearall）内部再检查权限

                        // ---- create ----
                        .then(literal("create")
                                .then(argument("name", word())
                                        .executes(CampCommand::executeCreate))
                        )

                        // ---- delete ----
                        .then(literal("delete")
                                .then(argument("name", word())
                                        .suggests(CampCommand::suggestCamps)
                                        .executes(CampCommand::executeDelete))
                        )

                        // ---- list ----
                        .then(literal("list")
                                .executes(CampCommand::executeList)
                        )

                        // ---- addmob ----
                        .then(literal("addmob")
                                .then(argument("name", word())
                                        .suggests(CampCommand::suggestCamps)
                                        .then(argument("entity", word())
                                                .suggests(CampCommand::suggestEntities)
                                                .then(argument("count", IntegerArgumentType.integer(1, 10))
                                                        .executes(CampCommand::executeAddMob))))
                        )

                        // ---- removemob ----
                        .then(literal("removemob")
                                .then(argument("name", word())
                                        .suggests(CampCommand::suggestCamps)
                                        .then(argument("entity", word())
                                                .suggests(CampCommand::suggestCampMobs)
                                                .executes(CampCommand::executeRemoveMob)))
                        )

                        // ---- setrange ----
                        .then(literal("setrange")
                                .then(argument("name", word())
                                        .suggests(CampCommand::suggestCamps)
                                        .then(argument("range", IntegerArgumentType.integer(5, 50))
                                                .executes(CampCommand::executeSetRange)))
                        )

                        // ---- setloot ----
                        .then(literal("setloot")
                                .then(argument("name", word())
                                        .suggests(CampCommand::suggestCamps)
                                        .then(argument("loottable", ResourceLocationArgument.id())
                                                .executes(CampCommand::executeSetLoot)))
                        )

                        // ---- reset ----
                        .then(literal("reset")
                                .then(argument("name", word())
                                        .suggests(CampCommand::suggestCamps)
                                        .executes(CampCommand::executeReset))
                        )

                        // ---- refresh ----
                        .then(literal("refresh")
                                .then(argument("name", word())
                                        .suggests(CampCommand::suggestCamps)
                                        .executes(CampCommand::executeRefresh))
                        )

                        // ---- check ----
                        .then(literal("check")
                                .then(argument("name", word())
                                        .suggests(CampCommand::suggestCamps)
                                        .executes(CampCommand::executeCheck))
                        )

                        // ---- clearall ----
                        .then(literal("clearall")
                                .executes(CampCommand::executeClearAll))
        );
    }

    // ==================== 命令执行 ====================

    private static int executeCreate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = getString(ctx, "name");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel world = player.serverLevel();
        CampManager manager = CampManager.getInstance(world);

        Camp existing = manager.getCamp(name);
        if (existing != null) {
            ctx.getSource().sendFailure(Component.literal("据点 '" + name + "' 已存在！"));
            return 0;
        }

        Camp camp = manager.createCamp(name, player.blockPosition());
        if (camp == null) {
            ctx.getSource().sendFailure(Component.literal("创建据点失败！"));
            return 0;
        }

        int spawnCount = camp.getSafeSpawnPoints() != null ? camp.getSafeSpawnPoints().size() : 0;
        String biomeName = CampRandomizer.getBiomeDisplayName(world.getBiome(player.blockPosition()));

        ctx.getSource().sendSuccess(() -> Component.literal(
                        "§a据点 '" + name + "' 已创建！"
                ), true);
        ctx.getSource().sendSuccess(() -> Component.literal(
                        "  §7生物群系: §f" + biomeName
                        + "  §7安全生成点: §f" + spawnCount + " 个"
                        + "  §7怪物配置: §f" + formatMobConfig(camp.getMobConfig())
                ), true);

        if (spawnCount == 0) {
            ctx.getSource().sendSuccess(() ->
                    Component.literal("  §e⚠ 未找到安全生成点，建议使用 /camp check 检查位置"), true);
        }

        return 1;
    }

    private static int executeDelete(CommandContext<CommandSourceStack> ctx) {
        if (!ctx.getSource().hasPermission(2)) {
            ctx.getSource().sendFailure(Component.literal("§c你没有权限删除据点，需要管理员权限"));
            return 0;
        }
        String name = getString(ctx, "name");
        ServerLevel world = ctx.getSource().getLevel();
        CampManager manager = CampManager.getInstance(world);

        Camp camp = manager.getCamp(name);
        if (camp == null) {
            ctx.getSource().sendFailure(Component.literal("据点 '" + name + "' 不存在！"));
            return 0;
        }

        manager.deleteCamp(name);
        ctx.getSource().sendSuccess(() -> Component.literal("§c据点 '" + name + "' 已删除"), true);
        return 1;
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx) {
        ServerLevel world = ctx.getSource().getLevel();
        CampManager manager = CampManager.getInstance(world);
        List<Camp> camps = manager.getAllCamps();

        if (camps.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7没有已创建的据点"), true);
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§6=== 据点列表 (§e" + camps.size() + "§6 个) ==="), false);

        for (Camp camp : camps) {
            BlockPos pos = camp.getBlockPos();
            String statusColor = switch (camp.getStatus()) {
                case IDLE -> "§a";      // 绿色
                case FIGHTING -> "§c";  // 红色
                case CLEARED -> "§7";   // 灰色
            };

            ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                    "  %s[%s] §f%s §7@ [%d, %d, %d] §7| 怪物: %s§7| 范围: %d",
                    statusColor, camp.getStatus().name(),
                    camp.getName(),
                    pos.getX(), pos.getY(), pos.getZ(),
                    formatMobConfig(camp.getMobConfig()),
                    camp.getTriggerRange()
            )), false);
        }

        return camps.size();
    }

    private static int executeAddMob(CommandContext<CommandSourceStack> ctx) {
        String name = getString(ctx, "name");
        String entity = getString(ctx, "entity");
        int count = IntegerArgumentType.getInteger(ctx, "count");

        ServerLevel world = ctx.getSource().getLevel();
        CampManager manager = CampManager.getInstance(world);
        Camp camp = manager.getCamp(name);

        if (camp == null) {
            ctx.getSource().sendFailure(Component.literal("据点 '" + name + "' 不存在！"));
            return 0;
        }

        // 验证实体类型是否存在
        String fullId = entity.contains(":") ? entity : "minecraft:" + entity;
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(fullId))) {
            ctx.getSource().sendFailure(Component.literal("未知实体类型: " + fullId));
            return 0;
        }

        camp.getMobConfig().merge(fullId, count, Integer::sum);
        manager.save();

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已添加 " + fullId + " ×" + count + " 到据点 '" + name + "'"
        ), true);
        return 1;
    }

    private static int executeRemoveMob(CommandContext<CommandSourceStack> ctx) {
        String name = getString(ctx, "name");
        String entity = getString(ctx, "entity");

        ServerLevel world = ctx.getSource().getLevel();
        CampManager manager = CampManager.getInstance(world);
        Camp camp = manager.getCamp(name);

        if (camp == null) {
            ctx.getSource().sendFailure(Component.literal("据点 '" + name + "' 不存在！"));
            return 0;
        }

        String fullId = entity.contains(":") ? entity : "minecraft:" + entity;
        Integer removed = camp.getMobConfig().remove(fullId);
        if (removed == null) {
            ctx.getSource().sendFailure(Component.literal("据点中未找到 " + fullId));
            return 0;
        }

        manager.save();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已从据点 '" + name + "' 移除 " + fullId + " ×" + removed
        ), true);
        return 1;
    }

    private static int executeSetRange(CommandContext<CommandSourceStack> ctx) {
        String name = getString(ctx, "name");
        int range = IntegerArgumentType.getInteger(ctx, "range");

        ServerLevel world = ctx.getSource().getLevel();
        CampManager manager = CampManager.getInstance(world);
        Camp camp = manager.getCamp(name);

        if (camp == null) {
            ctx.getSource().sendFailure(Component.literal("据点 '" + name + "' 不存在！"));
            return 0;
        }

        camp.setTriggerRange(range);
        manager.save();

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a据点 '" + name + "' 触发范围已设置为 " + range + " 格"
        ), true);
        return 1;
    }

    private static int executeSetLoot(CommandContext<CommandSourceStack> ctx) {
        String name = getString(ctx, "name");
        ResourceLocation lootTable = ResourceLocationArgument.getId(ctx, "loottable");

        ServerLevel world = ctx.getSource().getLevel();
        CampManager manager = CampManager.getInstance(world);
        Camp camp = manager.getCamp(name);

        if (camp == null) {
            ctx.getSource().sendFailure(Component.literal("据点 '" + name + "' 不存在！"));
            return 0;
        }

        camp.setLootTable(lootTable.toString());
        manager.save();

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a据点 '" + name + "' 战利品表已设置为 " + lootTable
        ), true);
        return 1;
    }

    private static int executeReset(CommandContext<CommandSourceStack> ctx) {
        String name = getString(ctx, "name");

        ServerLevel world = ctx.getSource().getLevel();
        CampManager manager = CampManager.getInstance(world);

        if (manager.getCamp(name) == null) {
            ctx.getSource().sendFailure(Component.literal("据点 '" + name + "' 不存在！"));
            return 0;
        }

        manager.resetCamp(name);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a据点 '" + name + "' 已重置（怪物和宝箱已刷新）"
        ), true);
        return 1;
    }

    private static int executeRefresh(CommandContext<CommandSourceStack> ctx) {
        String name = getString(ctx, "name");

        ServerLevel world = ctx.getSource().getLevel();
        CampManager manager = CampManager.getInstance(world);

        if (manager.getCamp(name) == null) {
            ctx.getSource().sendFailure(Component.literal("据点 '" + name + "' 不存在！"));
            return 0;
        }

        int count = manager.refreshCache(name);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a据点 '" + name + "' 安全生成点已刷新，当前 " + count + " 个安全位置"
        ), true);
        return 1;
    }

    private static int executeCheck(CommandContext<CommandSourceStack> ctx) {
        String name = getString(ctx, "name");

        ServerLevel world = ctx.getSource().getLevel();
        CampManager manager = CampManager.getInstance(world);
        CampManager.CheckResult result = manager.checkCamp(name);

        if (result == null) {
            ctx.getSource().sendFailure(Component.literal("据点 '" + name + "' 不存在！"));
            return 0;
        }

        boolean safe = result.isSafe();
        String color = safe ? "§a" : "§c";

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§6=== 据点安全检查: " + result.name() + " ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "  位置: §f[" + result.position().toShortString() + "]"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "  中心安全: " + (result.centerSafe() ? "§a✔" : "§c✘")), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "  安全生成点: " + color + result.safeSpawnCount() + " 个"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "  状态: §f" + result.status().name()), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "  怪物总数: §f" + result.totalMobs() + " 只"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "  评估: " + color + (safe ? "✔ 位置安全" : "✘ 位置不安全，建议使用 /camp refresh 重新计算")), false);

        return safe ? 1 : 0;
    }

    private static int executeClearAll(CommandContext<CommandSourceStack> ctx) {
        if (!ctx.getSource().hasPermission(2)) {
            ctx.getSource().sendFailure(Component.literal("§c你没有权限清除全部据点，需要管理员权限"));
            return 0;
        }
        ServerLevel world = ctx.getSource().getLevel();
        CampManager manager = CampManager.getInstance(world);

        int count = manager.getAllCamps().size();
        manager.clearAll();

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§c已清除全部 " + count + " 个据点"
        ), true);
        return 1;
    }

    // ==================== 参数补全 ====================

    private static CompletableFuture<Suggestions> suggestCamps(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        ServerLevel world = ctx.getSource().getLevel();
        CampManager manager = CampManager.getInstance(world);
        return SharedSuggestionProvider.suggest(
                manager.getAllCamps().stream()
                        .map(Camp::getName),
                builder
        );
    }

    private static CompletableFuture<Suggestions> suggestEntities(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        // 建议常用的敌对生物
        return SharedSuggestionProvider.suggest(List.of(
                "minecraft:zombie",
                "minecraft:skeleton",
                "minecraft:spider",
                "minecraft:creeper",
                "minecraft:husk",
                "minecraft:stray",
                "minecraft:slime",
                "minecraft:witch",
                "minecraft:phantom",
                "minecraft:enderman"
        ), builder);
    }

    private static CompletableFuture<Suggestions> suggestCampMobs(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String name = getString(ctx, "name");
        ServerLevel world = ctx.getSource().getLevel();
        CampManager manager = CampManager.getInstance(world);
        Camp camp = manager.getCamp(name);

        if (camp == null) {
            return Suggestions.empty();
        }

        return SharedSuggestionProvider.suggest(camp.getMobConfig().keySet(), builder);
    }

    // ==================== 工具方法 ====================

    private static String formatMobConfig(java.util.Map<String, Integer> config) {
        return config.entrySet().stream()
                .map(e -> {
                    String id = e.getKey();
                    // 简化显示：去掉 "minecraft:" 前缀
                    if (id.startsWith("minecraft:")) {
                        id = id.substring(10);
                    }
                    return "§e" + id + "§7×" + e.getValue();
                })
                .collect(Collectors.joining(" "));
    }
}
