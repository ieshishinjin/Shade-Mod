package io.github.shade.worldlevel;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public class WorldLevel {

    private WorldLevel() {}

    private static final int[] THRESHOLDS = { 0, 10, 20, 30, 40, 50, 60 };
    private static final String[] NAMES = {
            "§7✦ 世界等级 0", "§8✦ 世界等级 1", "§f✦ 世界等级 2",
            "§e✦ 世界等级 3", "§b✦ 世界等级 4", "§d✦ 世界等级 5", "§6✦ 世界等级 6"
    };

    public static int getLevel(ServerLevel world) {
        return getLevelFromPlayers(world.players());
    }

    public static int getLevelFromPlayers(java.util.List<ServerPlayer> players) {
        int max = 0;
        for (ServerPlayer p : players)
            if (p.experienceLevel > max) max = p.experienceLevel;
        for (int i = THRESHOLDS.length - 1; i >= 0; i--)
            if (max >= THRESHOLDS[i]) return i;
        return 0;
    }

    public static String getName(int wl) {
        return wl >= 0 && wl < NAMES.length ? NAMES[wl] : NAMES[0];
    }

    public static void applyScaling(LivingEntity mob, int wl) {
        if (wl <= 0) return;
        int inf = 1000000;
        if (wl >= 1) mob.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, inf, wl - 1, false, false));
        if (wl >= 2) mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, inf, wl - 2, false, false));
        if (wl >= 4) mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, inf, wl - 4, false, false));
        if (wl >= 6) mob.addEffect(new MobEffectInstance(MobEffects.REGENERATION, inf, 0, false, false));
    }

    public static void updateTabHeader(net.minecraft.server.MinecraftServer server, ServerLevel world) {
        try {
            var scoreboard = world.getScoreboard();

            // 找或创建计分板目标（用来替换 TAB 列表中的延迟图标）
            var obj = scoreboard.getObjective("shade_wl");
            if (obj == null) {
                obj = scoreboard.addObjective("shade_wl",
                    net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,
                    Component.literal("✦ 世界等级").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GOLD).withBold(true)),
                    net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER,
                    false, null);
                scoreboard.setDisplayObjective(net.minecraft.world.scores.DisplaySlot.LIST, obj);
            }

            // 更新标题和分数
            int wl = getLevelFromPlayers(server.getPlayerList().getPlayers());
            obj.setDisplayName(Component.literal("✦ " + getName(wl).replaceAll("§.", "") + " ✦")
                .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GOLD).withBold(true)));

            // 所有玩家名字旁显示世界等级数字（取代默认的延迟图标）
            for (var player : server.getPlayerList().getPlayers()) {
                var score = scoreboard.getOrCreatePlayerScore(
                    net.minecraft.world.scores.ScoreHolder.forNameOnly(player.getScoreboardName()), obj);
                score.set(wl);
            }
        } catch (Exception e) {
            io.github.shade.ShadeMod.LOGGER.error("[worldlevel] TAB 更新失败", e);
        }
    }

    public static int getMaxLevel(ServerLevel world) {
        return getMaxLevelFromPlayers(world.players());
    }

    public static int getMaxLevelFromPlayers(java.util.List<ServerPlayer> players) {
        int max = 0;
        for (ServerPlayer p : players)
            if (p.experienceLevel > max) max = p.experienceLevel;
        return max;
    }
}
