package com.qctv1.ai.drama.provider;

import java.nio.file.Path;
import java.util.List;

public interface VisionAnalysisClient {

    String analyzeShotFrames(String prompt, List<Path> framePaths, String modelName);

    String generateShotFrameText(String prompt, List<Path> framePaths, String modelName);
}
