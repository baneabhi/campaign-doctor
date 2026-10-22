package com.campaigndoctor.agent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.campaigndoctor.rag.Chunk;
import com.campaigndoctor.rag.Hit;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptsTest {

    @Test
    void embedsRetrievedChunksWithTheirSourceFile() {
        Chunk chunk = new Chunk("pacing.md", "What pacing means", "Pacing compares spend with plan.");
        String system = Prompts.system(List.of(new Hit(chunk, 2.0)));

        assertTrue(system.contains("[pacing.md] What pacing means"));
        assertTrue(system.contains("Pacing compares spend with plan."));
        assertTrue(system.contains(Prompts.ABSTAIN));
    }

    @Test
    void saysSoWhenNothingWasRetrieved() {
        assertTrue(Prompts.system(List.of()).contains("no reference context was found"));
    }
}
