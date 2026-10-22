package com.campaigndoctor.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Offline tests: they check the JSON we send and parse, never the network. */
class AnthropicClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void requestCarriesModelSystemMessagesAndTools() throws Exception {
        ToolDef tool = new ToolDef("list_campaigns", "List campaigns.",
                Map.of("type", "object", "properties", Map.of(), "required", List.of()));
        List<Message> messages = List.of(
                Message.user("hi"),
                Message.assistant(List.of(new Block.ToolUse("t1", "list_campaigns", Map.of()))),
                Message.toolResults(List.of(new Block.ToolResult("t1", "C101 | Alpha", false))));

        JsonNode body = MAPPER.readTree(
                AnthropicClient.buildRequestBody("test-model", 256, "Be brief.", messages, List.of(tool)));

        assertEquals("test-model", body.get("model").asText());
        assertEquals(256, body.get("max_tokens").asInt());
        assertEquals("Be brief.", body.get("system").asText());
        assertEquals(3, body.get("messages").size());

        JsonNode first = body.get("messages").get(0);
        assertEquals("user", first.get("role").asText());
        assertEquals("hi", first.get("content").get(0).get("text").asText());

        JsonNode toolUse = body.get("messages").get(1).get("content").get(0);
        assertEquals("assistant", body.get("messages").get(1).get("role").asText());
        assertEquals("tool_use", toolUse.get("type").asText());
        assertEquals("t1", toolUse.get("id").asText());
        assertEquals("list_campaigns", toolUse.get("name").asText());

        JsonNode toolResult = body.get("messages").get(2).get("content").get(0);
        assertEquals("user", body.get("messages").get(2).get("role").asText());
        assertEquals("tool_result", toolResult.get("type").asText());
        assertEquals("t1", toolResult.get("tool_use_id").asText());
        assertEquals("C101 | Alpha", toolResult.get("content").asText());
        assertFalse(toolResult.has("is_error"));

        assertEquals("list_campaigns", body.get("tools").get(0).get("name").asText());
        assertEquals("object", body.get("tools").get(0).get("input_schema").get("type").asText());
    }

    @Test
    void errorResultsAreFlagged() throws Exception {
        List<Message> messages = List.of(
                Message.toolResults(List.of(new Block.ToolResult("t1", "ERROR: nope", true))));

        JsonNode body = MAPPER.readTree(AnthropicClient.buildRequestBody("m", 10, null, messages, List.of()));

        assertTrue(body.get("messages").get(0).get("content").get(0).get("is_error").asBoolean());
    }

    @Test
    void toolsAndBlankSystemAreOmittedWhenEmpty() throws Exception {
        JsonNode body = MAPPER.readTree(
                AnthropicClient.buildRequestBody("m", 10, " ", List.of(Message.user("hi")), List.of()));

        assertFalse(body.has("tools"));
        assertFalse(body.has("system"));
    }

    @Test
    void parsesTextToolUseAndUsage() {
        String json = """
                {"id":"msg_1","type":"message","role":"assistant",
                 "content":[{"type":"text","text":"Checking."},
                            {"type":"tool_use","id":"toolu_1","name":"get_campaign_summary",
                             "input":{"campaign_id":"C103"}}],
                 "stop_reason":"tool_use","usage":{"input_tokens":120,"output_tokens":45}}""";

        LlmResponse response = AnthropicClient.parseResponse(json);

        assertEquals(StopReason.TOOL_USE, response.stopReason());
        assertEquals("Checking.", response.text());
        assertEquals(1, response.toolUses().size());
        assertEquals("toolu_1", response.toolUses().get(0).id());
        assertEquals("get_campaign_summary", response.toolUses().get(0).name());
        assertEquals("C103", response.toolUses().get(0).input().get("campaign_id"));
        assertEquals(120, response.usage().inputTokens());
        assertEquals(45, response.usage().outputTokens());
    }

    @Test
    void ignoresBlockTypesItDoesNotUse() {
        String json = """
                {"content":[{"type":"thinking","thinking":"hmm"},{"type":"text","text":"Hi"}],
                 "stop_reason":"end_turn","usage":{"input_tokens":1,"output_tokens":2}}""";

        LlmResponse response = AnthropicClient.parseResponse(json);

        assertEquals(StopReason.END_TURN, response.stopReason());
        assertEquals("Hi", response.text());
    }

    @Test
    void invalidJsonBecomesAnLlmException() {
        assertThrows(LlmException.class, () -> AnthropicClient.parseResponse("not json"));
    }
}
