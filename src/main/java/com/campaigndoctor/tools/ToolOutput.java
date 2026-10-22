package com.campaigndoctor.tools;

/** What a tool returns to the model. Errors are returned, not thrown, so the model can recover. */
public record ToolOutput(String content, boolean isError) {

    public static ToolOutput ok(String content) {
        return new ToolOutput(content, false);
    }

    public static ToolOutput error(String message) {
        return new ToolOutput("ERROR: " + message, true);
    }
}
