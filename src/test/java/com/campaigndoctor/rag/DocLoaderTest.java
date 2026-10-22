package com.campaigndoctor.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocLoaderTest {

    @Test
    void splitsAtHeadings() {
        String markdown = "# Pacing\nIntro text.\n\n## Causes\nLow win rate.\n\n## Fixes\nRaise the bid.\n";
        List<Chunk> chunks = DocLoader.splitMarkdown("pacing.md", markdown);

        assertEquals(3, chunks.size());
        assertEquals(new Chunk("pacing.md", "Pacing", "Intro text."), chunks.get(0));
        assertEquals(new Chunk("pacing.md", "Causes", "Low win rate."), chunks.get(1));
        assertEquals(new Chunk("pacing.md", "Fixes", "Raise the bid."), chunks.get(2));
    }

    @Test
    void skipsHeadingsWithNoBody() {
        List<Chunk> chunks = DocLoader.splitMarkdown("a.md", "# Title\n## Empty\n## Full\nText\n");
        assertEquals(1, chunks.size());
        assertEquals("Full", chunks.get(0).heading());
    }

    @Test
    void loadsTheBundledDocs() throws IOException {
        List<Chunk> chunks = DocLoader.loadDir(Path.of("data/docs"));
        assertTrue(chunks.size() >= 15, "expected at least 15 chunks, got " + chunks.size());
        assertTrue(chunks.stream().allMatch(c -> c.source().endsWith(".md")));
        assertTrue(chunks.stream().anyMatch(c -> c.source().equals("pacing.md")));
    }
}
