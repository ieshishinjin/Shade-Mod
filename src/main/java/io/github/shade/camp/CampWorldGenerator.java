package io.github.shade.camp;

import io.github.shade.ShadeMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.*;

/**
 * 自动据点生成器 — 跳过所有安全检查，区块加载后直接创建
 */
public class CampWorldGenerator {

    private CampWorldGenerator() {}

    public static final String GENERATION_FLAG_PREFIX = "auto_";

    public static List<BlockPos> generateCandidates(ServerLevel world) {
        // Handled in CampEventHandler, no separate method needed
        return new ArrayList<>();
    }

    /**
     * 区块加载后创建据点 — 跳过一切检查，强制生成
     */
    public static Camp finalizeCamp(ServerLevel world, BlockPos pending, CampManager manager) {
        if (!world.isLoaded(pending)) return null;

        int x = pending.getX();
        int z = pending.getZ();
        int surfaceY = world.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        if (surfaceY <= world.getMinBuildHeight() + 1) return null;

        BlockPos pos = new BlockPos(x, surfaceY + 1, z);

        // === 排斥水中的据点 ===
        var ground = world.getBlockState(pos.below());
        if (ground.is(net.minecraft.world.level.block.Blocks.WATER)
                || ground.is(net.minecraft.world.level.block.Blocks.LAVA)
                || ground.is(net.minecraft.world.level.block.Blocks.SEAGRASS)
                || ground.is(net.minecraft.world.level.block.Blocks.KELP)) return null;

        // 3×3 区域内不能有水
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (world.getBlockState(pos.offset(dx, -1, dz))
                        .is(net.minecraft.world.level.block.Blocks.WATER)) return null;
            }
        }

        // 生物群系过滤
        var biome = world.getBiome(pos);
        String biomeName = biome.unwrapKey().map(k -> k.location().getPath()).orElse("");
        if (biomeName.contains("ocean") || biomeName.contains("river")
                || biomeName.contains("swamp") || biomeName.contains("beach")
                || biomeName.contains("mushroom")) return null;

        // 检查与其他据点的距离（至少 30 格）
        for (Camp existing : manager.getAllCamps()) {
            BlockPos ep = existing.getBlockPos();
            int dx = ep.getX() - pos.getX();
            int dz = ep.getZ() - pos.getZ();
            if (dx * dx + dz * dz < 30 * 30) return null;
        }

        // 强制创建一个安全生成点（就是中心点）
        List<BlockPos> spawnPoints = new ArrayList<>();
        spawnPoints.add(pos);

        // 生成名称
        String[] pre = {"废弃", "破旧", "荒芜", "隐秘", "古老", "幽暗"};
        String name = pre[new Random(world.getSeed() + x * 31L + z * 37L).nextInt(pre.length)] + "营地";

        Camp camp = new Camp(name, pos);
        camp.setSafeSpawnPointsFromBlocks(spawnPoints);

        // 随机怪物配置（基于群系）
        List<String> mobPool = CampRandomizer.getMobPoolForBiome(biome);
        Random rand = new Random(world.getSeed() + pos.asLong());

        Map<String, Integer> mobConfig = new LinkedHashMap<>();
        int speciesCount = Math.min(1 + rand.nextInt(Math.min(3, mobPool.size())), mobPool.size());
        List<String> selected = new ArrayList<>(mobPool);
        Collections.shuffle(selected, rand);
        selected = selected.subList(0, speciesCount);

        int totalTarget = 3 + rand.nextInt(6);
        int allocated = 0;
        for (int i = 0; i < selected.size(); i++) {
            int max = i == selected.size() - 1 ? totalTarget - allocated
                    : Math.min(3, totalTarget - allocated - (selected.size() - i - 1));
            if (max < 1) max = 1;
            int count = 1 + rand.nextInt(Math.min(3, max));
            mobConfig.put(selected.get(i), count);
            allocated += count;
        }
        camp.setMobConfig(mobConfig);

        manager.addCamp(camp);
        ShadeMod.LOGGER.info("[shadecamp] ★ 据点 '{}' @ {} | {} 只怪物", name, pos.toShortString(),
                mobConfig.values().stream().mapToInt(Integer::intValue).sum());
        return camp;
    }

}
