package com.campaigndoctor.rag;

/** A retrievable passage: one section of one reference document. */
public record Chunk(String source, String heading, String text) {}
