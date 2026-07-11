package io.github.shade.story.aigen;

import com.google.gson.*;
import io.github.shade.ShadeMod;
import io.github.shade.story.model.*;

import java.util.*;

/**
 * AI 响应解析器 — 解析和校验 AI 返回的 JSON 剧情节点
 *
 * 对应设计文档 §5.1 安全机制：
 * - 类型白名单：只能使用已注册的节点类型
 * - JSON 格式严格校验
 * - 引用的 ID 检查
 * - 生成长度限制
 */
public class ResponseParser {

    private static final Gson GSON = new Gson();

    /** 最大节点数 */
    private static final int MAX_NODES = 20;
    /** 最大文本长度 */
    private static final int MAX_TEXT_LENGTH = 2000;

    /**
     * 解析 AI 返回的文本，提取其中的 JSON 并转换为 StoryNode 列表
     *
     * @param aiResponse AI 返回的原始文本
     * @return 解析结果，包含节点列表或错误信息
     */
    public static ParseResult parse(String aiResponse) {
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            return ParseResult.error("AI 返回为空");
        }

        try {
            // 从文本中提取 JSON（AI 可能在 JSON 前后添加解释文本）
            String jsonStr = extractJson(aiResponse);
            if (jsonStr == null) {
                return ParseResult.error("无法从响应中提取 JSON");
            }

            JsonObject root = GSON.fromJson(jsonStr, JsonObject.class);
            if (root == null) {
                return ParseResult.error("JSON 解析失败");
            }

            return parseNodes(root);

        } catch (JsonSyntaxException e) {
            return ParseResult.error("JSON 格式错误: " + e.getMessage());
        } catch (Exception e) {
            return ParseResult.error("解析异常: " + e.getMessage());
        }
    }

    /**
     * 从 AI 回复中提取第一个 JSON 对象
     */
    private static String extractJson(String text) {
        // 尝试找 ```json ... ``` 包裹的 JSON
        int codeBlockStart = text.indexOf("```json");
        if (codeBlockStart >= 0) {
            codeBlockStart += 7;
            int codeBlockEnd = text.indexOf("```", codeBlockStart);
            if (codeBlockEnd > codeBlockStart) {
                return text.substring(codeBlockStart, codeBlockEnd).trim();
            }
        }

        // 尝试找 { 到 } 的最外层括号
        int braceStart = text.indexOf('{');
        if (braceStart >= 0) {
            int depth = 0;
            for (int i = braceStart; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return text.substring(braceStart, i + 1);
                    }
                }
            }
        }

        return null;
    }

    /**
     * 解析 JSON 节点
     */
    private static ParseResult parseNodes(JsonObject root) {
        List<StoryNode> nodes = new ArrayList<>();
        Map<String, Object> flagsToSet = new LinkedHashMap<>();
        String startNode = null;

        // 解析起始节点
        if (root.has("start_node")) {
            startNode = root.get("start_node").getAsString();
        }

        // 解析 Flag
        if (root.has("flags_to_set") && root.get("flags_to_set").isJsonObject()) {
            JsonObject flags = root.getAsJsonObject("flags_to_set");
            for (String key : flags.keySet()) {
                flagsToSet.put(key, flags.get(key).getAsString());
            }
        }

        // 解析节点列表
        if (!root.has("nodes") || !root.get("nodes").isJsonArray()) {
            return ParseResult.error("缺少 nodes 字段或格式错误");
        }

        JsonArray nodesArray = root.getAsJsonArray("nodes");
        if (nodesArray.size() > MAX_NODES) {
            return ParseResult.error("生成的节点数超过限制 (" + MAX_NODES + ")");
        }

        Set<String> nodeIds = new HashSet<>();

        for (int i = 0; i < nodesArray.size(); i++) {
            try {
                JsonObject nodeObj = nodesArray.get(i).getAsJsonObject();
                StoryNode node = parseSingleNode(nodeObj);
                if (node == null) continue;

                // 检查 ID 唯一性
                if (nodeIds.contains(node.getId())) {
                    return ParseResult.error("重复的节点 ID: " + node.getId());
                }
                nodeIds.add(node.getId());

                nodes.add(node);
            } catch (Exception e) {
                return ParseResult.error("节点 #" + i + " 解析失败: " + e.getMessage());
            }
        }

        if (nodes.isEmpty()) {
            return ParseResult.error("没有生成任何有效节点");
        }

        // 检查起始节点是否存在
        if (startNode != null && !nodeIds.contains(startNode)) {
            startNode = nodes.get(0).getId();
        }

        return new ParseResult(nodes, startNode != null ? startNode : nodes.get(0).getId(), flagsToSet, null);
    }

    /**
     * 解析单个节点
     */
    private static StoryNode parseSingleNode(JsonObject obj) {
        StoryNode node = new StoryNode();

        // ID (必需)
        if (!obj.has("id")) return null;
        node.setId(obj.get("id").getAsString());

        // 类型 (必需)
        if (!obj.has("type")) return null;
        String typeStr = obj.get("type").getAsString().toUpperCase().trim();
        try {
            node.setType(NodeType.valueOf(typeStr));
        } catch (IllegalArgumentException e) {
            ShadeMod.LOGGER.warn("[ai] 未知节点类型: {}, 使用 DIALOG", typeStr);
            node.setType(NodeType.DIALOG);
        }

        // 文本 (必需)
        if (obj.has("text")) {
            String text = obj.get("text").getAsString();
            if (text.length() > MAX_TEXT_LENGTH) {
                text = text.substring(0, MAX_TEXT_LENGTH);
            }
            node.setText(text);
        }

        // 说话人 (可选)
        if (obj.has("speaker")) {
            node.setSpeaker(obj.get("speaker").getAsString());
        }

        // 立绘 (可选)
        if (obj.has("portrait")) {
            node.setPortrait(obj.get("portrait").getAsString());
        }

        // Next (可选，END 类型不需要)
        if (obj.has("next")) {
            node.setNext(obj.get("next").getAsString());
        }

        // 选项 (CHOICE)
        if (obj.has("options") && obj.get("options").isJsonArray()) {
            List<StoryChoice> choices = new ArrayList<>();
            JsonArray optArray = obj.getAsJsonArray("options");
            for (int i = 0; i < optArray.size(); i++) {
                JsonObject optObj = optArray.get(i).getAsJsonObject();
                StoryChoice choice = new StoryChoice();
                choice.setLabel(optObj.get("label").getAsString());
                choice.setNext(optObj.get("next").getAsString());
                if (optObj.has("flags") && optObj.get("flags").isJsonObject()) {
                    Map<String, Object> flags = new LinkedHashMap<>();
                    for (String key : optObj.getAsJsonObject("flags").keySet()) {
                        flags.put(key, optObj.getAsJsonObject("flags").get(key).getAsString());
                    }
                    choice.setFlags(flags);
                }
                choices.add(choice);
            }
            node.setOptions(choices);
        }

        // Quest (QUEST_START)
        if (obj.has("quest") && obj.get("quest").isJsonObject()) {
            JsonObject qObj = obj.getAsJsonObject("quest");
            QuestData quest = new QuestData();
            quest.setQuestId(getString(qObj, "quest_id"));
            quest.setQuestName(getString(qObj, "quest_name"));
            quest.setQuestDescription(getString(qObj, "quest_description"));

            if (qObj.has("objectives") && qObj.get("objectives").isJsonArray()) {
                List<QuestObjectiveData> objectives = new ArrayList<>();
                for (var el : qObj.getAsJsonArray("objectives")) {
                    JsonObject oObj = el.getAsJsonObject();
                    String type = getString(oObj, "type");
                    String targetId = getString(oObj, "targetId");
                    int count = getInt(oObj, "count", 1);
                    String display = getString(oObj, "displayText");
                    QuestObjectiveData objData = new QuestObjectiveData(type, targetId, count);
                    objData.setDisplayText(display);
                    objectives.add(objData);
                }
                quest.setObjectives(objectives);
            }

            if (qObj.has("rewards") && qObj.get("rewards").isJsonObject()) {
                JsonObject rObj = qObj.getAsJsonObject("rewards");
                QuestRewardData rewards = new QuestRewardData();
                rewards.setExp(getInt(rObj, "exp", 0));
                if (rObj.has("items") && rObj.get("items").isJsonArray()) {
                    List<String> items = new ArrayList<>();
                    for (var el : rObj.getAsJsonArray("items")) {
                        items.add(el.getAsString());
                    }
                    rewards.setItems(items);
                }
                quest.setRewards(rewards);
            }

            quest.setOnQuestComplete(getString(qObj, "on_quest_complete"));
            quest.setOnQuestFail(getString(qObj, "on_quest_fail"));

            node.setQuest(quest);
        }

        // Condition (CONDITION)
        if (obj.has("condition") && obj.get("condition").isJsonObject()) {
            JsonObject cObj = obj.getAsJsonObject("condition");
            ConditionData cond = new ConditionData();
            cond.setType(getString(cObj, "type"));
            cond.setTargetId(getString(cObj, "targetId"));
            cond.setValue(getInt(cObj, "value", 0));
            cond.setOperator(getString(cObj, "operator"));
            cond.setNextIfTrue(getString(cObj, "nextIfTrue"));
            cond.setNextIfFalse(getString(cObj, "nextIfFalse"));
            node.setCondition(cond);
        }

        // Event (EVENT)
        if (obj.has("event") && obj.get("event").isJsonObject()) {
            JsonObject eObj = obj.getAsJsonObject("event");
            EventData event = new EventData();
            event.setType(getString(eObj, "type"));
            event.setValue(getString(eObj, "value"));
            if (eObj.has("params") && eObj.get("params").isJsonObject()) {
                Map<String, Object> params = new LinkedHashMap<>();
                for (String key : eObj.getAsJsonObject("params").keySet()) {
                    params.put(key, eObj.getAsJsonObject("params").get(key).getAsString());
                }
                event.setParams(params);
            }
            node.setEvent(event);
        }

        return node;
    }

    private static String getString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : "";
    }

    private static int getInt(JsonObject obj, String key, int def) {
        return obj.has(key) ? obj.get(key).getAsInt() : def;
    }

    // ==================== 内部类 ====================

    /**
     * 解析结果
     */
    public static class ParseResult {
        private final List<StoryNode> nodes;
        private final String startNode;
        private final Map<String, Object> flagsToSet;
        private final String error;

        private ParseResult(List<StoryNode> nodes, String startNode, Map<String, Object> flagsToSet, String error) {
            this.nodes = nodes;
            this.startNode = startNode;
            this.flagsToSet = flagsToSet;
            this.error = error;
        }

        public static ParseResult error(String msg) {
            return new ParseResult(null, null, null, msg);
        }

        public boolean isSuccess() { return error == null; }
        public boolean isError() { return error != null; }
        public String getError() { return error; }
        public List<StoryNode> getNodes() { return nodes; }
        public String getStartNode() { return startNode; }
        public Map<String, Object> getFlagsToSet() { return flagsToSet; }
    }
}
