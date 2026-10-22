package com.campaigndoctor.agent;

/** What the CLI and eval harness need from an agent. Lets tests substitute a fake. */
public interface Assistant {

    AgentResult ask(String question);

    /** Forget the conversation so far. */
    void reset();
}
