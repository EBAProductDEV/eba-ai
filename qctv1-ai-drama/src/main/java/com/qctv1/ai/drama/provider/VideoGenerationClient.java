package com.qctv1.ai.drama.provider;

import java.nio.file.Path;

public interface VideoGenerationClient {

    VideoTask submitVideoTask(VideoRequest request);

    VideoTaskStatus queryVideoTask(String providerTaskId);

    VideoFile downloadVideo(String videoUrl);

    record VideoRequest(
            String prompt,
            Path referenceImage,
            Integer seconds,
            String ratio,
            String resolution
    ) {
    }

    record VideoTask(String providerTaskId) {
    }

    record VideoTaskStatus(
            String providerTaskId,
            String status,
            Integer progress,
            String videoUrl,
            String errorMessage
    ) {
    }

    record VideoFile(
            byte[] content,
            String contentType,
            String fileExtension
    ) {
    }
}
