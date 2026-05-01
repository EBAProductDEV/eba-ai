package com.qctv1.ai.drama.provider;

import java.nio.file.Path;
import java.util.List;

public interface ImageGenerationClient {

    String submitImageTask(String prompt);

    ImageResult generateImage(String prompt);

    ImageResult generateImage(String prompt, String imageSize);

    ImageResult editImage(String prompt, List<Path> referenceImages);

    ImageResult editImage(String prompt, List<Path> referenceImages, String imageSize);

    record ImageResult(
            byte[] content,
            String contentType,
            String fileExtension,
            String providerTaskId
    ) {
    }
}
