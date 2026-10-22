package com.campaigndoctor.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CitationsTest {

    @Test
    void extractsDistinctFileCitationsInOrder() {
        String text = "Use [pacing.md] and [bidding-and-win-rate.md]; see [pacing.md] again. Also [not a file].";
        assertEquals(List.of("pacing.md", "bidding-and-win-rate.md"), Citations.extract(text));
    }

    @Test
    void noCitationsGivesEmptyList() {
        assertTrue(Citations.extract("Nothing cited here.").isEmpty());
        assertTrue(Citations.extract(null).isEmpty());
    }
}
