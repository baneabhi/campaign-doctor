package com.campaigndoctor.llm;

public enum StopReason {
    /** The model finished its answer. */
    END_TURN,
    /** The model wants one or more tools run before it continues. */
    TOOL_USE,
    /** The reply was cut off by the max_tokens limit. */
    MAX_TOKENS,
    OTHER
}
