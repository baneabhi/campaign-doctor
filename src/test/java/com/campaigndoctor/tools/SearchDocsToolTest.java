package com.campaigndoctor.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.campaigndoctor.llm.ToolDef;
import com.campaigndoctor.rag.Chunk;
import com.campaigndoctor.rag.Hit;
import com.campaigndoctor.rag.Retriever;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Spec for the search_docs tool (step 2, agentic RAG). With it, the model decides when to search the reference docs
 * instead of us stuffing the top chunks into the system prompt on every question.
 */
class SearchDocsToolTest {

    private static final Chunk PACING = new Chunk("pacing.md", "What pacing means",
            "Pacing compares actual spend with expected spend.");
    private static final Chunk BIDDING = new Chunk("bidding-and-win-rate.md", "Low win rate",
            "A low win rate often means bids are below the clearing price.");

    private static CampaignRepository repo() {
        Campaign alpha = new Campaign("C1", "Alpha Campaign", 1000, LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 10), 5.0, 3, 100_000);
        return new CampaignRepository(List.of(alpha), List.of());
    }

    private static ToolDef searchDef(ToolRegistry registry) {
        return registry.definitions().stream().filter(d -> d.name().equals("search_docs")).findFirst().orElseThrow();
    }

    @Test
    void searchDocsIsOnlyOfferedWhenThereIsARetriever() {
        assertFalse(new ToolRegistry(repo()).definitions().stream().anyMatch(d -> d.name().equals("search_docs")));
        assertTrue(new ToolRegistry(repo(), (q, k) -> List.of()).definitions().stream()
                .anyMatch(d -> d.name().equals("search_docs")));
    }

    @Test
    void theSchemaRequiresAQueryString() {
        ToolDef def = searchDef(new ToolRegistry(repo(), (q, k) -> List.of()));

        assertEquals("object", def.inputSchema().get("type"));
        assertEquals(List.of("query"), def.inputSchema().get("required"));
        assertFalse(def.description().isBlank());
    }

    @Test
    void returnsEachHitAsSourceHeadingAndText() {
        Retriever retriever = (q, k) -> List.of(new Hit(PACING, 2.0), new Hit(BIDDING, 1.0));

        ToolOutput out = new ToolRegistry(repo(), retriever).execute("search_docs", Map.of("query", "pacing"));

        assertFalse(out.isError());
        assertTrue(out.content().contains("[pacing.md] What pacing means"), out.content());
        assertTrue(out.content().contains("Pacing compares actual spend with expected spend."), out.content());
        assertTrue(out.content().contains("[bidding-and-win-rate.md] Low win rate"), out.content());
        assertTrue(out.content().indexOf("[pacing.md]") < out.content().indexOf("[bidding-and-win-rate.md]"),
                "best hit first");
    }

    @Test
    void passesTheModelsQueryToTheRetriever() {
        List<String> queries = new ArrayList<>();
        Retriever retriever = (q, k) -> {
            queries.add(q);
            return List.of();
        };

        new ToolRegistry(repo(), retriever).execute("search_docs", Map.of("query", "why is delivery low"));

        assertEquals(List.of("why is delivery low"), queries);
    }

    @Test
    void asksForAFewChunksNotTheWholeCorpus() {
        List<Integer> ks = new ArrayList<>();
        Retriever retriever = (q, k) -> {
            ks.add(k);
            return List.of();
        };

        new ToolRegistry(repo(), retriever).execute("search_docs", Map.of("query", "pacing"));

        assertEquals(1, ks.size());
        assertTrue(ks.get(0) >= 1 && ks.get(0) <= 5, "k was " + ks.get(0));
    }

    @Test
    void noMatchesIsAnAnswerNotAnError() {
        var query = "weather in Sydney";
        ToolOutput out = new ToolRegistry(repo(), (q, k) -> List.of())
                .execute("search_docs", Map.of("query", query));
        assertFalse(out.isError(), "the model should read this and decide to abstain");
        assertTrue(out.content().toLowerCase().contains("no matching"), out.content());
    }

    @Test
    void aBlankOrMissingQueryIsAnErrorTheModelCanRecoverFrom() {
        ToolRegistry registry = new ToolRegistry(repo(), (q, k) -> List.of(new Hit(PACING, 1.0)));

        ToolOutput blank = registry.execute("search_docs", Map.of("query", "  "));
        ToolOutput missing = registry.execute("search_docs", Map.of());

        assertTrue(blank.isError());
        assertTrue(blank.content().contains("query"), blank.content());
        assertTrue(missing.isError());
        assertTrue(missing.content().contains("query"), missing.content());
    }

    @Test
    void callingSearchDocsWithoutARetrieverIsAnUnknownToolError() {
        assertTrue(new ToolRegistry(repo()).execute("search_docs", Map.of("query", "pacing")).isError());
    }
}