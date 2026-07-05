package io.github.shade.camp;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于种子的安全位置分析算法
 * 模拟 Minecraft 寻找安全生成位置的算法，通过世界种子和坐标预判安全性。
 */
public class CampSpawnValidator {

    private CampSpawnValidator() {}

    // 安全检查半径
    private static final int AREA_RADIUS = 1;        // 3×3 区域
    private static final int CLIFF_CHECK_RADIUS = 2;  // 悬崖检测范围
    private static final int HEADSPACE_REQUIRED = 2;  // 头部空间格数

    /**
     * 查找据点周围的安全生成点
     *
     * @param world  服务端世界
     * @param center 据点中心
     * @param radius 搜索半径
     * @return 安全位置列表
     */
    public static List<BlockPos> findSafeSpawnPoints(ServerLevel world, BlockPos center, int radius) {
        List<BlockPos> safePoints = new ArrayList<>();
        Set<BlockPos> tested = new HashSet<>();

        // 从中心开始螺旋搜索
        for (int r = 0; r <= radius && safePoints.size() < 16; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    if (dx == 0 && dz == 0) continue;

                    BlockPos candidate = center.offset(dx, 0, dz);
                    if (!tested.add(candidate)) continue;

                    // 根据地表高度找到正确的 Y
                    int surfaceY = getSurfaceY(world, candidate.getX(), candidate.getZ());
                    if (surfaceY == -1) continue;

                    candidate = new BlockPos(candidate.getX(), surfaceY + 1, candidate.getZ());

                    if (isPositionSafe(world, candidate)) {
                        safePoints.add(candidate);
                        if (safePoints.size() >= 16) break;
                    }
                }
                if (safePoints.size() >= 16) break;
            }
        }

        // 如果搜索半径内没找到，测试中心点本身
        if (safePoints.isEmpty()) {
            int surfaceY = getSurfaceY(world, center.getX(), center.getZ());
            if (surfaceY != -1) {
                BlockPos centerStand = new BlockPos(center.getX(), surfaceY + 1, center.getZ());
                if (isPositionSafe(world, centerStand)) {
                    safePoints.add(centerStand);
                }
            }
        }

        return safePoints;
    }

    /**
     * 判断单个位置是否安全
     */
    public static boolean isPositionSafe(ServerLevel world, BlockPos pos) {
        // 1. 地面检查：脚下必须是完整固体方块
        BlockPos groundPos = pos.below();
        BlockState groundBlock = world.getBlockState(groundPos);
        if (!isValidGround(groundBlock)) {
            return false;
        }

        // 2. 头部空间：上方 N 格必须是空气/可替换方块
        for (int i = 0; i < HEADSPACE_REQUIRED; i++) {
            BlockState headBlock = world.getBlockState(pos.above(i));
            if (!headBlock.isAir() && !headBlock.canBeReplaced()) {
                return false;
            }
        }

        // 3. 活动空间：3×3 区域地面完整
        for (int dx = -AREA_RADIUS; dx <= AREA_RADIUS; dx++) {
            for (int dz = -AREA_RADIUS; dz <= AREA_RADIUS; dz++) {
                BlockPos checkPos = pos.offset(dx, -1, dz);
                if (!isValidGround(world.getBlockState(checkPos))) {
                    return false;
                }
                // 3×3×3 无障碍物
                for (int dy = 0; dy < HEADSPACE_REQUIRED; dy++) {
                    BlockState checkHead = world.getBlockState(pos.offset(dx, dy, dz));
                    if (!checkHead.isAir() && !checkHead.canBeReplaced()) {
                        return false;
                    }
                }
            }
        }

        // 4. 悬崖检查：周围 2 格内无突然落差
        for (int dx = -CLIFF_CHECK_RADIUS; dx <= CLIFF_CHECK_RADIUS; dx++) {
            for (int dz = -CLIFF_CHECK_RADIUS; dz <= CLIFF_CHECK_RADIUS; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos checkPos = pos.offset(dx, 0, dz);
                int checkY = getSurfaceY(world, checkPos.getX(), checkPos.getZ());
                if (checkY == -1) return false;
                // 落差超过 2 格视为不安全
                if (Math.abs(checkY - pos.getY()) > 2) {
                    return false;
                }
            }
        }

        // 5. 光照检查：光照等级 ≥ 7（避免怪物在阳光中灼烧）
        int skyLight = world.getBrightness(LightLayer.SKY, pos);
        int blockLight = world.getBrightness(LightLayer.BLOCK, pos);
        if (skyLight + blockLight < 7) {
            // 地表通常有足够天空光照，除非是夜晚
            // 但夜里生怪是合理的，所以只检查是否有完全无光的情况
            if (skyLight + blockLight < 1) {
                return false;
            }
        }

        return true;
    }

    /**
     * 统计安全生成点数量
     */
    public static int countSafeSpawnPoints(ServerLevel world, BlockPos center, int radius) {
        return findSafeSpawnPoints(world, center, radius).size();
    }

    /**
     * 获取地表的 Y 坐标（使用地表高度图）
     * 需要区块已加载，否则返回 -1
     */
    private static int getSurfaceY(ServerLevel world, int x, int z) {
        BlockPos checkPos = new BlockPos(x, 0, z);
        if (!world.isLoaded(checkPos)) return -1;
        int y = world.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        if (y <= world.getMinBuildHeight()) return -1;
        return y;
    }

    /**
     * 判断方块是否可作为站立地面
     */
    private static boolean isValidGround(BlockState state) {
        if (state.isAir()) return false;
        if (state.canBeReplaced()) return false;

        // 禁止站立的方块
        if (state.is(Blocks.WATER)) return false;
        if (state.is(Blocks.LAVA)) return false;
        if (state.is(Blocks.POWDER_SNOW)) return false;
        if (state.is(Blocks.COBWEB)) return false;
        if (state.is(BlockTags.FIRE)) return false;
        if (state.is(Blocks.MAGMA_BLOCK)) return false;
        if (state.is(Blocks.CACTUS)) return false;
        if (state.is(Blocks.SWEET_BERRY_BUSH)) return false;

        // 必须是完整方块（可以站在上面）
        return state.isSolid() && !state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED);
    }
}
