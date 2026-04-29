package com.qctv1.ai.drama.rag;

public record RagSearchResult(
        Long chunkId,
        Long seriesId,
        String content,
        double score
) {
}
