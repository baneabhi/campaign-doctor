package com.campaigndoctor.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal, non-streaming client for the Anthropic Messages API, built on the JDK HttpClient.
 * Retries rate limits (429) and server errors (5xx) with exponential backoff.
 */
public final class AnthropicClient implements LlmClient {

    static final String DEFAULT_MODEL = "claude-sonnet-5-5";
    private static final URI ENDPOINT = URI.create("https://api.anthropic.com/v1/messages");
    private static final String API_VERSION = "2023-06-01";
    private static final int MAX_ATTEMPTS = 3;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final HttpClient http;
    private final URI endpoint;

    public AnthropicClient(String apiKey, String model, int maxTokens) {
        this(apiKey, model, maxTokens,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), ENDPOINT);
    }

    AnthropicClient(String apiKey, String model, int maxTokens, HttpClient http, URI endpoint) {
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.http = http;
        this.endpoint = endpoint;
    }

    /** Reads ANTHROPIC_API_KEY (required) and ANTHROPIC_MODEL (optional) from the environment. */
    public static AnthropicClient fromEnv() {
        String key = System.getenv("ANTHROPIC_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "ANTHROPIC_API_KEY is not set. Put it in a .env file (see .env.example) or export it.");
        }
        String model = System.getenv().getOrDefault("ANTHROPIC_MODEL", DEFAULT_MODEL);
        return new AnthropicClient(key, model, 4096);
    }

    @Override
    public LlmResponse send(String system, List<Message> messages, List<ToolDef> tools) {
        String body = buildRequestBody(model, maxTokens, system, messages, tools);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(60))
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        for (int attempt = 1; ; attempt++) {
            HttpResponse<String> response;
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                if (attempt < MAX_ATTEMPTS) {
                    backoff(attempt);
                    continue;
                }
                throw new LlmException("Network error calling the Anthropic API: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmException("Interrupted while calling the Anthropic API", e);
            }

            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return parseResponse(response.body());
            }
            boolean retryable = status == 429 || status >= 500;
            if (retryable && attempt < MAX_ATTEMPTS) {
                backoff(attempt);
                continue;
            }
            throw new LlmException("Anthropic API returned HTTP " + status + ": " + truncate(response.body(), 500));
        }
    }

    static String buildRequestBody(String model, int maxTokens, String system,
                                   List<Message> messages, List<ToolDef> tools) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", maxTokens);
        if (system != null && !system.isBlank()) {
            root.put("system", system);
        }

        ArrayNode messageArray = root.putArray("messages");
        for (Message message : messages) {
            ObjectNode messageNode = messageArray.addObject();
            messageNode.put("role", message.role() == Message.Role.USER ? "user" : "assistant");
            ArrayNode content = messageNode.putArray("content");
            for (Block block : message.blocks()) {
                ObjectNode blockNode = content.addObject();
                switch (block) {
                    case Block.Text t -> {
                        blockNode.put("type", "text");
                        blockNode.put("text", t.text());
                    }
                    case Block.ToolUse u -> {
                        blockNode.put("type", "tool_use");
                        blockNode.put("id", u.id());
                        blockNode.put("name", u.name());
                        blockNode.set("input", MAPPER.valueToTree(u.input()));
                    }
                    case Block.ToolResult r -> {
                        blockNode.put("type", "tool_result");
                        blockNode.put("tool_use_id", r.toolUseId());
                        blockNode.put("content", r.content());
                        if (r.isError()) {
                            blockNode.put("is_error", true);
                        }
                    }
                }
            }
        }

        if (!tools.isEmpty()) {
            ArrayNode toolArray = root.putArray("tools");
            for (ToolDef tool : tools) {
                ObjectNode toolNode = toolArray.addObject();
                toolNode.put("name", tool.name());
                toolNode.put("description", tool.description());
                toolNode.set("input_schema", MAPPER.valueToTree(tool.inputSchema()));
            }
        }
        return root.toString();
    }

    static LlmResponse parseResponse(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            List<Block> blocks = new ArrayList<>();
            for (JsonNode b : root.path("content")) {
                switch (b.path("type").asText()) {
                    case "text" -> blocks.add(new Block.Text(b.path("text").asText()));
                    case "tool_use" -> {
                        Map<String, Object> input = b.has("input")
                                ? MAPPER.convertValue(b.get("input"), new TypeReference<Map<String, Object>>() {})
                                : Map.of();
                        blocks.add(new Block.ToolUse(b.path("id").asText(), b.path("name").asText(), input));
                    }
                    default -> System.err.println("ignored block type: " + b.path("type").asText());
                }
            }
            StopReason stop = switch (root.path("stop_reason").asText()) {
                case "end_turn" -> StopReason.END_TURN;
                case "tool_use" -> StopReason.TOOL_USE;
                case "max_tokens" -> StopReason.MAX_TOKENS;
                default -> StopReason.OTHER;
            };
            JsonNode usage = root.path("usage");
            return new LlmResponse(blocks, stop,
                    new Usage(usage.path("input_tokens").asInt(), usage.path("output_tokens").asInt()));
        } catch (IOException e) {
            throw new LlmException("Could not parse the API response: " + e.getMessage(), e);
        }
    }

    private static void backoff(int attempt) {
        try {
            Thread.sleep(1000L << (attempt - 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted while waiting to retry", e);
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
