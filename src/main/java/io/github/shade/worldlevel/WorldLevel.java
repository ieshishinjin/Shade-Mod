package io.github.shade.worldlevel;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** 世界等级系统 — 受原神世界等级启发 */
public class WorldLevel {

    private WorldLevel() {}

    private static final float HP_PER_LEVEL = 0.20f;
    private static final int[] THRESHOLDS = { 0, 10, 20, 30, 40, 60, 80 };

    /**
     * 获取当前世界等级 (0~6)
     */
    public static int getLevel(ServerLevel world) {
        int maxLevel = 0;
        for (ServerPlayer player : world.players()) {
            if (player.experienceLevel > maxLevel) maxLevel = player.experienceLevel;
        }
        for (int i = THRESHOLDS.length - 1; i >= 0; i--) {
            if (maxLevel >= THRESHOLDS[i]) return i;
        }
        return 0;
    }

    /**
     * 获取最高玩家经验等级
     */
    public static int getMaxPlayerLevel(ServerLevel world) {
        int max = 0;
        for (ServerPlayer p : world.players()) {
            if (p.experienceLevel > max) max = p.experienceLevel;
        }
        return max;
    }

    /**
     * 给怪物施加等级增益效果
     */
    public static void applyScaling(LivingEntity mob, int worldLevel) {
        if (worldLevel <= 0) return;
        int inf = 1000000;

        if (worldLevel >= 1)
            mob.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, inf, worldLevel - 1, false, false));
        if (worldLevel >= 2)
            mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, inf, worldLevel - 2, false, false));
        if (worldLevel >= 4)
            mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, inf, worldLevel - 4, false, false));
        if (worldLevel >= 6)
            mob.addEffect(new MobEffectInstance(MobEffects.REGENERATION, inf, 0, false, false));
    }

    public static String formatLevel(int wl) {
        return "§d✦ WL." + wl;
    }

    /**
     * 对应世界等级的推荐宝箱品质
     */
    public static String chestTierName(int wl) {
        if (wl >= 6) return "EPIC";
        if (wl >= 4) return "RARE";
        if (wl >= 2) return "UNCOMMON";
        if (wl >= 1) return "COMMON";
        return "BASIC";
    }
}
