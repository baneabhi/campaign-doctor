package com.campaigndoctor.agent;

import com.campaigndoctor.llm.Block;
import com.campaigndoctor.llm.LlmClient;
import com.campaigndoctor.llm.LlmResponse;
import com.campaigndoctor.llm.Message;
import com.campaigndoctor.llm.StopReason;
import com.campaigndoctor.llm.ToolDef;
import com.campaigndoctor.llm.Usage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/** A scripted stand-in for the model. Records every call, snapshotting the messages as they were sent. */
final class FakeLlmClient implements LlmClient {

    record Call(String system, List<Message> messages, List<ToolDef> tools) {}

    final List<Call> calls = new ArrayList<>();
    private final Deque<LlmResponse> script = new ArrayDeque<>();
    private final LlmResponse fallback;

    private FakeLlmClient(List<LlmResponse> responses, LlmResponse fallback) {
        this.script.addAll(responses);
        this.fallback = fallback;
    }

    static FakeLlmClient scripted(LlmResponse... responses) {
        return new FakeLlmClient(List.of(responses), null);
    }

    static FakeLlmClient alwaysReturning(LlmResponse response) {
        return new FakeLlmClient(List.of(), response);
    }

    static LlmResponse text(String text, int inputTokens, int outputTokens) {
        return new LlmResponse(List.of(new Block.Text(text)), StopReason.END_TURN,
                new Usage(inputTokens, outputTokens));
    }

    static LlmResponse toolCall(String id, String name, Map<String, Object> input, int inputTokens, int outputTokens) {
        return new LlmResponse(List.of(new Block.ToolUse(id, name, input)), StopReason.TOOL_USE,
                new Usage(inputTokens, outputTokens));
    }

    @Override
    public LlmResponse send(String system, List<Message> messages, List<ToolDef> tools) {
        calls.add(new Call(system, List.copyOf(messages), List.copyOf(tools)));
        LlmResponse next = script.poll();
        if (next != null) {
            return next;
        }
        if (fallback != null) {
            return fallback;
        }
        throw new AssertionError("The LLM was called more times than the test scripted");
    }
}
