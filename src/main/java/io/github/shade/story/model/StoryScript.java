package io.github.shade.story.model;

import java.util.Map;

/**
 * 剧情脚本 — 对应设计文档定义的一个完整故事脚本文件
 *
 * 每个 .json 文件对应一个 StoryScript 实例，包含一组节点
 * 和元数据。文件存储在 data/shade/story/ 目录下。
 */
public class StoryScript {

    /** 脚本唯一标识（文件名去掉 .json） */
    private String id;

    /** 脚本标题（显示用） */
    private String title;

    /** 起始节点 ID */
    private String startNode;

    /** 节点映射：节点 ID → 节点数据 */
    private Map<String, StoryNode> nodes;

    public StoryScript() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getStartNode() { return startNode; }
    public void setStartNode(String startNode) { this.startNode = startNode; }

    public Map<String, StoryNode> getNodes() { return nodes; }
    public void setNodes(Map<String, StoryNode> nodes) { this.nodes = nodes; }

    /** 获取指定节点 */
    public StoryNode getNode(String nodeId) {
        return nodes != null ? nodes.get(nodeId) : null;
    }
}
