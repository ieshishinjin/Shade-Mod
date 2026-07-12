package io.github.shade.story.model;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class StoryScript {

    private String id;
    private String title;

    @SerializedName("description")
    private String description;

    @SerializedName("start_node")
    private String startNode;

    private Map<String, StoryNode> nodes;

    public StoryScript() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d; }
    public String getStartNode() { return startNode; }
    public void setStartNode(String s) { this.startNode = s; }
    public Map<String, StoryNode> getNodes() { return nodes; }
    public void setNodes(Map<String, StoryNode> n) { this.nodes = n; }
    public StoryNode getNode(String id) { return nodes != null ? nodes.get(id) : null; }
}
