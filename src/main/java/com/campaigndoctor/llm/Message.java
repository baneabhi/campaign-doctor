package com.campaigndoctor.llm;

import java.util.List;

/** A single turn in the conversation. Tool results travel in a USER message, as the Messages API expects. */
public record Message(Role role, List<Block> blocks) {

    public enum Role { USER, ASSISTANT }

    public Message {
        blocks = List.copyOf(blocks);
    }

    public static Message user(String text) {
        return new Message(Role.USER, List.of(new Block.Text(text)));
    }

    public static Message assistant(List<Block> blocks) {
        return new Message(Role.ASSISTANT, blocks);
    }

    public static Message toolResults(List<Block.ToolResult> results) {
        return new Message(Role.USER, List.<Block>copyOf(results));
    }

    /** All text blocks joined together. */
    public String text() {
        return Block.textOf(blocks);
    }

    public List<Block.ToolUse> toolUses() {
        return Block.toolUsesOf(blocks);
    }
}
