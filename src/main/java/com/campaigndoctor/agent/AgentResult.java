package com.campaigndoctor.agent;

import java.util.List;

/**
 * Everything the CLI and the eval harness need to know about one answer.
 *
 * @param citations        files cited in the answer text, as [file.md]
 * @param toolsCalled      tool names in the order they were called (repeats allowed)
 * @param retrievedSources distinct files of the chunks put in the prompt
 */
public record AgentResult(String text, List<String> citations, List<String> toolsCalled,
                          List<String> retrievedSources, int inputTokens, int outputTokens, long latencyMs) {

    public static AgentResult empty() {
        return new AgentResult("", List.of(), List.of(), List.of(), 0, 0, 0);
    }
}
