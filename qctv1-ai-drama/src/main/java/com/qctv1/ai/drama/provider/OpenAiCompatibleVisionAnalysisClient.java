package com.qctv1.ai.drama.provider;

import com.qctv1.ai.drama.config.DramaProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleVisionAnalysisClient implements VisionAnalysisClient {

    private final DramaProperties properties;
    private final WebClient webClient;

    public OpenAiCompatibleVisionAnalysisClient(DramaProperties properties, WebClient.Builder builder) {
        this.properties = properties;
        this.webClient = builder.build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String analyzeShotFrames(String prompt, List<Path> framePaths, String modelName) {
        return analyzeImages(
                "你是短剧剪辑视觉分析助手，只返回用户要求的 JSON。",
                prompt,
                framePaths,
                modelName,
                1600,
                0.1
        );
    }

    @Override
    public String generateShotFrameText(String prompt, List<Path> framePaths, String modelName) {
        return analyzeImages(
                "你是专业短剧导演、分镜导演和 AI 视频提示词工程师。只返回用户要求的最终中文提示词，不要 Markdown，不要解释。",
                prompt,
                framePaths,
                modelName,
                2600,
                0.35
        );
    }

    @SuppressWarnings("unchecked")
    private String analyzeImages(String systemPrompt, String prompt, List<Path> framePaths, String modelName, int maxTokens, double temperature) {
        DramaProperties.ModelConfig config = resolveConfig();
        if (!config.isReady()) {
            throw new IllegalStateException("视觉模型未配置，请配置 DRAMA_VISION_BASE_URL / DRAMA_VISION_API_KEY / DRAMA_VISION_MODEL");
        }
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", modelName == null || modelName.isBlank() ? config.getModel() : modelName);
        requestBody.put("temperature", temperature);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", buildContent(prompt, framePaths))
        ));

        Map<?, ?> response = webClient.post()
                .uri(resolveChatCompletionsUrl(config.getBaseUrl(), config.getCompletionsPath()))
                .header("Authorization", "Bearer " + config.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(180));
        return extractContent(response);
    }

    private DramaProperties.ModelConfig resolveConfig() {
        return properties.getVision() != null && properties.getVision().isReady()
                ? properties.getVision()
                : properties.getText();
    }

    private List<Map<String, Object>> buildContent(String prompt, List<Path> framePaths) {
        List<Map<String, Object>> content = new java.util.ArrayList<>();
        content.add(Map.of("type", "text", "text", prompt));
        for (Path framePath : framePaths) {
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", dataUrl(framePath), "detail", "high")
            ));
        }
        return content;
    }

    private String dataUrl(Path imagePath) {
        try {
            String contentType = Files.probeContentType(imagePath);
            if (contentType == null || contentType.isBlank()) {
                contentType = "image/jpeg";
            }
            String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath));
            return "data:" + contentType + ";base64," + base64;
        } catch (IOException ex) {
            throw new IllegalStateException("读取关键帧失败：" + imagePath, ex);
        }
    }

    private String resolveChatCompletionsUrl(String baseUrl, String path) {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path == null || path.isBlank() ? "/chat/completions" : path;
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        return normalizedBase + normalizedPath;
    }

    private String extractContent(Map<?, ?> response) {
        if (response == null) {
            return "";
        }
        Object choicesValue = response.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()) {
            return "";
        }
        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> firstChoice)) {
            return "";
        }
        Object messageValue = firstChoice.get("message");
        if (messageValue instanceof Map<?, ?> message) {
            Object content = message.get("content");
            return content == null ? "" : content.toString();
        }
        Object text = firstChoice.get("text");
        return text == null ? "" : text.toString();
    }
}
