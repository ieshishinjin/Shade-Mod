package io.github.shade.story;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import io.github.shade.ShadeMod;
import io.github.shade.story.model.*;
import io.github.shade.story.quest.QuestManager;
import io.github.shade.story.quest.RuntimeQuest;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 剧情引擎核心 — 对应设计文档 §六 "剧情引擎" 模块
 *
 * 职责：加载脚本、推进节点、管理状态、条件求值、事件执行。
 * 这是整个 Galgame 系统的"心脏"，不依赖任何具体游戏系统。
 */
public class StoryEngine {

    private static final Map<ServerLevel, StoryEngine> INSTANCES = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(NodeType.class, new NodeTypeDeserializer())
            .create();

    /** 已加载的脚本库：scriptId → StoryScript */
    private final Map<String, StoryScript> scripts = new LinkedHashMap<>();

    private final ServerLevel world;

    /** 玩家活跃故事状态：playerUUID → PlayerStoryState */
    private final Map<UUID, PlayerStoryState> activeStories = new ConcurrentHashMap<>();

    // ==================== 单例 ====================

    private StoryEngine(ServerLevel world) {
        this.world = world;
    }

    public static StoryEngine getInstance(ServerLevel world) {
        return INSTANCES.computeIfAbsent(world, StoryEngine::new);
    }

    public static void cleanup(ServerLevel world) {
        INSTANCES.remove(world);
    }

    public static void cleanupAll() {
        INSTANCES.clear();
    }

    // ==================== 脚本加载 ====================

    /**
     * 从 assets/shade/story/ 目录加载所有 JSON 脚本
     * 支持热加载：开发时修改脚本后调用此方法即可生效
     */
    public void loadScripts() {
        scripts.clear();
        int loaded = 0;

        // 使用服务器 ResourceManager 扫描 shade:story/*.json
        String folder = "story";
        var resourceManager = world.getServer().getResourceManager();
        var resourceIds = resourceManager.listResources(folder,
                path -> path.getPath().endsWith(".json") && path.getNamespace().equals("shade"));

        for (var entry : resourceIds.entrySet()) {
            ResourceLocation location = entry.getKey();
            try (var input = entry.getValue().open();
                 var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {

                StoryScript script = GSON.fromJson(reader, StoryScript.class);
                if (script != null && script.getNodes() != null) {
                    // 如果脚本没有显式设置 id，从文件名推断
                    if (script.getId() == null || script.getId().isBlank()) {
                        String path = location.getPath();
                        String fileName = path.substring(path.lastIndexOf('/') + 1);
                        script.setId(fileName.replace(".json", ""));
                    }
                    scripts.put(script.getId(), script);
                    loaded++;
                    ShadeMod.LOGGER.debug("[story] 加载脚本: {} ({})", script.getId(), script.getTitle());
                }
            } catch (IOException | JsonSyntaxException e) {
                ShadeMod.LOGGER.error("[story] 加载脚本失败: {}", location, e);
            }
        }

        ShadeMod.LOGGER.info("[story] 已加载 {} 个剧情脚本", loaded);
    }

    /**
     * 按 ResourceLocation 直接加载指定脚本（用于调试/命令）
     */
    public StoryScript loadScript(ResourceLocation location) {
        try (var input = world.getServer().getResourceManager().open(location);
             var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            StoryScript script = GSON.fromJson(reader, StoryScript.class);
            if (script != null && script.getNodes() != null) {
                if (script.getId() == null || script.getId().isBlank()) {
                    String path = location.getPath();
                    String fileName = path.substring(path.lastIndexOf('/') + 1);
                    script.setId(fileName.replace(".json", ""));
                }
                scripts.put(script.getId(), script);
                ShadeMod.LOGGER.info("[story] 加载脚本: {} ({})", script.getId(), script.getTitle());
                return script;
            }
        } catch (IOException | JsonSyntaxException e) {
            ShadeMod.LOGGER.error("[story] 加载脚本失败: {}", location, e);
        }
        return null;
    }

    /**
     * 热加载指定脚本文件
     */
    public void reloadScript(String scriptId) {
        // 从资源中重新加载
        loadScripts();
        ShadeMod.LOGGER.info("[story] 热加载完成: {}", scriptId);
    }

    /**
     * 获取已加载的脚本
     */
    public StoryScript getScript(String scriptId) {
        return scripts.get(scriptId);
    }

    /**
     * 获取所有已加载的脚本
     */
    public Collection<StoryScript> getAllScripts() {
        return scripts.values();
    }

    // ==================== 故事流程控制 ====================

    /**
     * 开始一个故事脚本
     *
     * @param player   目标玩家
     * @param scriptId 脚本 ID
     * @return 起始节点（用于展示），可能为 null（脚本不存在）
     */
    public StoryNode startScript(ServerPlayer player, String scriptId) {
        StoryScript script = scripts.get(scriptId);
        if (script == null) {
            ShadeMod.LOGGER.warn("[story] 脚本不存在: {}", scriptId);
            return null;
        }

        PlayerStoryState state = getOrCreateState(player);
        state.currentScript = scriptId;
        state.currentNode = script.getStartNode();

        ShadeMod.LOGGER.info("[story] 玩家 {} 开始剧情: {}", player.getName().getString(), script.getTitle());

        // 获取起始节点，自动跳过非交互节点
        return resolveNextDisplayNode(player, state);
    }

    /**
     * 推进到下一个节点（用于 DIALOG 节点点击后）
     *
     * @param player 目标玩家
     * @return 下一个展示节点，null 表示剧情结束
     */
    public StoryNode advance(ServerPlayer player) {
        PlayerStoryState state = activeStories.get(player.getUUID());
        if (state == null || state.currentScript == null) return null;

        StoryScript script = scripts.get(state.currentScript);
        if (script == null) return null;

        StoryNode currentNode = script.getNode(state.currentNode);
        if (currentNode == null) return null;

        // 根据当前节点类型决定下一步
        String nextId = switch (currentNode.getType()) {
            case DIALOG, QUEST_UPDATE, QUEST_COMPLETE -> currentNode.getNext();
            default -> currentNode.getNext();
        };

        if (nextId == null || nextId.isEmpty()) {
            // 没有下一个节点 → 结束
            endStory(player);
            return null;
        }

        state.currentNode = nextId;
        return resolveNextDisplayNode(player, state);
    }

    /**
     * 玩家做出选择（用于 CHOICE 节点）
     *
     * @param player      目标玩家
     * @param optionIndex 选择的选项索引
     * @return 下一个展示节点，null 表示剧情结束
     */
    public StoryNode choose(ServerPlayer player, int optionIndex) {
        PlayerStoryState state = activeStories.get(player.getUUID());
        if (state == null || state.currentScript == null) return null;

        StoryScript script = scripts.get(state.currentScript);
        if (script == null) return null;

        StoryNode currentNode = script.getNode(state.currentNode);
        if (currentNode == null || currentNode.getType() != NodeType.CHOICE) return null;

        List<StoryChoice> options = currentNode.getOptions();
        if (options == null || optionIndex < 0 || optionIndex >= options.size()) return null;

        StoryChoice chosen = options.get(optionIndex);

        // 设置选项携带的 Flag
        if (chosen.getFlags() != null) {
            StoryManager manager = StoryManager.getInstance(world);
            StoryManager.PlayerProgress progress = manager.getProgress(player);
            if (progress != null) {
                progress.getFlags().putAll(chosen.getFlags());
                manager.save(player);
            }
        }

        state.currentNode = chosen.getNext();
        return resolveNextDisplayNode(player, state);
    }

    /**
     * 结束当前剧情
     */
    public void endStory(ServerPlayer player) {
        PlayerStoryState state = activeStories.get(player.getUUID());
        if (state != null) {
            // 标记脚本为已完成
            if (state.currentScript != null) {
                StoryManager manager = StoryManager.getInstance(world);
                manager.markScriptCompleted(player, state.currentScript);
                ShadeMod.LOGGER.info("[story] 玩家 {} 完成剧情: {}",
                        player.getName().getString(), state.currentScript);
            }
            activeStories.remove(player.getUUID());
        }
    }

    /**
     * 获取玩家当前的展示节点
     */
    public StoryNode getCurrentNode(ServerPlayer player) {
        PlayerStoryState state = activeStories.get(player.getUUID());
        if (state == null || state.currentScript == null) return null;

        StoryScript script = scripts.get(state.currentScript);
        if (script == null) return null;

        return script.getNode(state.currentNode);
    }

    /**
     * 玩家是否正在剧情中
     */
    public boolean isInStory(ServerPlayer player) {
        return activeStories.containsKey(player.getUUID());
    }

    /**
     * 获取当前脚本 ID
     */
    public String getActiveScriptId(ServerPlayer player) {
        PlayerStoryState state = activeStories.get(player.getUUID());
        return state != null ? state.currentScript : null;
    }

    // ==================== 内部方法 ====================

    /**
     * 从当前节点开始，自动跳过 CONDITION 和 EVENT 等非交互节点，
     * 返回第一个需要玩家交互的节点（DIALOG / CHOICE / QUEST_START / END）
     *
     * 安全保护：最多遍历 100 个节点防止死循环
     */
    private StoryNode resolveNextDisplayNode(ServerPlayer player, PlayerStoryState state) {
        StoryScript script = scripts.get(state.currentScript);
        if (script == null) return null;

        int maxIterations = 100;
        for (int i = 0; i < maxIterations; i++) {
            StoryNode node = script.getNode(state.currentNode);
            if (node == null) {
                endStory(player);
                return null;
            }

            switch (node.getType()) {
                case CONDITION:
                    // 自动求值并跳转，无需玩家交互
                    String nextNode = ConditionEvaluator.evaluate(player, node);
                    if (nextNode == null || nextNode.isEmpty()) {
                        endStory(player);
                        return null;
                    }
                    state.currentNode = nextNode;
                    continue;

                case EVENT:
                    // 自动执行事件并继续
                    executeEvent(player, node);
                    if (node.getNext() == null || node.getNext().isEmpty()) {
                        endStory(player);
                        return null;
                    }
                    state.currentNode = node.getNext();
                    continue;

                case END:
                    // 剧情结束
                    endStory(player);
                    return node;

                case QUEST_START:
                    // 触发 Quest 开始
                    if (node.getQuest() != null) {
                        QuestManager qm = QuestManager.getInstance(world);
                        RuntimeQuest quest = qm.startQuest(player, node.getQuest());
                        if (quest != null) {
                            ShadeMod.LOGGER.info("[story] 玩家 {} 接受 Quest: {}",
                                    player.getName().getString(), quest.getQuestName());
                        }
                    }
                    return node;

                case QUEST_UPDATE:
                    // Quest 进度更新提示（显示给玩家）
                    return node;

                case QUEST_COMPLETE:
                    // 如果有关联 Quest ID，检查是否可强制完成
                    if (node.getQuest() != null && node.getQuest().getQuestId() != null) {
                        QuestManager qm = QuestManager.getInstance(world);
                        if (!qm.isQuestCompleted(player, node.getQuest().getQuestId())) {
                            // 标记为已完成（用于剧情强制完成）
                            var quests = qm.getActiveQuests(player);
                            for (RuntimeQuest q : quests) {
                                if (q.getQuestId().equals(node.getQuest().getQuestId())) {
                                    // 强制完成所有 Objective
                                    for (var obj : q.getObjectives()) {
                                        obj.setProgress(obj.getTargetCount());
                                    }
                                }
                            }
                        }
                    }
                    return node;

                case DIALOG:
                case CHOICE:
                    // 需要玩家交互
                    return node;

                default:
                    // 未知类型，尝试继续
                    if (node.getNext() != null && !node.getNext().isEmpty()) {
                        state.currentNode = node.getNext();
                        continue;
                    }
                    endStory(player);
                    return null;
            }
        }

        // 超过最大迭代次数，防死循环保护
        ShadeMod.LOGGER.warn("[story] 节点解析超过最大迭代次数 (100)，强制结束");
        endStory(player);
        return null;
    }

    /**
     * 执行事件节点
     */
    private void executeEvent(ServerPlayer player, StoryNode node) {
        EventData event = node.getEvent();
        if (event == null) return;

        String type = event.getType();
        String value = event.getValue();
        Map<String, Object> params = event.getParams();

        ShadeMod.LOGGER.debug("[story] 执行事件: type={}, value={}", type, value);

        try {
            switch (type) {
                case EventData.TYPE_COMMAND:
                    // 执行命令
                    if (value != null && !value.isEmpty()) {
                        String command = value.startsWith("/") ? value.substring(1) : value;
                        world.getServer().getCommands().performPrefixedCommand(
                                player.createCommandSourceStack(), command);
                    }
                    break;

                case EventData.TYPE_TELEPORT:
                    // 传送玩家
                    if (params != null) {
                        int x = getParamInt(params, "x", 0);
                        int y = getParamInt(params, "y", 64);
                        int z = getParamInt(params, "z", 0);
                        player.teleportTo(world, x + 0.5, y, z + 0.5, player.getYRot(), player.getXRot());
                    }
                    break;

                case EventData.TYPE_GIVE_ITEM:
                    // 给予物品
                    if (value != null && params != null) {
                        int count = getParamInt(params, "count", 1);
                        ResourceLocation itemId = ResourceLocation.parse(value);
                        var item = BuiltInRegistries.ITEM.get(itemId);
                        if (item != null) {
                            ItemStack stack = new ItemStack(item, count);
                            if (!player.getInventory().add(stack)) {
                                // 背包满了，掉落在玩家位置
                                world.addFreshEntity(new ItemEntity(
                                        world, player.getX(), player.getY(), player.getZ(), stack));
                            }
                        }
                    }
                    break;

                case EventData.TYPE_SET_FLAG:
                    // 设置 Flag
                    if (value != null && params != null) {
                        StoryManager manager = StoryManager.getInstance(world);
                        StoryManager.PlayerProgress progress = manager.getProgress(player);
                        if (progress != null) {
                            Object flagValue = params.getOrDefault("flagValue", true);
                            progress.getFlags().put(value, flagValue);
                            manager.save(player);
                        }
                    }
                    break;

                case EventData.TYPE_PLAY_SOUND:
                    // 播放音效
                    if (value != null) {
                        var sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(value));
                        if (sound != null) {
                            player.playNotifySound(sound, SoundSource.PLAYERS, 1.0f, 1.0f);
                        }
                    }
                    break;

                case EventData.TYPE_BGM:
                    // BGM 切换 — 需要客户端包支持，Phase 2 实现
                    ShadeMod.LOGGER.debug("[story] BGM 切换: {}", value);
                    break;

                case EventData.TYPE_TRIGGER_CAMP:
                    // 触发 Camp 据点战斗 — Phase 6 接入适配器后实现
                    ShadeMod.LOGGER.debug("[story] 触发据点: {}", value);
                    break;

                default:
                    ShadeMod.LOGGER.warn("[story] 未知事件类型: {}", type);
            }
        } catch (Exception e) {
            ShadeMod.LOGGER.error("[story] 执行事件失败: type={}", type, e);
        }
    }

    private int getParamInt(Map<String, Object> params, String key, int defaultValue) {
        Object val = params.get(key);
        if (val instanceof Number n) return n.intValue();
        return defaultValue;
    }

    private PlayerStoryState getOrCreateState(ServerPlayer player) {
        return activeStories.computeIfAbsent(player.getUUID(), k -> new PlayerStoryState());
    }

    // ==================== 内部类 ====================

    /**
     * 玩家当前故事状态（运行时，不持久化）
     */
    public static class PlayerStoryState {
        String currentScript;
        String currentNode;
    }

    /**
     * Gson NodeType 反序列化器 — 支持大小写不敏感和常量名
     */
    private static class NodeTypeDeserializer implements com.google.gson.JsonDeserializer<NodeType> {
        @Override
        public NodeType deserialize(com.google.gson.JsonElement json,
                                     java.lang.reflect.Type typeOfT,
                                     com.google.gson.JsonDeserializationContext context)
                throws com.google.gson.JsonParseException {
            String value = json.getAsString().toUpperCase().trim();
            try {
                return NodeType.valueOf(value);
            } catch (IllegalArgumentException e) {
                ShadeMod.LOGGER.warn("[story] 未知节点类型: {}, 使用 DIALOG", value);
                return NodeType.DIALOG;
            }
        }
    }
}
