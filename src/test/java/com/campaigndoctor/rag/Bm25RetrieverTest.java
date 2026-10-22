package com.campaigndoctor.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Spec for YOUR TASK #2. These fail until you implement Bm25Retriever. */
class Bm25RetrieverTest {

    private static final List<Chunk> CHUNKS = List.of(
            new Chunk("pacing.md", "Pacing",
                    "Pacing compares actual spend with expected spend over the flight."),
            new Chunk("bidding.md", "Win rate",
                    "Win rate is wins divided by bids. A low win rate means bids are below the clearing price."),
            new Chunk("frequency.md", "Frequency caps",
                    "A frequency cap limits impressions per user and can limit delivery on small audiences."));

    @Test
    void mostRelevantChunkComesFirst() {
        List<Hit> hits = new Bm25Retriever(CHUNKS).search("why is my win rate low", 3);
        assertEquals("bidding.md", hits.get(0).chunk().source());
    }

    @Test
    void returnsAtMostK() {
        List<Hit> hits = new Bm25Retriever(CHUNKS).search("win rate spend impressions", 2);
        assertEquals(2, hits.size());
    }

    @Test
    void hitsAreSortedByDescendingScore() {
        List<Hit> hits = new Bm25Retriever(CHUNKS).search("win rate spend impressions", 3);
        assertEquals(3, hits.size());
        for (int i = 1; i < hits.size(); i++) {
            assertTrue(hits.get(i - 1).score() >= hits.get(i).score());
        }
    }

    @Test
    void chunksWithNoMatchingTermAreNotReturned() {
        List<Hit> hits = new Bm25Retriever(CHUNKS).search("frequency cap", 3);
        assertEquals(1, hits.size());
        assertEquals("frequency.md", hits.get(0).chunk().source());
        assertTrue(hits.get(0).score() > 0);
    }

    @Test
    void unknownWordsReturnNothing() {
        assertTrue(new Bm25Retriever(CHUNKS).search("quantum blockchain", 3).isEmpty());
    }

    @Test
    void emptyOrStopwordOnlyQueriesReturnNothing() {
        Bm25Retriever retriever = new Bm25Retriever(CHUNKS);
        assertTrue(retriever.search("", 3).isEmpty());
        assertTrue(retriever.search("the is a", 3).isEmpty());
    }

    @Test
    void pluralsMatchSingulars() {
        List<Hit> hits = new Bm25Retriever(CHUNKS).search("wins", 3);
        assertEquals("bidding.md", hits.get(0).chunk().source());
    }

    @Test
    void aRareWordOutweighsACommonWord() {
        List<Chunk> chunks = List.of(
                new Chunk("x.md", "H1", "campaign campaign campaign campaign delivery"),
                new Chunk("y.md", "H2", "campaign viewability"));
        List<Hit> hits = new Bm25Retriever(chunks).search("campaign viewability", 2);
        assertEquals("y.md", hits.get(0).chunk().source());
    }
}
