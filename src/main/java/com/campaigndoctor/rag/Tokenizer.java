package com.campaigndoctor.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns text into search terms: lowercase, split on anything that isn't a letter or digit,
 * drop stopwords and one-letter tokens, and strip a trailing plural "s" so "bids" matches "bid".
 * Use the same tokenizer for documents and queries.
 */
public final class Tokenizer {

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "can", "do", "does", "for", "from",
            "has", "have", "how", "i", "in", "is", "it", "its", "my", "of", "on", "or", "should",
            "than", "that", "the", "their", "them", "then", "this", "to", "was", "we", "what",
            "when", "where", "which", "why", "with", "you", "your");

    private Tokenizer() {}

    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) {
            return tokens;
        }
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (raw.length() < 2 || STOPWORDS.contains(raw)) {
                continue;
            }
            tokens.add(stem(raw));
        }
        return tokens;
    }

    static String stem(String token) {
        if (token.length() > 3 && token.endsWith("s") && !token.endsWith("ss")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }
}
