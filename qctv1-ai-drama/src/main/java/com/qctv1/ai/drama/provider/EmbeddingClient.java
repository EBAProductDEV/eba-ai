package com.qctv1.ai.drama.provider;

public interface EmbeddingClient {

    float[] embed(String text);
}
