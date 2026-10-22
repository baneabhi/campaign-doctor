package com.campaigndoctor.llm;

import java.util.List;

public record LlmResponse(List<Block> content, StopReason stopReason, Usage usage) {

    public LlmResponse {
        content = List.copyOf(content);
    }

    public String text() {
        return Block.textOf(content);
    }

    public List<Block.ToolUse> toolUses() {
        return Block.toolUsesOf(content);
    }
}
