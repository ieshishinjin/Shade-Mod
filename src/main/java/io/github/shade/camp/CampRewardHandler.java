package io.github.shade.camp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

/**
 * 宝箱生成和奖励逻辑 — 按据点怪物数量分等级
 */
public class CampRewardHandler {

    private CampRewardHandler() {}

    /** 宝箱等级：根据据点怪物总数决定 */
    public enum ChestTier {
        COMMON   (3, 4,  Blocks.CHEST,       "shadecamp:chests/camp_common",   ParticleTypes.HAPPY_VILLAGER,  SoundEvents.PLAYER_LEVELUP,       20),
        UNCOMMON (5, 5,  Blocks.CHEST,       "shadecamp:chests/camp_uncommon", ParticleTypes.END_ROD,         SoundEvents.PLAYER_LEVELUP,       30),
        RARE     (6, 7,  Blocks.CHEST,       "shadecamp:chests/camp_rare",     ParticleTypes.END_ROD,         SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 40),
        EPIC     (8, 99, Blocks.ENDER_CHEST, "shadecamp:chests/camp_epic",     ParticleTypes.FLASH,           SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 60);

        final int minMobs;
        final int maxMobs;
        final Block chestBlock;
        final String lootTable;
        final SimpleParticleType particle;
        final SoundEvent sound;
        final int particleCount;

        ChestTier(int min, int max, Block block, String loot, SimpleParticleType p, SoundEvent s, int pc) {
            this.minMobs = min; this.maxMobs = max;
            this.chestBlock = block; this.lootTable = loot;
            this.particle = p; this.sound = s; this.particleCount = pc;
        }

        public static ChestTier forMobCount(int count) {
            for (ChestTier t : values()) {
                if (count >= t.minMobs && count <= t.maxMobs) return t;
            }
            return COMMON;
        }
    }

    /**
     * 在据点位置生成分级宝箱
     */
    public static void spawnRewardChest(ServerLevel world, Camp camp, ChestTier tier) {
        BlockPos chestPos = camp.getChestBlockPos();
        chestPos = findSafePlacement(world, chestPos);

        // 放置对应等级的宝箱方块
        world.setBlock(chestPos, tier.chestBlock.defaultBlockState(), 3);

        // 设置战利品表
        BlockEntity blockEntity = world.getBlockEntity(chestPos);
        if (blockEntity instanceof ChestBlockEntity chest) {
            ResourceLocation tableLoc = ResourceLocation.parse(tier.lootTable);
            ResourceKey<LootTable> tableKey = ResourceKey.create(Registries.LOOT_TABLE, tableLoc);
            chest.setLootTable(tableKey, world.random.nextLong());
        }

        // 播放分级特效
        playClearEffects(world, Vec3.atCenterOf(chestPos), tier);

        camp.setChestBlockPos(chestPos);
    }

    /**
     * 按等级播放清空效果
     */
    private static void playClearEffects(ServerLevel world, Vec3 pos, ChestTier tier) {
        // 粒子效果
        world.sendParticles(tier.particle, pos.x, pos.y + 1.0, pos.z,
                tier.particleCount, 1.5, 0.5, 1.5, 0.5);

        // 高级箱子额外加经验球环绕
        if (tier.ordinal() >= ChestTier.RARE.ordinal()) {
            world.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1.5, pos.z,
                    20, 1.0, 0.3, 1.0, 0.2);
        }

        // 史诗级箱子加音效和光柱
        if (tier == ChestTier.EPIC) {
            world.sendParticles(ParticleTypes.INSTANT_EFFECT, pos.x, pos.y + 2.0, pos.z,
                    50, 0.5, 1.0, 0.5, 0.5);
        }

        // 音效
        world.playSound(null, pos.x, pos.y, pos.z, tier.sound, SoundSource.PLAYERS, 1.0f, 0.8f);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.8f, 1.2f);
    }

    /** 旧版方法：向后兼容 */
    public static void spawnRewardChest(ServerLevel world, Camp camp) {
        spawnRewardChest(world, camp, ChestTier.forMobCount(camp.getTotalMobCount()));
    }

    public static void playClearEffects(ServerLevel world, Vec3 pos) {
        // No-op, use tiered version
    }

    public static void playActivationEffects(ServerLevel world, Vec3 pos) {
        world.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y + 0.5, pos.z, 20, 2.0, 0.5, 2.0, 0.05);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 0.5f, 0.8f);
    }

    public static void removeChest(ServerLevel world, Camp camp) {
        BlockPos chestPos = camp.getChestBlockPos();
        if (chestPos != null) world.destroyBlock(chestPos, false);
    }

    private static BlockPos findSafePlacement(ServerLevel world, BlockPos pos) {
        if (world.getBlockState(pos).isAir()) {
            BlockPos ground = pos.below();
            while (ground.getY() > world.getMinBuildHeight()
                    && world.getBlockState(ground).isAir()
                    && !world.getBlockState(ground.below()).isAir()) {
                ground = ground.below();
            }
            if (!world.getBlockState(ground).isAir()) return ground.above();
        }
        if (!canPlaceChest(world, pos)) {
            for (int i = 1; i <= 5; i++) {
                BlockPos above = pos.above(i);
                if (canPlaceChest(world, above)) return above;
            }
        }
        return pos;
    }

    private static boolean canPlaceChest(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }
}
