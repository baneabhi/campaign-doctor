package com.campaigndoctor.agent;

/**
 * @param topK        how many chunks to retrieve per question
 * @param maxLlmCalls hard cap on model calls for one question, so a confused model cannot loop forever
 */
public record AgentConfig(int topK, int maxLlmCalls, boolean agenticRag) {

    public AgentConfig(int topK, int maxLlmCalls) {
        this(topK, maxLlmCalls, false);
    }

    public static AgentConfig defaults() {
        return new AgentConfig(4, 6);
    }

    public static AgentConfig agentic() {
        return new AgentConfig(4, 6, true);
    }
}
