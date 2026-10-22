package com.campaigndoctor.llm;

import java.util.Map;

/** A tool the model may call. {@code inputSchema} is a JSON Schema object, expressed as nested maps. */
public record ToolDef(String name, String description, Map<String, Object> inputSchema) {}
