package com.campaigndoctor.llm;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** LlmClient backed by the official Anthropic Java SDK. */
public final class SdkAnthropicClient implements LlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final com.anthropic.client.AnthropicClient sdk;
    private final String model;
    private final long maxTokens;

    public SdkAnthropicClient(com.anthropic.client.AnthropicClient sdk, String model, long maxTokens) {
        this.sdk = sdk;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    public static SdkAnthropicClient fromEnv() {
        String key = System.getenv("ANTHROPIC_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is not set.");
        }
        String model = System.getenv().getOrDefault("ANTHROPIC_MODEL", AnthropicClient.DEFAULT_MODEL);
        var sdk = AnthropicOkHttpClient.builder()
                .fromEnv()
                .maxRetries(2)
                .timeout(Duration.ofSeconds(60))
                .build();
        return new SdkAnthropicClient(sdk, model, 4096);
    }

    @Override
    public LlmResponse send(String system, List<Message> messages, List<ToolDef> tools) {
        try {
            var reply = sdk.messages().create(toParams(model, maxTokens, system, messages, tools));
            return toResponse(reply);
        } catch (AnthropicException e) {
            throw new LlmException("Anthropic API call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        sdk.close();
    }

    // ---- our types -> SDK types ----

    static MessageCreateParams toParams(String model, long maxTokens, String system,
                                        List<Message> messages, List<ToolDef> tools) {
        MessageCreateParams.Builder b = MessageCreateParams.builder().model(model).maxTokens(maxTokens);
        if (system != null && !system.isBlank()) {
            b.system(system);
        }
        for (ToolDef t : tools) {
            b.addTool(toSdkTool(t));
        }
        for (Message m : messages) {
            List<ContentBlockParam> blocks = m.blocks().stream().map(SdkAnthropicClient::toBlockParam).toList();
            // UNSURE: exact builder method names. Let autocomplete find the ones that take a List<ContentBlockParam>.
            if (m.role() == Message.Role.USER) {
                b.addUserMessageOfContentBlockParams(blocks);
            } else {
                b.addAssistantMessageOfContentBlockParams(blocks);
            }
        }
        return b.build();
    }

    private static ContentBlockParam toBlockParam(Block block) {
        return switch (block) {
            case Block.Text t -> ContentBlockParam.ofText(TextBlockParam.builder().text(t.text()).build());
            case Block.ToolUse u -> ContentBlockParam.ofToolUse(ToolUseBlockParam.builder()
                    .id(u.id()).name(u.name()).input(JsonValue.from(u.input())).build());
            case Block.ToolResult r -> ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                    .toolUseId(r.toolUseId()).content(r.content()).isError(r.isError()).build());
        };
    }

    @SuppressWarnings("unchecked")
    private static Tool toSdkTool(ToolDef def) {
        Map<String, Object> schema = def.inputSchema();
        // UNSURE: Tool.InputSchema builder names.
        var input = Tool.InputSchema.builder().properties(JsonValue.from(schema.get("properties")));
        for (String required : (List<String>) schema.get("required")) {
            input.addRequired(required);
        }
        return Tool.builder().name(def.name()).description(def.description()).inputSchema(input.build()).build();
    }

    // ---- SDK types -> our types ----

    static LlmResponse toResponse(com.anthropic.models.messages.Message reply) {
        List<Block> blocks = new ArrayList<>();
        reply.content().forEach(b -> {
            b.text().ifPresent(t -> blocks.add(new Block.Text(t.text())));
            b.toolUse().ifPresent(u -> blocks.add(new Block.ToolUse(u.id(), u.name(), toMap(u._input()))));
            // thinking and other block types are ignored, as in the hand-rolled client
        });
        String stop = reply.stopReason().map(r -> r.asString()).orElse("");
        StopReason stopReason = switch (stop) {
            case "end_turn" -> StopReason.END_TURN;
            case "tool_use" -> StopReason.TOOL_USE;
            case "max_tokens" -> StopReason.MAX_TOKENS;
            default -> StopReason.OTHER;
        };
        return new LlmResponse(blocks, stopReason, new Usage(
                Math.toIntExact(reply.usage().inputTokens()),
                Math.toIntExact(reply.usage().outputTokens())));
    }

    private static Map<String, Object> toMap(JsonValue input) {
        // UNSURE: if this fails at runtime, walk input.asObject() and convert each value by hand.
        return MAPPER.convertValue(input, new TypeReference<Map<String, Object>>() {});
    }
}