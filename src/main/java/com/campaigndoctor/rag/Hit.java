package com.campaigndoctor.rag;

/** A chunk returned by a retriever, with its relevance score (higher is better). */
public record Hit(Chunk chunk, double score) {}
