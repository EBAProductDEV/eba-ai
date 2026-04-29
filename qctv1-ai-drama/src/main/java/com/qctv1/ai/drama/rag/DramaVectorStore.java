package com.qctv1.ai.drama.rag;

import java.util.List;

public interface DramaVectorStore {

    void ensureIndex();

    void upsertChunk(Long seriesId, Long chunkId, String content, float[] embedding);

    List<RagSearchResult> search(Long seriesId, float[] queryEmbedding, int topK);
}
