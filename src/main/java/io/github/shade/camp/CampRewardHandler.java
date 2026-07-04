package io.github.shade.camp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

/**
 * 宝箱生成和奖励逻辑
 * 据点清空后在指定位置生成宝箱，包含战利品表内容
 */
public class CampRewardHandler {

    private CampRewardHandler() {}

    /**
     * 在据点位置生成奖励宝箱
     *
     * @param world 服务端世界
     * @param camp  据点对象
     */
    public static void spawnRewardChest(ServerLevel world, Camp camp) {
        BlockPos chestPos = camp.getChestBlockPos();

        // 确保宝箱位置安全（如果是空中，落到地面）
        chestPos = findSafePlacement(world, chestPos);

        // 设置宝箱方块
        world.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);

        // 设置战利品表
        BlockEntity blockEntity = world.getBlockEntity(chestPos);
        if (blockEntity instanceof ChestBlockEntity chest) {
            ResourceLocation lootTableLocation = ResourceLocation.parse(camp.getLootTable());
            ResourceKey<LootTable> lootTableKey = ResourceKey.create(Registries.LOOT_TABLE, lootTableLocation);
            chest.setLootTable(lootTableKey, world.random.nextLong());
        }

        // 播放粒子效果
        playClearEffects(world, Vec3.atCenterOf(chestPos));

        // 更新宝箱位置记录
        camp.setChestBlockPos(chestPos);
    }

    /**
     * 播放清空效果（粒子 + 音效）
     */
    public static void playClearEffects(ServerLevel world, Vec3 pos) {
        // 粒子效果：绿色星光爆炸
        world.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                pos.x, pos.y + 1.0, pos.z,
                30,                    // 数量
                1.5, 0.5, 1.5,         // 扩散范围
                0.5                    // 速度
        );

        // 粒子效果：经验球环绕
        world.sendParticles(
                ParticleTypes.END_ROD,
                pos.x, pos.y + 1.5, pos.z,
                15,
                1.0, 0.3, 1.0,
                0.2
        );

        // 音效
        world.playSound(
                null,
                pos.x, pos.y, pos.z,
                SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS,
                1.0f, 0.8f
        );

        world.playSound(
                null,
                pos.x, pos.y, pos.z,
                SoundEvents.CHEST_OPEN,
                SoundSource.BLOCKS,
                0.8f, 1.2f
        );
    }

    /**
     * 播放激活效果（怪物出现时）
     */
    public static void playActivationEffects(ServerLevel world, Vec3 pos) {
        // 紫色烟雾
        world.sendParticles(
                ParticleTypes.SMOKE,
                pos.x, pos.y + 0.5, pos.z,
                20,
                2.0, 0.5, 2.0,
                0.05
        );

        // 警示音效
        world.playSound(
                null,
                pos.x, pos.y, pos.z,
                SoundEvents.ZOMBIE_AMBIENT,
                SoundSource.HOSTILE,
                0.5f, 0.8f
        );
    }

    /**
     * 销毁宝箱并标记
     */
    public static void removeChest(ServerLevel world, Camp camp) {
        BlockPos chestPos = camp.getChestBlockPos();
        if (chestPos != null) {
            world.destroyBlock(chestPos, false);
        }
    }

    /**
     * 找到一个安全的地面位置放置宝箱
     */
    private static BlockPos findSafePlacement(ServerLevel world, BlockPos pos) {
        // 如果宝箱位置是空气，向下找到地面
        if (world.getBlockState(pos).isAir()) {
            BlockPos ground = pos.below();
            while (ground.getY() > world.getMinBuildHeight()
                    && world.getBlockState(ground).isAir()
                    && !world.getBlockState(ground.below()).isAir()) {
                ground = ground.below();
            }
            // 检查地面是否存在
            if (!world.getBlockState(ground).isAir()) {
                return ground.above();
            }
        }
        // 如果当前位置被占用，尝试向上找空间
        if (!canPlaceChest(world, pos)) {
            for (int i = 1; i <= 5; i++) {
                BlockPos above = pos.above(i);
                if (canPlaceChest(world, above)) {
                    return above;
                }
            }
        }
        return pos;
    }

    /**
     * 检查是否可放置宝箱
     */
    private static boolean canPlaceChest(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }
}
