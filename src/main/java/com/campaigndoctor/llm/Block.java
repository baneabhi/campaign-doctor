package com.campaigndoctor.llm;

import java.util.List;
import java.util.Map;

/**
 * One piece of content inside a message. These types are provider-neutral:
 * {@link AnthropicClient} is the only class that knows how they map to JSON.
 */
public sealed interface Block {

    record Text(String text) implements Block {}

    /** The model asking us to run a tool. The {@code id} must be echoed back in the matching {@link ToolResult}. */
    record ToolUse(String id, String name, Map<String, Object> input) implements Block {}

    /** Our answer to a {@link ToolUse}. Set {@code isError} so the model knows the call failed. */
    record ToolResult(String toolUseId, String content, boolean isError) implements Block {}

    static String textOf(List<Block> blocks) {
        StringBuilder sb = new StringBuilder();
        for (Block b : blocks) {
            if (b instanceof Text t) {
                sb.append(t.text());
            }
        }
        return sb.toString();
    }

    static List<ToolUse> toolUsesOf(List<Block> blocks) {
        return blocks.stream().filter(ToolUse.class::isInstance).map(ToolUse.class::cast).toList();
    }
}
