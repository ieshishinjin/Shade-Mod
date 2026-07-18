package io.github.shade.story.journal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.github.shade.ShadeMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图鉴管理器 — 记录玩家发现的生物、物品和方块
 *
 * 每个玩家的发现记录独立保存：
 * <world>/data/shade/bestiary/<uuid>.json
 */
public class BestiaryManager {

    private static final Map<ServerLevel, BestiaryManager> INSTANCES = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STRING_SET_TYPE = new TypeToken<Set<String>>() {}.getType();

    private final ServerLevel world;
    private final Path saveDir;

    /** 内置图鉴条目定义 */
    private final Map<String, BestiaryEntry> bestiaryDefs = new LinkedHashMap<>();

    /** 玩家发现缓存：uuid → Set<entryId> */
    private final Map<UUID, Set<String>> discoveredEntries = new ConcurrentHashMap<>();

    private BestiaryManager(ServerLevel world) {
        this.world = world;
        this.saveDir = world.getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve("data/shade/bestiary");
        registerDefaultEntries();
    }

    // ==================== 单例 ====================

    public static BestiaryManager getInstance(ServerLevel world) {
        return INSTANCES.computeIfAbsent(world, BestiaryManager::new);
    }

    public static void cleanup(ServerLevel world) {
        BestiaryManager mgr = INSTANCES.remove(world);
        if (mgr != null) mgr.saveAll();
    }

    public static void cleanupAll() {
        for (BestiaryManager mgr : INSTANCES.values()) mgr.saveAll();
        INSTANCES.clear();
    }

    // ==================== 内置条目 ====================

    private void registerDefaultEntries() {
        // —— 敌对生物 ——
        addDefinition(BestiaryEntry.mob("bestiary_zombie", "僵尸",
                "最常见的亡灵生物，白天被阳光灼烧，夜间四处游荡。常在据点中成群出现。", "hostile"));
        addDefinition(BestiaryEntry.mob("bestiary_skeleton", "骷髅",
                "亡灵弓箭手，在黑暗中潜伏。被它们射中的箭矢会带来缓慢效果。", "hostile"));
        addDefinition(BestiaryEntry.mob("bestiary_spider", "蜘蛛",
                "能在墙壁和天花板上攀爬的节肢生物。夜行性，白天不会主动攻击。", "hostile"));
        addDefinition(BestiaryEntry.mob("bestiary_creeper", "苦力怕",
                "标志性的绿色生物，会无声接近玩家然后爆炸。保持距离是最好的应对方式。", "hostile"));
        addDefinition(BestiaryEntry.mob("bestiary_pillager", "掠夺者",
                "手持弩箭的灾厄村民，成群结队地出没。西边哨站就是被他们占领的。", "hostile"));
        addDefinition(BestiaryEntry.mob("bestiary_husk", "尸壳",
                "沙漠中的僵尸变种，不会被阳光灼伤。它们的攻击会附带饥饿效果。", "hostile"));
        addDefinition(BestiaryEntry.mob("bestiary_stray", "流浪者",
                "雪地中的骷髅变种，射出的箭矢带有缓慢效果。在针叶林和雪地据点中出没。", "hostile"));
        addDefinition(BestiaryEntry.mob("bestiary_witch", "女巫",
                "使用药水战斗的法师型生物，会投掷伤害药水和治疗自身。通常独自出现在沼泽中。", "hostile"));

        // —— 中立/被动生物 ——
        addDefinition(BestiaryEntry.mob("bestiary_villager", "村民",
                "生活在村庄中的 passive 生物，可以与玩家进行交易。不同职业的村民提供不同的交易内容。", "passive"));
        addDefinition(BestiaryEntry.mob("bestiary_wandering_trader", "流浪商人",
                "游走四方的行商，会随机出现在玩家附近。跟随他们的羊驼是辨别身份的标志。", "passive"));

        // —— 常见物品 ——
        addDefinition(BestiaryEntry.item("bestiary_iron_ingot", "铁锭",
                "最实用的金属材料，用于制作工具、武器和盔甲。可从各种战利品宝箱中获得。", "resource"));
        addDefinition(BestiaryEntry.item("bestiary_gold_ingot", "金锭",
                "闪亮的贵金属，虽然工具耐久度不高，但在某些交易中不可或缺。", "resource"));
        addDefinition(BestiaryEntry.item("bestiary_diamond", "钻石",
                "最珍贵的宝石之一，用于制作顶级工具和盔甲。通常深埋在地下深处。", "resource"));
        addDefinition(BestiaryEntry.item("bestiary_bread", "面包",
                "由小麦合成的便携食物，是长途探索中的主要口粮。", "food"));
        addDefinition(BestiaryEntry.item("bestiary_golden_apple", "金苹果",
                "蕴含神秘能量的苹果，能在短时间内提供强大的回复效果和伤害吸收。", "food"));

        // —— 常见方块 ——
        addDefinition(BestiaryEntry.block("bestiary_stone", "石头",
                "构成地壳的主要材料。用镐子挖掘可以获得圆石，是建造基地的基础材料。", "building"));
        addDefinition(BestiaryEntry.block("bestiary_oak_log", "橡木原木",
                "最常见的木材来源。可用于制作工具、建造房屋，或者直接当作燃料。", "building"));
        addDefinition(BestiaryEntry.block("bestiary_coal_ore", "煤矿",
                "最容易找到的矿物块，挖掘后获得煤炭。煤炭是熔炉的重要燃料来源。", "resource"));
        addDefinition(BestiaryEntry.block("bestiary_iron_ore", "铁矿",
                "含铁的矿石块，挖掘后获得粗铁。冶炼后得到铁锭，是中期发展的关键材料。", "resource"));
    }

    public void addDefinition(BestiaryEntry entry) {
        bestiaryDefs.put(entry.getId(), entry);
    }

    // ==================== 发现/解锁 ====================

    /**
     * 为玩家发现一个图鉴条目
     */
    public boolean discover(ServerPlayer player, String entryId) {
        if (!bestiaryDefs.containsKey(entryId)) return false;

        Set<String> discovered = getDiscoveredSet(player);
        if (discovered.contains(entryId)) return false;

        discovered.add(entryId);
        save(player);
        ShadeMod.LOGGER.debug("[bestiary] 玩家 {} 发现: {} ({})",
                player.getName().getString(),
                bestiaryDefs.get(entryId).getTitle(), entryId);

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§a✦ 图鉴解锁: §e" + bestiaryDefs.get(entryId).getTitle()));
        return true;
    }

    /**
     * 根据击杀的生物 ID 自动发现图鉴条目
     */
    public void discoverByMobKill(ServerPlayer player, String entityId) {
        for (BestiaryEntry entry : bestiaryDefs.values()) {
            if (!"MOB".equals(entry.getType())) continue;
            // 匹配 bestiary ID 或 Minecraft 实体 ID
            String mappedEntityId = mapEntityToBestiaryId(entityId);
            if (mappedEntityId != null && entry.getId().equals(mappedEntityId)) {
                discover(player, entry.getId());
                return;
            }
        }
    }

    /**
     * 根据收集的物品 ID 自动发现图鉴条目
     */
    public void discoverByItemCollect(ServerPlayer player, String itemId) {
        for (BestiaryEntry entry : bestiaryDefs.values()) {
            if (!"ITEM".equals(entry.getType()) && !"BLOCK".equals(entry.getType())) continue;
            String mappedId = mapRegistryToBestiaryId(itemId);
            if (mappedId != null && entry.getId().equals(mappedId)) {
                discover(player, entry.getId());
                return;
            }
        }
    }

    /**
     * 将 Minecraft 实体 ID 映射到图鉴条目 ID
     */
    private String mapEntityToBestiaryId(String entityId) {
        return switch (entityId) {
            case "minecraft:zombie" -> "bestiary_zombie";
            case "minecraft:skeleton" -> "bestiary_skeleton";
            case "minecraft:spider" -> "bestiary_spider";
            case "minecraft:creeper" -> "bestiary_creeper";
            case "minecraft:pillager" -> "bestiary_pillager";
            case "minecraft:husk" -> "bestiary_husk";
            case "minecraft:stray" -> "bestiary_stray";
            case "minecraft:witch" -> "bestiary_witch";
            case "minecraft:villager" -> "bestiary_villager";
            case "minecraft:wandering_trader" -> "bestiary_wandering_trader";
            default -> null;
        };
    }

    /**
     * 将 Minecraft 注册表 ID 映射到图鉴条目 ID
     */
    private String mapRegistryToBestiaryId(String registryId) {
        return switch (registryId) {
            case "minecraft:iron_ingot" -> "bestiary_iron_ingot";
            case "minecraft:gold_ingot" -> "bestiary_gold_ingot";
            case "minecraft:diamond" -> "bestiary_diamond";
            case "minecraft:bread" -> "bestiary_bread";
            case "minecraft:golden_apple" -> "bestiary_golden_apple";
            case "minecraft:stone" -> "bestiary_stone";
            case "minecraft:oak_log" -> "bestiary_oak_log";
            case "minecraft:coal_ore" -> "bestiary_coal_ore";
            case "minecraft:iron_ore" -> "bestiary_iron_ore";
            default -> null;
        };
    }

    // ==================== 查询 ====================

    public boolean isDiscovered(ServerPlayer player, String entryId) {
        return getDiscoveredSet(player).contains(entryId);
    }

    public List<BestiaryEntry> getAllEntries() {
        return new ArrayList<>(bestiaryDefs.values());
    }

    public BestiaryEntry getEntry(String id) {
        return bestiaryDefs.get(id);
    }

    public int getDiscoveredCount(ServerPlayer player) {
        return getDiscoveredSet(player).size();
    }

    public int getTotalCount() {
        return bestiaryDefs.size();
    }

    /**
     * 获取带发现状态的展示数据
     */
    public BestiaryDisplayData getDisplayData(ServerPlayer player) {
        Set<String> discovered = getDiscoveredSet(player);
        return new BestiaryDisplayData(new ArrayList<>(bestiaryDefs.values()), discovered);
    }

    // ==================== 持久化 ====================

    private Set<String> getDiscoveredSet(ServerPlayer player) {
        return discoveredEntries.computeIfAbsent(player.getUUID(), uuid -> loadFromDisk(uuid));
    }

    private Set<String> loadFromDisk(UUID uuid) {
        Path file = saveDir.resolve(uuid + ".json");
        if (!Files.exists(file)) return ConcurrentHashMap.newKeySet();
        try (Reader reader = Files.newBufferedReader(file)) {
            Set<String> loaded = GSON.fromJson(reader, STRING_SET_TYPE);
            if (loaded != null) { Set<String> result = ConcurrentHashMap.newKeySet(); result.addAll(loaded); return result; }
            return ConcurrentHashMap.newKeySet();
        } catch (IOException e) {
            ShadeMod.LOGGER.error("[bestiary] 加载失败: {}", uuid, e);
            return ConcurrentHashMap.newKeySet();
        }
    }

    public void save(ServerPlayer player) {
        Set<String> discovered = discoveredEntries.get(player.getUUID());
        if (discovered == null) return;
        try {
            Files.createDirectories(saveDir);
            try (Writer writer = Files.newBufferedWriter(saveDir.resolve(player.getUUID() + ".json"))) {
                GSON.toJson(discovered, writer);
            }
        } catch (IOException e) {
            ShadeMod.LOGGER.error("[bestiary] 保存失败: {}", player.getUUID(), e);
        }
    }

    private void saveAll() {
        // 持久化已在每次 discover 时同步写入
    }

    // ==================== 内部类 ====================

    public record BestiaryDisplayData(
            List<BestiaryEntry> entries,
            Set<String> discoveredIds
    ) {}
}
