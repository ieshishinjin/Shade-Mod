package io.github.shade.camp;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.*;

/**
 * 随机波次生成器
 * 根据生物群系随机生成据点的怪物组合
 */
public class CampRandomizer {

    private CampRandomizer() {}

    /** 生物群系 → 可用怪物池 */
    private static final Map<String, List<String>> BIOME_MOB_POOLS = new LinkedHashMap<>();
    static {
        BIOME_MOB_POOLS.put("plains",       List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:spider"));
        BIOME_MOB_POOLS.put("desert",       List.of("minecraft:husk", "minecraft:skeleton", "minecraft:spider"));
        BIOME_MOB_POOLS.put("forest",       List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:spider"));
        BIOME_MOB_POOLS.put("taiga",        List.of("minecraft:stray", "minecraft:zombie", "minecraft:spider"));
        BIOME_MOB_POOLS.put("swamp",        List.of("minecraft:zombie", "minecraft:spider", "minecraft:slime", "minecraft:witch"));
        BIOME_MOB_POOLS.put("jungle",       List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:spider"));
        BIOME_MOB_POOLS.put("savanna",      List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:spider"));
        BIOME_MOB_POOLS.put("snowy",        List.of("minecraft:stray", "minecraft:zombie", "minecraft:spider"));
        BIOME_MOB_POOLS.put("badlands",     List.of("minecraft:husk", "minecraft:skeleton", "minecraft:spider"));
        BIOME_MOB_POOLS.put("beach",        List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:spider"));
        BIOME_MOB_POOLS.put("mountain",     List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:spider"));
        BIOME_MOB_POOLS.put("default",      List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:spider"));
    }

    /** 默认通用池（当生物群系无法识别时使用） */
    private static final List<String> DEFAULT_POOL = List.of(
            "minecraft:zombie", "minecraft:skeleton", "minecraft:spider"
    );

    /**
     * 获取指定生物群系对应的怪物池
     */
    public static List<String> getMobPoolForBiome(Holder<Biome> biomeHolder) {
        String key = biomeHolder.unwrapKey()
                .map(k -> {
                    ResourceLocation loc = k.location();
                    // 根据路径中的关键词匹配群系类别
                    String path = loc.getPath();
                    if (path.contains("desert")) return "desert";
                    if (path.contains("badlands") || path.contains("mesa")) return "badlands";
                    if (path.contains("taiga")) return "taiga";
                    if (path.contains("snowy") || path.contains("ice") || path.contains("frozen") || path.contains("glacier")) return "snowy";
                    if (path.contains("swamp") || path.contains("mangrove")) return "swamp";
                    if (path.contains("jungle")) return "jungle";
                    if (path.contains("savanna")) return "savanna";
                    if (path.contains("forest") || path.contains("wood") || path.contains("birch") || path.contains("dark")) return "forest";
                    if (path.contains("plains") || path.contains("meadow") || path.contains("cherry")) return "plains";
                    if (path.contains("beach") || path.contains("shore") || path.contains("coast") || path.contains("ocean")) return "beach";
                    if (path.contains("mountain") || path.contains("peak") || path.contains("slope") || path.contains("hills")) return "mountain";
                    return null;
                })
                .orElse("default");

        return BIOME_MOB_POOLS.getOrDefault(key, DEFAULT_POOL);
    }

    /**
     * 生成随机怪物配置
     *
     * @param random 随机数生成器
     * @param mobPool 可用怪物池
     * @return 实体ID → 数量的映射，满足总数量在3~8之间
     */
    public static Map<String, Integer> generateRandomMobConfig(Random random, List<String> mobPool) {
        Map<String, Integer> config = new LinkedHashMap<>();

        // 1. 随机选择怪物种类数（1～3种），但不能超过怪物池大小
        int speciesCount = Math.min(1 + random.nextInt(Math.min(3, mobPool.size() - 1)), mobPool.size());

        // 2. 从池中随机抽取物种（去重）
        List<String> selectedTypes = new ArrayList<>(mobPool);
        Collections.shuffle(selectedTypes, random);
        selectedTypes = selectedTypes.subList(0, speciesCount);

        // 3. 为每种怪物分配数量（1～3只）
        int remaining = 3 + random.nextInt(6); // 目标总数 3~8
        int allocated = 0;

        for (int i = 0; i < selectedTypes.size(); i++) {
            if (i == selectedTypes.size() - 1) {
                // 最后一种怪物拿剩余所有数量
                int count = remaining - allocated;
                if (count < 1) count = 1;
                config.put(selectedTypes.get(i), count);
            } else {
                int maxPerType = Math.min(3, remaining - allocated - (selectedTypes.size() - i - 1));
                if (maxPerType < 1) maxPerType = 1;
                int count = 1 + random.nextInt(Math.min(3, maxPerType));
                config.put(selectedTypes.get(i), count);
                allocated += count;
            }
        }

        return config;
    }

    /**
     * 从生物群系名称获取可读的显示名
     */
    public static String getBiomeDisplayName(Holder<Biome> biomeHolder) {
        return biomeHolder.unwrapKey()
                .map(key -> key.location().getPath())
                .orElse("unknown");
    }
}
