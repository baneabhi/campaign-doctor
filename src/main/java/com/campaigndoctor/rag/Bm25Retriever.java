package com.campaigndoctor.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * YOUR TASK #2: a BM25 keyword retriever over chunks. Tests: Bm25RetrieverTest.
 *
 * <p>BM25 scores a chunk d against a query q as the sum, over each query term t:
 *
 * <pre>
 *   idf(t) * tf(t,d) * (K1 + 1) / ( tf(t,d) + K1 * (1 - B + B * len(d) / avgLen) )
 *   idf(t) = ln( 1 + (N - df(t) + 0.5) / (df(t) + 0.5) )
 * </pre>
 *
 * where tf is how often t appears in d, df is how many chunks contain t, N is the number of chunks,
 * len(d) is the number of tokens in d and avgLen is the mean of len(d) over all chunks.
 *
 * <p>Suggested approach:
 * <ol>
 *   <li>In the constructor, tokenize each chunk (heading + text) with {@link Tokenizer} and precompute
 *       term frequencies per chunk, document frequencies, and average length.</li>
 *   <li>In {@code search}, tokenize the query, score every chunk, keep only chunks with score &gt; 0,
 *       sort best first and return at most k.</li>
 * </ol>
 *
 * <p>Why bother with BM25 instead of embeddings? It is exact on ids and jargon ("C103", "eCPM"), needs no
 * extra service, and is a strong baseline. Swapping in embeddings later only means writing another
 * {@link Retriever}.
 */
public final class Bm25Retriever implements Retriever {

    static final double K1 = 1.5;
    static final double B = 0.75;

    private final List<Chunk> chunks;
    private final List<Map<String, Integer>> termFreqs = new ArrayList<>();
    private final int[] lengths;
    private final Map<String, Integer> docFreqs = new HashMap<>();
    private final double avgLen;

    public Bm25Retriever(List<Chunk> chunks) {
        this.chunks = List.copyOf(chunks);
        this.lengths = new int[chunks.size()];
        long total = 0;
        for (int i = 0; i < chunks.size(); i++) {
            Chunk c = chunks.get(i);
            List<String> tokens = Tokenizer.tokenize(c.heading() + " " + c.text());
            Map<String, Integer> counts = new HashMap<>();
            for (String t : tokens) {
                counts.merge(t, 1, Integer::sum);
            }
            termFreqs.add(counts);
            lengths[i] = tokens.size();
            total += tokens.size();
            for (String t : counts.keySet()) {
                docFreqs.merge(t, 1, Integer::sum);
            }
        }
        this.avgLen = chunks.isEmpty() ? 0 : (double) total / chunks.size();
    }

    @Override
    public List<Hit> search(String query, int k) {
        LinkedHashSet<String> terms = new LinkedHashSet<>(Tokenizer.tokenize(query));
        List<Hit> hits = new ArrayList<>();
        if (terms.isEmpty() || k <= 0) {
            return hits;
        }
        int n = chunks.size();
        for (int i = 0; i < n; i++) {
            double score = 0;
            for (String term : terms) {
                int f = termFreqs.get(i).getOrDefault(term, 0);
                if (f == 0) {
                    continue;
                }
                int df = docFreqs.get(term);
                double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
                score += idf * f * (K1 + 1) / (f + K1 * (1 - B + B * lengths[i] / avgLen));
            }
            if (score > 0) {
                hits.add(new Hit(chunks.get(i), score));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        return hits.size() > k ? new ArrayList<>(hits.subList(0, k)) : hits;
    }
}
