package io.github.shade.story.aigen;

import io.github.shade.story.StoryEngine;
import io.github.shade.story.StoryManager;
import io.github.shade.story.adapter.AdapterRegistry;
import io.github.shade.story.model.StoryNode;
import io.github.shade.story.model.StoryScript;
import io.github.shade.story.quest.QuestManager;
import io.github.shade.story.quest.RuntimeQuest;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * AI Prompt 构建器 — 对应设计文档 §5.6 AI Prompt 模板
 *
 * 从当前游戏世界状态和剧情上下文构建完整的 AI 提示词，
 * 包含系统角色设定、游戏状态摘要、可用操作、输出格式约束。
 */
public class PromptBuilder {

    // ==================== 系统提示词 ====================

    /**
     * 构建系统提示词 — 设定 AI 的角色和行为约束
     */
    public static String buildSystemPrompt() {
        return """
                你是一个 Minecraft Galgame 的剧情生成 AI。
                你的任务是根据游戏世界状态和故事上下文，生成符合 Minecraft 世界观的剧情内容。

                ## 角色设定
                - 你是一位擅长讲故事的叙事者
                - 你生成的内容应符合 Minecraft 的奇幻+冒险风格
                - 对话应自然生动，符合角色性格
                - 剧情应有一定的深度和沉浸感

                ## 内容安全
                - 不生成暴力、色情、政治敏感内容
                - 不生成违反 Minecraft 社区准则的内容
                - 保持积极向上的冒险精神

                ## 输出约束
                - 必须使用 JSON 格式输出
                - 必须使用已注册的 Objective 类型
                - 引用的 ID 必须使用游戏中真实存在的 ID
                - 每个节点必须包含 next 字段（除 END 类型外）
                """;
    }

    // ==================== 上下文构建 ====================

    /**
     * 构建用户提示词 — 包含当前游戏状态和具体任务
     */
    public static String buildUserPrompt(ServerPlayer player, StoryEngine engine, String userInput) {
        ServerLevel world = player.serverLevel();

        StringBuilder sb = new StringBuilder();
        sb.append("【游戏世界状态】\n");
        sb.append(getWorldStateSummary(player));
        sb.append("\n");

        sb.append("【玩家信息】\n");
        sb.append(getPlayerSummary(player));
        sb.append("\n");

        sb.append("【剧情上下文】\n");
        sb.append(getStoryContext(player, engine));
        sb.append("\n");

        sb.append("【可用 Objective 类型】\n");
        sb.append(getObjectiveTypes());
        sb.append("\n");

        sb.append("【玩家输入】\n");
        if (userInput != null && !userInput.isEmpty()) {
            sb.append("玩家说：").append(userInput).append("\n");
        } else {
            sb.append("（无玩家输入，继续推进剧情）\n");
        }
        sb.append("\n");

        sb.append("【输出要求】\n");
        sb.append(getOutputFormat());
        sb.append("\n");

        sb.append("请根据以上信息生成下一个剧情节点。\n");
        sb.append("注意：如果剧情已经自然结束，请输出 END 类型节点。\n");

        return sb.toString();
    }

    // ==================== 状态摘要 ====================

    /**
     * 获取世界状态摘要（来自所有适配器）
     */
    private static String getWorldStateSummary(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        StringBuilder sb = new StringBuilder();

        // 适配器系统状态
        String adapterSummary = AdapterRegistry.getAllStatusSummaries(world);
        if (!adapterSummary.isEmpty()) {
            sb.append(adapterSummary);
        } else {
            sb.append("- 无已对接的游戏系统\n");
        }

        // 玩家活跃 Quest
        QuestManager qm = QuestManager.getInstance(world);
        var quests = qm.getActiveQuests(player);
        if (!quests.isEmpty()) {
            sb.append("- 当前活跃 Quest:\n");
            for (RuntimeQuest q : quests) {
                sb.append("  * ").append(q.getQuestName()).append("\n");
                for (var obj : q.getObjectives()) {
                    sb.append("    - ").append(obj.getDisplayLine()).append("\n");
                }
            }
        } else {
            sb.append("- 当前无活跃 Quest\n");
        }

        // 世界等级（如果有）
        try {
            int wl = io.github.shade.worldlevel.WorldLevel.getLevel(world);
            sb.append("- 世界等级: WL.").append(wl).append("\n");
        } catch (Exception ignored) {}

        return sb.toString();
    }

    /**
     * 获取玩家信息摘要
     */
    private static String getPlayerSummary(ServerPlayer player) {
        return String.format("""
                - 玩家名称: %s
                - 经验等级: %d
                - 生命值: %.0f/%.0f
                - 坐标: [%d, %d, %d]
                - 所在维度: %s
                """,
                player.getName().getString(),
                player.experienceLevel,
                player.getHealth(), player.getMaxHealth(),
                player.blockPosition().getX(),
                player.blockPosition().getY(),
                player.blockPosition().getZ(),
                player.serverLevel().dimension().location().toString()
        );
    }

    /**
     * 获取剧情上下文
     */
    private static String getStoryContext(ServerPlayer player, StoryEngine engine) {
        StringBuilder sb = new StringBuilder();

        if (!engine.isInStory(player)) {
            sb.append("- 玩家当前不在剧情中，需要生成一个开场剧情\n");
            sb.append("- 建议以某个 NPC 发现玩家开始对话\n");
            return sb.toString();
        }

        String scriptId = engine.getActiveScriptId(player);
        StoryScript script = engine.getScript(scriptId);
        if (script != null) {
            sb.append("- 当前剧本: ").append(script.getTitle()).append("\n");
        }

        StoryNode currentNode = engine.getCurrentNode(player);
        if (currentNode != null) {
            sb.append("- 当前节点: ").append(currentNode.getId())
                    .append(" (").append(currentNode.getType()).append(")\n");
            if (currentNode.getSpeaker() != null) {
                sb.append("- 当前说话人: ").append(currentNode.getSpeaker()).append("\n");
            }
            if (currentNode.getText() != null) {
                sb.append("- 当前文本: ").append(currentNode.getText()).append("\n");
            }
        }

        // 已完成脚本列表
        StoryManager manager = StoryManager.getInstance(player.serverLevel());
        var completed = manager.getProgress(player).getCompletedScripts();
        if (!completed.isEmpty()) {
            sb.append("- 已完成剧情: ").append(String.join(", ", completed)).append("\n");
        }

        return sb.toString();
    }

    // ==================== 类型库 ====================

    /**
     * 获取已注册的 Objective 类型列表
     */
    private static String getObjectiveTypes() {
        return """
                | 类型 | 说明 | 参数 |
                | OCCUPY_CAMP | 占领指定营地 | targetId=营地名称 |
                | ATTACK_CAMP | 攻击营地造成伤害 | targetId=营地名称 |
                | COLLECT_ITEM | 收集指定物品 | targetId=物品ID, count=数量 |
                | KILL_MOB | 击杀指定实体 | targetId=实体ID, count=数量 |
                | KILL_BOSS | 击败 Boss | targetId=实体ID |
                | REACH_LOCATION | 到达指定区域 | targetId=区域名 |
                | REACH_LEVEL | 达到指定等级 | count=等级 |
                """;
    }

    // ==================== 输出格式 ====================

    /**
     * 获取严格的 JSON 输出格式要求
     */
    private static String getOutputFormat() {
        return """
                你必须严格按以下 JSON 格式返回，不要包含解释文本：

                {
                  "nodes": [
                    {
                      "id": "node_id",
                      "type": "DIALOG",
                      "speaker": "说话人名称",
                      "text": "对话内容...",
                      "next": "next_node_id"
                    }
                  ],
                  "start_node": "node_id",
                  "flags_to_set": {}
                }

                节点类型可以是: DIALOG, CHOICE, QUEST_START, QUEST_UPDATE, QUEST_COMPLETE, CONDITION, EVENT, END

                CHOICE 类型需要 options 字段：
                {
                  "type": "CHOICE",
                  "speaker": "说话人",
                  "text": "问题...",
                  "options": [
                    {"label": "选项1", "next": "node_1"},
                    {"label": "选项2", "next": "node_2"}
                  ]
                }

                QUEST_START 类型需要 quest 字段：
                {
                  "type": "QUEST_START",
                  "speaker": "NPC名称",
                  "text": "对话...",
                  "quest": {
                    "quest_id": "quest_xxx",
                    "quest_name": "任务名称",
                    "quest_description": "任务描述",
                    "objectives": [
                      {"type": "KILL_MOB", "targetId": "minecraft:zombie", "count": 5, "displayText": "击杀僵尸"}
                    ],
                    "rewards": {"exp": 100, "items": ["minecraft:iron_ingot:3"]},
                    "on_quest_complete": "complete_node"
                  }
                }

                END 类型：
                {
                  "type": "END",
                  "text": "章节结束语..."
                }

                重要规则：
                1. 每个节点必须有唯一的 id
                2. DIALOG 和 QUEST_START 必须有 next
                3. CHOICE 必须有至少 2 个选项
                4. END 不需要 next
                5. 所有 ID 必须使用小写字母和下划线
                6. 文本内容要使用中文（如果玩家使用中文）
                """;
    }
}
