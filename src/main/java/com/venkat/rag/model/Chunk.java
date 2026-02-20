package com.venkat.rag.model;

public record Chunk(
    String chunkId,
    String documentId,
    String documentTitle,
    String source,
    int chunkIndex,
    String text
) {}
