package com.campaigndoctor.agent;

import static com.campaigndoctor.agent.FakeLlmClient.text;
import static com.campaigndoctor.agent.FakeLlmClient.toolCall;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.campaigndoctor.llm.Block;
import com.campaigndoctor.llm.Message;
import com.campaigndoctor.rag.Chunk;
import com.campaigndoctor.rag.Hit;
import com.campaigndoctor.rag.Retriever;
import com.campaigndoctor.tools.Campaign;
import com.campaigndoctor.tools.CampaignRepository;
import com.campaigndoctor.tools.ToolRegistry;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec for YOUR TASK #3. A fake LlmClient stands in for the model, so no API key is needed. */
class AgentTest {

    private static final Chunk PACING_CHUNK = new Chunk("pacing.md", "What pacing means",
            "Pacing compares actual spend with expected spend.");
    private static final Retriever ONE_HIT = (query, k) -> List.of(new Hit(PACING_CHUNK, 1.0));
    private static final Retriever NO_HITS = (query, k) -> List.of();

    private static ToolRegistry tools() {
        Campaign alpha = new Campaign("C1", "Alpha Campaign", 1000, LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 10), 5.0, 3, 100_000);
        return new ToolRegistry(new CampaignRepository(List.of(alpha), List.of()));
    }

    @Test
    void answersFromRetrievedContextAndReportsCitations() {
        FakeLlmClient llm = FakeLlmClient.scripted(text("Pacing compares spend with plan [pacing.md].", 100, 20));
        Agent agent = new Agent(llm, ONE_HIT, tools(), AgentConfig.defaults());

        AgentResult result = agent.ask("What is pacing?");

        assertEquals("Pacing compares spend with plan [pacing.md].", result.text());
        assertEquals(List.of("pacing.md"), result.citations());
        assertEquals(List.of("pacing.md"), result.retrievedSources());
        assertTrue(result.toolsCalled().isEmpty());
        assertEquals(100, result.inputTokens());
        assertEquals(20, result.outputTokens());
        assertEquals(1, llm.calls.size());
        assertTrue(llm.calls.get(0).system().contains("Pacing compares actual spend with expected spend."),
                "the retrieved chunk should be in the system prompt");
        assertEquals(3, llm.calls.get(0).tools().size(), "the tool definitions should be offered to the model");
    }

    @Test
    void runsRequestedToolsThenReturnsTheFinalAnswer() {
        FakeLlmClient llm = FakeLlmClient.scripted(
                toolCall("t1", "list_campaigns", Map.of(), 50, 10),
                text("There is one campaign: Alpha.", 70, 15));
        Agent agent = new Agent(llm, NO_HITS, tools(), AgentConfig.defaults());

        AgentResult result = agent.ask("Which campaigns exist?");

        assertEquals("There is one campaign: Alpha.", result.text());
        assertEquals(List.of("list_campaigns"), result.toolsCalled());
        assertEquals(120, result.inputTokens());
        assertEquals(25, result.outputTokens());
        assertEquals(2, llm.calls.size());

        List<Message> secondCall = llm.calls.get(1).messages();
        assertEquals(3, secondCall.size()); // question, tool request, tool result
        assertEquals(Message.Role.ASSISTANT, secondCall.get(1).role());
        Message last = secondCall.get(2);
        assertEquals(Message.Role.USER, last.role());
        Block.ToolResult toolResult = (Block.ToolResult) last.blocks().get(0);
        assertEquals("t1", toolResult.toolUseId());
        assertFalse(toolResult.isError());
        assertTrue(toolResult.content().contains("Alpha Campaign"));
    }

    @Test
    void passesToolErrorsBackToTheModelInsteadOfThrowing() {
        FakeLlmClient llm = FakeLlmClient.scripted(
                toolCall("t1", "get_campaign_summary", Map.of("campaign_id", "NOPE"), 50, 10),
                text("I don't have enough information about NOPE.", 70, 15));
        Agent agent = new Agent(llm, NO_HITS, tools(), AgentConfig.defaults());

        AgentResult result = agent.ask("How is NOPE pacing?");

        assertEquals("I don't have enough information about NOPE.", result.text());
        Block.ToolResult toolResult = (Block.ToolResult) llm.calls.get(1).messages().get(2).blocks().get(0);
        assertTrue(toolResult.isError());
    }

    @Test
    void stopsWhenTheModelKeepsAskingForTools() {
        FakeLlmClient llm = FakeLlmClient.alwaysReturning(toolCall("t", "list_campaigns", Map.of(), 10, 5));
        Agent agent = new Agent(llm, NO_HITS, tools(), new AgentConfig(4, 3));

        AgentResult result = agent.ask("Loop forever");

        assertEquals(3, llm.calls.size());
        assertEquals(Prompts.TOOL_LIMIT_MESSAGE, result.text());
    }

    @Test
    void keepsConversationHistoryUntilReset() {
        FakeLlmClient llm = FakeLlmClient.scripted(text("one", 1, 1), text("two", 1, 1), text("three", 1, 1));
        List<String> queries = new ArrayList<>();
        Retriever recording = (query, k) -> {
            queries.add(query);
            return List.of();
        };
        Agent agent = new Agent(llm, recording, tools(), AgentConfig.defaults());

        agent.ask("first");
        agent.ask("second");

        List<Message> secondCall = llm.calls.get(1).messages();
        assertEquals(3, secondCall.size());
        assertEquals("first", secondCall.get(0).text());
        assertEquals("one", secondCall.get(1).text());
        assertEquals("second", secondCall.get(2).text());
        assertEquals(List.of("first", "second"), queries); // retrieval happens for every question

        agent.reset();
        agent.ask("third");
        assertEquals(1, llm.calls.get(2).messages().size());
    }
}
