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

/** Agentic RAG: the model calls search_docs itself. Nothing is retrieved up front. */
class AgenticAgentTest {

    private static final Chunk PACING_CHUNK = new Chunk("pacing.md", "What pacing means",
            "Pacing compares actual spend with expected spend.");

    private final List<String> searches = new ArrayList<>();
    private final Retriever recording = (query, k) -> {
        searches.add(query);
        return List.of(new Hit(PACING_CHUNK, 1.0));
    };

    private Agent agent(FakeLlmClient llm) {
        Campaign alpha = new Campaign("C1", "Alpha Campaign", 1000, LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 10), 5.0, 3, 100_000);
        ToolRegistry tools = new ToolRegistry(new CampaignRepository(List.of(alpha), List.of()), recording);
        return new Agent(llm, recording, tools, AgentConfig.agentic());
    }

    @Test
    void doesNotRetrieveBeforeTheFirstModelCall() {
        FakeLlmClient llm = FakeLlmClient.scripted(text("Hello.", 10, 2));

        agent(llm).ask("hi");

        assertTrue(searches.isEmpty(), "retrieval is the model's decision now");
        assertEquals(Prompts.systemAgentic(), llm.calls.get(0).system());
        assertTrue(llm.calls.get(0).tools().stream().anyMatch(t -> t.name().equals("search_docs")));
    }

    @Test
    void searchResultsCountAsRetrievedSourcesAndToolsCalled() {
        FakeLlmClient llm = FakeLlmClient.scripted(
                toolCall("t1", "search_docs", Map.of("query", "what is pacing"), 50, 10),
                text("Pacing compares spend with plan [pacing.md].", 80, 15));

        AgentResult result = agent(llm).ask("What is pacing?");

        assertEquals(List.of("what is pacing"), searches);
        assertEquals(List.of("search_docs"), result.toolsCalled());
        assertEquals(List.of("pacing.md"), result.retrievedSources());
        assertEquals(List.of("pacing.md"), result.citations());
        assertTrue(result.retrievedSources().containsAll(result.citations()), "cited must be a subset of retrieved");
    }

    @Test
    void retrievedSourcesIsEmptyWhenTheModelNeverSearches() {
        FakeLlmClient llm = FakeLlmClient.scripted(text("I don't have enough information to answer that.", 10, 8));

        AgentResult result = agent(llm).ask("What is the weather in Sydney?");

        assertTrue(result.retrievedSources().isEmpty());
        assertTrue(result.toolsCalled().isEmpty());
    }

    @Test
    void theSearchResultIsSentBackToTheModel() {
        FakeLlmClient llm = FakeLlmClient.scripted(
                toolCall("t1", "search_docs", Map.of("query", "pacing"), 50, 10),
                text("Done.", 80, 5));

        agent(llm).ask("What is pacing?");

        Message last = llm.calls.get(1).messages().get(llm.calls.get(1).messages().size() - 1);
        Block.ToolResult result = (Block.ToolResult) last.blocks().get(0);
        assertEquals("t1", result.toolUseId());
        assertFalse(result.isError());
        assertTrue(result.content().contains("Pacing compares actual spend with expected spend."));
    }

    @Test
    void chunksFromAnEarlierQuestionStayInHistoryForFollowUps() {
        // This is the Q3 bug fixed: with prompt stuffing the chunks vanished from the next turn's prompt.
        FakeLlmClient llm = FakeLlmClient.scripted(
                toolCall("t1", "search_docs", Map.of("query", "pacing"), 50, 10),
                text("Pacing compares spend with plan [pacing.md].", 80, 15),
                text("As I said, it compares spend with plan [pacing.md].", 120, 15));
        Agent agent = agent(llm);

        agent.ask("What is pacing?");
        agent.ask("Can you repeat that?");

        List<Message> secondTurn = llm.calls.get(2).messages();
        boolean chunkStillThere = secondTurn.stream().flatMap(m -> m.blocks().stream())
                .anyMatch(b -> b instanceof Block.ToolResult r
                        && r.content().contains("Pacing compares actual spend with expected spend."));
        assertTrue(chunkStillThere);
        assertEquals(1, searches.size(), "the follow-up should not need a second search");
    }

    @Test
    void theSystemPromptDoesNotChangeBetweenQuestions() {
        FakeLlmClient llm = FakeLlmClient.scripted(text("One.", 10, 2), text("Two.", 20, 2));
        Agent agent = agent(llm);

        agent.ask("first");
        agent.ask("second");

        assertEquals(llm.calls.get(0).system(), llm.calls.get(1).system());
    }
}