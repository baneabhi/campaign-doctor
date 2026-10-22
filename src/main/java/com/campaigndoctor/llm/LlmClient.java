package com.campaigndoctor.llm;

import java.util.List;

/** The one seam between our code and a model provider. Tests plug in a fake; production uses {@link AnthropicClient}. */
public interface LlmClient {

    LlmResponse send(String system, List<Message> messages, List<ToolDef> tools);
}
