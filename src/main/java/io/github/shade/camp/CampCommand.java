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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;

/** /camp 命令 — i18n */
public class CampCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("camp")
                        .then(literal("create")
                                .then(argument("name", word())
                                        .executes(CampCommand::executeCreate))
                        )
                        .then(literal("delete")
                                .then(argument("name", word())
                                        .suggests(CampCommand::suggestCamps)
                                        .executes(CampCommand::executeDelete))
                        )
                        .then(literal("list")
                                .executes(CampCommand::executeList)
                        )
                        .then(literal("addmob")
                                .then(argument("name", word())
                                        .suggests(CampCommand::suggestCamps)
                                        .then(argument("entity", word())
                                                .suggests(CampCommand::suggestEntities)
                                                .then(argument("count", IntegerArgumentType.integer(1, 10))
                                                        .executes(CampCommand::executeAddMob))))
                        )
                        .then(literal("removemob")
                                .then(argument("name", word())
                                        .suggests(CampCommand::suggestCamps)
                                        .then(argument("entity", word())
                                                .suggests(CampCommand::suggestCampMobs)
                                                .executes(CampCommand::executeRemoveMob)))
                        )
                        .then(literal("setrange")
                                .then(argument("name", word())
                                        .suggests(CampCommand::suggestCamps)
                                        .then(argument("range", IntegerArgumentType.integer(5, 50))
                                                .executes(CampCommand::executeSetRange)))
                        )
                        .then(literal("setloot")
                                .then(argument("name", word())
                                        .suggests(CampCommand::suggestCamps)
                                        .then(argument("loottable", ResourceLocationArgument.id())
                                                .executes(CampCommand::executeSetLoot)))
                        )
                        .then(literal("reset")
                                .then(argument("name", word())
                                        .suggests(CampCommand::suggestCamps)
                                        .executes(CampCommand::executeReset))
                        )
                        .then(literal("refresh")
                                .then(argument("name", word())
                                        .suggests(CampCommand::suggestCamps)
                                        .executes(CampCommand::executeRefresh))
                        )
                        .then(literal("check")
                                .then(argument("name", word())
                                        .suggests(CampCommand::suggestCamps)
                                        .executes(CampCommand::executeCheck))
                        )
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
            ctx.getSource().sendFailure(Component.translatable("shadecamp.camp.create.exists", name));
            return 0;
        }

        Camp camp = manager.createCamp(name, player.blockPosition());
        if (camp == null) {
            ctx.getSource().sendFailure(Component.translatable("shadecamp.camp.create.failed"));
            return 0;
        }

        int spawnCount = camp.getSafeSpawnPoints() != null ? camp.getSafeSpawnPoints().size() : 0;
        String biomeName = CampRandomizer.getBiomeDisplayName(world.getBiome(player.blockPosition()));

        ctx.getSource().sendSuccess(() ->
                Component.translatable("shadecamp.camp.create.success", name), true);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("shadecamp.camp.create.info", biomeName, spawnCount, formatMobConfig(camp.getMobConfig())), true);

        if (spawnCount == 0) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("shadecamp.camp.create.nowarning"), true);
        }
        return 1;
    }

    private static int executeDelete(CommandContext<CommandSourceStack> ctx) {
        if (!ctx.getSource().hasPermission(2)) {
            ctx.getSource().sendFailure(Component.translatable("shadecamp.camp.delete.noperm"));
            return 0;
        }
        String name = getString(ctx, "name");
        ServerLevel world = ctx.getSource().getLevel();
        CampManager manager = CampManager.getInstance(world);

        if (manager.getCamp(name) == null) {
            ctx.getSource().sendFailure(Component.translatable("shadecamp.camp.delete.notfound", name));
            return 0;
        }
        manager.deleteCamp(name);
        ctx.getSource().sendSuccess(() -> Component.translatable("shadecamp.camp.delete.success", name), true);
        return 1;
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx) {
        ServerLevel world = ctx.getSource().getLevel();
        CampManager manager = CampManager.getInstance(world);
        List<Camp> camps = manager.getAllCamps();
        int pendingCount = manager.getPendingCampCount();

        if (camps.isEmpty() && pendingCount == 0) {
            ctx.getSource().sendSuccess(() -> Component.translatable("shadecamp.camp.list.none"), false);
            return 0;
        }

        boolean hasPending = pendingCount > 0;
        ctx.getSource().sendSuccess(() ->
                hasPending
                        ? Component.translatable("shadecamp.camp.list.header", camps.size(), pendingCount)
                        : Component.translatable("shadecamp.camp.list.header.simple", camps.size()),
                false);

        for (Camp camp : camps) {
            BlockPos pos = camp.getBlockPos();
            String statusKey = "shadecamp.status." + camp.getStatus().name();
            String statusColor = switch (camp.getStatus()) {
                case IDLE -> "§a";
                case FIGHTING -> "§c";
                case CLEARED -> "§7";
            };
            String finalStatusColor = statusColor;
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("shadecamp.camp.list.entry",
                            finalStatusColor,
                            Component.translatable(statusKey).getString(),
                            camp.getName(),
                            pos.getX(), pos.getY(), pos.getZ(),
                            formatMobConfig(camp.getMobConfig()),
                            camp.getTriggerRange()
                    ), false);
        }

        if (hasPending) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("shadecamp.camp.list.pending", pendingCount), false);
        }
        return camps.size() + pendingCount;
    }

    private static int executeAddMob(CommandContext<CommandSourceStack> ctx) {
        String name = getString(ctx, "name");
        String entity = getString(ctx, "entity");
        int count = IntegerArgumentType.getInteger(ctx, "count");

        ServerLevel world = ctx.getSource().getLevel();
        CampManager manager = CampManager.getInstance(world);
        Camp camp = manager.getCamp(name);
        if (camp == null) {
            ctx.getSource().sendFailure(Component.translatable("shadecamp.camp.delete.notfound", name));
            return 0;
        }

        String fullId = entity.contains(":") ? entity : "minecraft:" + entity;
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(fullId))) {
            ctx.getSource().sendFailure(Component.translatable("shadecamp.camp.addmob.invalid", fullId));
            return 0;
        }

        camp.getMobConfig().merge(fullId, count, Integer::sum);
        manager.save();
        ctx.getSource().sendSuccess(() ->
                Component.translatable("shadecamp.camp.addmob.success", fullId, count, name), true);
        return 1;
    }

    private static int executeRemoveMob(CommandContext<CommandSourceStack> ctx) {
        String name = getString(ctx, "name");
        String entity = getString(ctx, "entity");

        ServerLevel world = ctx.getSource().getLevel();
        CampManager manager = CampManager.getInstance(world);
        Camp camp = manager.getCamp(name);
        if (camp == null) {
            ctx.getSource().sendFailure(Component.translatable("shadecamp.camp.delete.notfound", name));
            return 0;
        }

        String fullId = entity.contains(":") ? entity : "minecraft:" + entity;
        Integer removed = camp.getMobConfig().remove(fullId);
        if (removed == null) {
            ctx.getSource().sendFailure(Component.translatable("shadecamp.camp.removemob.notfound", fullId));
            return 0;
        }
        manager.save();
        ctx.getSource().sendSuccess(() ->
                Component.translatable("shadecamp.camp.removemob.success", name, fullId, removed), true);
        return 1;
    }

    private static int executeSetRange(CommandContext<CommandSourceStack> ctx) {
        String name = getString(ctx, "name");
        int range = IntegerArgumentType.getInteger(ctx, "range");

        ServerLevel world = ctx.getSource().getLevel();
        CampManager manager = CampManager.getInstance(world);
        Camp camp = manager.getCamp(name);
        if (camp == null) {
            ctx.getSource().sendFailure(Component.translatable("shadecamp.camp.delete.notfound", name));
            return 0;
        }
        camp.setTriggerRange(range);
        manager.save();
        ctx.getSource().sendSuccess(() ->
                Component.translatable("shadecamp.camp.setrange.success", name, range), true);
        return 1;
    }

    private static int executeSetLoot(CommandContext<CommandSourceStack> ctx) {
        String name = getString(ctx, "name");
        ResourceLocation lootTable = ResourceLocationArgument.getId(ctx, "loottable");

        ServerLevel world = ctx.getSource().getLevel();
        CampManager manager = CampManager.getInstance(world);
        Camp camp = manager.getCamp(name);
        if (camp == null) {
            ctx.getSource().sendFailure(Component.translatable("shadecamp.camp.delete.notfound", name));
            return 0;
        }
        camp.setLootTable(lootTable.toString());
        manager.save();
        ctx.getSource().sendSuccess(() ->
                Component.translatable("shadecamp.camp.setloot.success", name, lootTable), true);
        return 1;
    }

    private static int executeReset(CommandContext<CommandSourceStack> ctx) {
        String name = getString(ctx, "name");
        ServerLevel world = ctx.getSource().getLevel();
        CampManager manager = CampManager.getInstance(world);
        if (manager.getCamp(name) == null) {
            ctx.getSource().sendFailure(Component.translatable("shadecamp.camp.delete.notfound", name));
            return 0;
        }
        manager.resetCamp(name);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("shadecamp.camp.reset.success", name), true);
        return 1;
    }

    private static int executeRefresh(CommandContext<CommandSourceStack> ctx) {
        String name = getString(ctx, "name");
        ServerLevel world = ctx.getSource().getLevel();
        CampManager manager = CampManager.getInstance(world);
        if (manager.getCamp(name) == null) {
            ctx.getSource().sendFailure(Component.translatable("shadecamp.camp.delete.notfound", name));
            return 0;
        }
        int count = manager.refreshCache(name);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("shadecamp.camp.refresh.success", name, count), true);
        return 1;
    }

    private static int executeCheck(CommandContext<CommandSourceStack> ctx) {
        String name = getString(ctx, "name");
        ServerLevel world = ctx.getSource().getLevel();
        CampManager manager = CampManager.getInstance(world);
        CampManager.CheckResult result = manager.checkCamp(name);

        if (result == null) {
            ctx.getSource().sendFailure(Component.translatable("shadecamp.camp.delete.notfound", name));
            return 0;
        }

        boolean safe = result.isSafe();
        ctx.getSource().sendSuccess(() ->
                Component.translatable("shadecamp.camp.check.header", result.name()), false);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("shadecamp.camp.check.pos",
                        result.position().getX(), result.position().getY(), result.position().getZ()), false);
        ctx.getSource().sendSuccess(() ->
                Component.translatable(result.centerSafe()
                        ? "shadecamp.camp.check.safe" : "shadecamp.camp.check.unsafe"), false);
        ctx.getSource().sendSuccess(() ->
                Component.translatable(result.safeSpawnCount() > 0
                        ? "shadecamp.camp.check.spawns" : "shadecamp.camp.check.spawns.none",
                        result.safeSpawnCount()), false);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("shadecamp.camp.check.status", result.status().name()), false);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("shadecamp.camp.check.mobs", result.totalMobs()), false);
        ctx.getSource().sendSuccess(() ->
                Component.translatable(safe
                        ? "shadecamp.camp.check.result.ok" : "shadecamp.camp.check.result.bad"), false);
        return safe ? 1 : 0;
    }



    private static int executeClearAll(CommandContext<CommandSourceStack> ctx) {
        if (!ctx.getSource().hasPermission(2)) {
            ctx.getSource().sendFailure(Component.translatable("shadecamp.camp.clearall.noperm"));
            return 0;
        }
        ServerLevel world = ctx.getSource().getLevel();
        CampManager manager = CampManager.getInstance(world);
        int count = manager.getAllCamps().size();
        manager.clearAll();
        ctx.getSource().sendSuccess(() ->
                Component.translatable("shadecamp.camp.clearall.success", count), true);
        return 1;
    }

    // ==================== 参数补全 ====================

    private static CompletableFuture<Suggestions> suggestCamps(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        ServerLevel world = ctx.getSource().getLevel();
        CampManager manager = CampManager.getInstance(world);
        return SharedSuggestionProvider.suggest(
                manager.getAllCamps().stream().map(Camp::getName), builder);
    }

    private static CompletableFuture<Suggestions> suggestEntities(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.of(
                "minecraft:zombie", "minecraft:skeleton", "minecraft:spider",
                "minecraft:creeper", "minecraft:husk", "minecraft:stray",
                "minecraft:slime", "minecraft:witch", "minecraft:phantom",
                "minecraft:enderman"
        ), builder);
    }

    private static CompletableFuture<Suggestions> suggestCampMobs(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String name = getString(ctx, "name");
        ServerLevel world = ctx.getSource().getLevel();
        CampManager manager = CampManager.getInstance(world);
        Camp camp = manager.getCamp(name);
        if (camp == null) return Suggestions.empty();
        return SharedSuggestionProvider.suggest(camp.getMobConfig().keySet(), builder);
    }

    // ==================== 工具方法 ====================

    /** 格式化怪物配置为显示文字，实体名使用 translatable */
    private static String formatMobConfig(java.util.Map<String, Integer> config) {
        return config.entrySet().stream()
                .map(e -> {
                    String id = e.getKey();
                    String shortId = id.startsWith("minecraft:") ? id.substring(10) : id;
                    // 尝试使用 translatable 实体名称，失败则用短 ID
                    return "§e" + shortId + "§7×" + e.getValue();
                })
                .collect(Collectors.joining(" "));
    }
}
