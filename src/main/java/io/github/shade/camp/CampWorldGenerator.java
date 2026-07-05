package io.github.shade.camp;

import io.github.shade.ShadeMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 自动据点生成器
 */
public class CampWorldGenerator {

    private CampWorldGenerator() {}

    public static final String GENERATION_FLAG_PREFIX = "auto_";
    private static final long SEED_OFFSET = 0xCAFE_BABE_DEAD_BEEFL;
    private static int nameId = 0;

    /**
     * 生成候选据点位置
     */
    public static List<BlockPos> generateCandidates(ServerLevel world) {
        long seed = world.getSeed();
        List<BlockPos> candidates = new ArrayList<>();
        ShadeMod.LOGGER.info("[shadecamp] === 生成候选 ===");

        Random random = new Random(seed ^ SEED_OFFSET);

        int[] distances = {100, 130, 160, 190, 220, 250, 300, 350, 400};

        for (int dist : distances) {
            for (int q = 0; q < 4; q++) {
                if (random.nextDouble() > 0.50) continue;

                double angle = q * (Math.PI / 2) + (random.nextDouble() - 0.5) * (Math.PI / 3);
                int x = (int) Math.round(Math.cos(angle) * dist) + random.nextInt(21) - 10;
                int z = (int) Math.round(Math.sin(angle) * dist) + random.nextInt(21) - 10;

                if (x * x + z * z < 80 * 80) continue;

                candidates.add(new BlockPos(x, 64, z));
            }
        }

        ShadeMod.LOGGER.info("[shadecamp] 候选: {} 个", candidates.size());
        return candidates;
    }

    /**
     * 在区块加载后创建据点
     */
    public static Camp finalizeCamp(ServerLevel world, BlockPos pending, CampManager manager) {
        if (!world.isLoaded(pending)) return null;

        int x = pending.getX();
        int z = pending.getZ();
        int surfaceY = world.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        if (surfaceY <= world.getMinBuildHeight() + 1) return null;

        BlockPos pos = new BlockPos(x, surfaceY + 1, z);

        // 过滤明显不能生成的位置（水里、岩浆里）
        var ground = world.getBlockState(pos.below());
        if (ground.is(net.minecraft.world.level.block.Blocks.WATER)
                || ground.is(net.minecraft.world.level.block.Blocks.LAVA)) return null;

        // 查找安全生成点（现在Y修好了，应该能正常工作）
        List<BlockPos> spawnPoints = CampSpawnValidator.findSafeSpawnPoints(world, pos, 10);
        if (spawnPoints.isEmpty()) {
            // 如果真没有，至少用中心点
            if (CampSpawnValidator.isPositionSafe(world, pos)) {
                spawnPoints.add(pos);
            } else {
                return null; // 中心都不安全就跳过
            }
        }

        // 生成名称
        nameId++;
        String[] pre = {"废弃", "破旧", "荒芜", "隐秘", "古老", "幽暗"};
        String name = pre[new Random(world.getSeed() + x * 31L + z * 37L).nextInt(pre.length)] + "营地" + nameId;

        Camp camp = new Camp(name, pos);
        camp.setSafeSpawnPointsFromBlocks(spawnPoints);

        // === 随机怪物配置 ===
        var biome = world.getBiome(pos);
        List<String> mobPool = CampRandomizer.getMobPoolForBiome(biome);
        Random rand = new Random(world.getSeed() + pos.asLong());

        Map<String, Integer> mobConfig = new LinkedHashMap<>();
        int speciesCount = Math.min(1 + rand.nextInt(Math.min(3, mobPool.size())), mobPool.size());
        List<String> selected = new ArrayList<>(mobPool);
        java.util.Collections.shuffle(selected, rand);
        selected = selected.subList(0, speciesCount);

        int totalTarget = 3 + rand.nextInt(6); // 3~8 只
        int allocated = 0;
        for (int i = 0; i < selected.size(); i++) {
            int maxPerType = i == selected.size() - 1
                    ? totalTarget - allocated
                    : Math.min(3, totalTarget - allocated - (selected.size() - i - 1));
            if (maxPerType < 1) maxPerType = 1;
            int count = 1 + rand.nextInt(Math.min(3, maxPerType));
            mobConfig.put(selected.get(i), count);
            allocated += count;
        }
        camp.setMobConfig(mobConfig);

        ShadeMod.LOGGER.info("[shadecamp] ★ 据点 '{}' @ {} | {} 种怪物共 {} 只 | 生成点 {} 个",
                name, pos.toShortString(), mobConfig.size(),
                mobConfig.values().stream().mapToInt(Integer::intValue).sum(),
                spawnPoints.size());
        for (var e : mobConfig.entrySet()) {
            String id = e.getKey().replace("minecraft:", "");
            ShadeMod.LOGGER.info("[shadecamp]     {} ×{}", id, e.getValue());
        }

        manager.addCamp(camp);
        return camp;
    }

    public static void resetNameId() { nameId = 0; }
}
