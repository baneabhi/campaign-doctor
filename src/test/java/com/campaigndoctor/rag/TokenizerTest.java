package com.campaigndoctor.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TokenizerTest {

    @Test
    void lowercasesSplitsOnPunctuationAndDropsStopwords() {
        assertEquals(List.of("win", "rate", "low"), Tokenizer.tokenize("Why is my Win-Rate low?"));
    }

    @Test
    void stripsSimplePlurals() {
        assertEquals(List.of("bid", "campaign"), Tokenizer.tokenize("Bids and campaigns"));
    }

    @Test
    void keepsIdsAndDoesNotStemShortWords() {
        assertEquals(List.of("c103", "pacing"), Tokenizer.tokenize("C103 pacing"));
    }

    @Test
    void nullAndBlankGiveNoTokens() {
        assertTrue(Tokenizer.tokenize(null).isEmpty());
        assertTrue(Tokenizer.tokenize("  ").isEmpty());
    }
}
