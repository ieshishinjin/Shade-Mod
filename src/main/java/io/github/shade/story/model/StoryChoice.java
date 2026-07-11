package io.github.shade.story.model;

import java.util.Map;

/**
 * 选项数据 — 对应设计文档的 CHOICE 节点中的 options
 */
public class StoryChoice {
    /** 选项显示文字 */
    private String label;
    /** 选择后跳转的节点 ID */
    private String next;
    /** 选择后设置的 Flag（可选） */
    private Map<String, Object> flags;

    public StoryChoice() {}

    public StoryChoice(String label, String next) {
        this.label = label;
        this.next = next;
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getNext() { return next; }
    public void setNext(String next) { this.next = next; }

    public Map<String, Object> getFlags() { return flags; }
    public void setFlags(Map<String, Object> flags) { this.flags = flags; }
}
