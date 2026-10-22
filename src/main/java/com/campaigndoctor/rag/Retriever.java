package com.campaigndoctor.rag;

import java.util.List;

public interface Retriever {

    /**
     * Returns up to {@code k} chunks relevant to the query, best first.
     * Returns an empty list when nothing matches.
     */
    List<Hit> search(String query, int k);
}
