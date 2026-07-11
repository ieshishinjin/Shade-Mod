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
            var players = server.getPlayerList().getPlayers();
            int wl = getLevelFromPlayers(players);
            int maxLvl = getMaxLevelFromPlayers(players);
            String prefix = "§6✦ WL" + wl + " §r";

            // 通过队伍前缀在 TAB 列表显示世界等级
            var scoreboard = world.getScoreboard();
            var team = scoreboard.getPlayerTeam("shade_wl");
            if (team == null) {
                team = scoreboard.addPlayerTeam("shade_wl");
                team.setColor(net.minecraft.ChatFormatting.GOLD);
                team.setNameTagVisibility(net.minecraft.world.scores.Team.Visibility.NEVER);
            }
            team.setPlayerPrefix(Component.literal(prefix));

            // 将所有在线玩家加入该队伍
            for (var player : players) {
                if (!team.getPlayers().contains(player.getScoreboardName())) {
                    scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
                }
            }

            // 同时发送 TAB 头部显示更大的标题（作为备用）
            Component header = Component.literal("§6✦ " + getName(wl) + " §7(最高 Lv." + maxLvl + ")");
            var packet = new net.minecraft.network.protocol.game.ClientboundTabListPacket(header, Component.literal("§8Shade Camp Mod"));
            for (var player : players) {
                player.connection.send(packet);
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
