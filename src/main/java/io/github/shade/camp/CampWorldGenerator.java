package io.github.shade.camp;

import io.github.shade.ShadeMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.*;

/** 种子计算+区块定稿 — 跳过安全检查 */
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

        Random rand = new Random(world.getSeed() + pos.asLong());
        int distanceFromSpawn = (int) Math.sqrt(x * x + z * z);

        // 确定据点类型：远处更可能出现特殊类型
        Camp.Type type = determineCampType(rand, distanceFromSpawn);

        // 生成名称
        String name = generateCampName(rand, type);

        Camp camp = new Camp(name, pos);
        camp.setType(type);
        camp.setSafeSpawnPointsFromBlocks(spawnPoints);

        // 根据类型配置怪物和战利品
        switch (type) {
            case BOSS -> configureBossCamp(camp, world, biome, rand, distanceFromSpawn);
            case RESOURCE -> configureResourceCamp(camp, world, biome, rand);
            case PUZZLE -> configurePuzzleCamp(camp, world, biome, rand);
            default -> configureNormalCamp(camp, world, biome, rand);
        }

        manager.addCamp(camp);
        ShadeMod.LOGGER.debug("[shadecamp] {} '{}' @ {} | {} 只怪物",
                type, name, pos.toShortString(),
                camp.getMobConfig().values().stream().mapToInt(Integer::intValue).sum());
        return camp;
    }

    /**
     * 根据距离确定据点类型
     */
    private static Camp.Type determineCampType(Random rand, int distanceFromSpawn) {
        // 靠近出生点：大多为普通
        if (distanceFromSpawn < 500) {
            return rand.nextDouble() < 0.15 ? Camp.Type.RESOURCE : Camp.Type.NORMAL;
        }
        // 中距离：可能出现资源营地和谜题营地
        double r = rand.nextDouble();
        if (r < 0.10) return Camp.Type.BOSS;
        if (r < 0.25) return Camp.Type.PUZZLE;
        if (r < 0.40) return Camp.Type.RESOURCE;
        return Camp.Type.NORMAL;
    }

    /**
     * 生成据点名称
     */
    private static String generateCampName(Random rand, Camp.Type type) {
        String[] pre = {"废弃", "破旧", "荒芜", "隐秘", "古老", "幽暗"};
        String[] bossPre = {"血腥", "恶魔", "诅咒", "黑暗", "邪教"};
        String[] resourcePre = {"富饶", "矿藏", "资源", "物资", "补给"};
        String[] puzzlePre = {"谜之", "机关", "迷宫", "封印", "神秘"};

        return switch (type) {
            case BOSS -> bossPre[rand.nextInt(bossPre.length)] + "营寨";
            case RESOURCE -> resourcePre[rand.nextInt(resourcePre.length)] + "矿场";
            case PUZZLE -> puzzlePre[rand.nextInt(puzzlePre.length)] + "遗迹";
            default -> pre[rand.nextInt(pre.length)] + "营地";
        };
    }

    /**
     * 配置普通营地
     */
    private static void configureNormalCamp(Camp camp, ServerLevel world,
                                             Holder<net.minecraft.world.level.biome.Biome> biome, Random rand) {
        List<String> mobPool = CampRandomizer.getMobPoolForBiome(biome);
        camp.setMobConfig(generateMobConfig(mobPool, rand, 3, 8));
        camp.setLootTable(Camp.CAMP_ID + ":chests/camp_common");
    }

    /**
     * 配置 Boss 营寨 — 大量普通怪物 + 一只 Boss
     */
    private static void configureBossCamp(Camp camp, ServerLevel world,
                                           Holder<net.minecraft.world.level.biome.Biome> biome, Random rand,
                                           int distanceFromSpawn) {
        List<String> mobPool = CampRandomizer.getMobPoolForBiome(biome);
        // Boss 营寨有更多怪物
        Map<String, Integer> config = generateMobConfig(mobPool, rand, 5, 12);

        // 添加 Boss 级怪物
        String boss = selectBossForBiome(biome, rand);
        config.put(boss, 1);

        camp.setMobConfig(config);
        camp.setTriggerRange(16); // 更大触发范围
        camp.setLootTable(Camp.CAMP_ID + ":chests/camp_epic");
    }

    /**
     * 配置资源营地 — 少量弱怪 + 资源宝箱
     */
    private static void configureResourceCamp(Camp camp, ServerLevel world,
                                               Holder<net.minecraft.world.level.biome.Biome> biome, Random rand) {
        List<String> mobPool = CampRandomizer.getMobPoolForBiome(biome);
        // 资源营地怪物较少
        Map<String, Integer> config = generateMobConfig(mobPool, rand, 1, 3);

        camp.setMobConfig(config);
        camp.setTriggerRange(8); // 较小触发范围
        // 资源营地使用矿物奖励战利品表
        camp.setLootTable(Camp.CAMP_ID + ":chests/camp_resource");
    }

    /**
     * 配置谜题营地 — 少量怪物 + 特殊宝藏
     */
    private static void configurePuzzleCamp(Camp camp, ServerLevel world,
                                             Holder<net.minecraft.world.level.biome.Biome> biome, Random rand) {
        List<String> mobPool = CampRandomizer.getMobPoolForBiome(biome);
        // 谜题营地中等数量
        Map<String, Integer> config = generateMobConfig(mobPool, rand, 2, 5);

        camp.setMobConfig(config);
        camp.setLootTable(Camp.CAMP_ID + ":chests/camp_rare");
    }

    /**
     * 选择适合群系的 Boss 怪物
     */
    private static String selectBossForBiome(Holder<net.minecraft.world.level.biome.Biome> biome, Random rand) {
        var biomeKey = biome.unwrapKey();
        String biomeName = biomeKey.map(k -> k.location().getPath()).orElse("");

        String[] bosses;
        if (biomeName.contains("desert")) {
            bosses = new String[]{"minecraft:husk", "minecraft:zombie"};
        } else if (biomeName.contains("snowy") || biomeName.contains("ice")) {
            bosses = new String[]{"minecraft:stray", "minecraft:skeleton"};
        } else {
            bosses = new String[]{"minecraft:zombie", "minecraft:skeleton"};
        }
        // Boss 带装备强化
        return bosses[rand.nextInt(bosses.length)];
    }

    /**
     * 通用怪物配置生成
     */
    private static Map<String, Integer> generateMobConfig(List<String> mobPool, Random rand,
                                                           int minTotal, int maxTotal) {
        if (mobPool.isEmpty()) {
            mobPool = new ArrayList<>(List.of("minecraft:zombie", "minecraft:skeleton"));
        }

        int totalTarget = minTotal + rand.nextInt(maxTotal - minTotal + 1);
        int speciesCount = Math.min(1 + rand.nextInt(Math.min(3, mobPool.size())), mobPool.size());
        List<String> selected = new ArrayList<>(mobPool);
        Collections.shuffle(selected, rand);
        selected = selected.subList(0, speciesCount);

        Map<String, Integer> config = new LinkedHashMap<>();
        int allocated = 0;
        for (int i = 0; i < selected.size(); i++) {
            int max = i == selected.size() - 1 ? totalTarget - allocated
                    : Math.min(3, totalTarget - allocated - (selected.size() - i - 1));
            if (max < 1) max = 1;
            int count = 1 + rand.nextInt(Math.min(3, max));
            config.put(selected.get(i), count);
            allocated += count;
        }
        return config;
    }

}
