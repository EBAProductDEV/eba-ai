package com.qctv1.ai.drama.provider;

import com.qctv1.ai.drama.config.DramaProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.FileSystemResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OpenAiCompatibleProviderClients implements TextGenerationClient, ImageGenerationClient, VideoGenerationClient, EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProviderClients.class);


    private final DramaProperties properties;
    private final WebClient webClient;
    private final HttpClient imageHttpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiCompatibleProviderClients(DramaProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(20 * 1024 * 1024))
                .build();
        this.imageHttpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String generate(String prompt) {
        if (!properties.getText().isReady()) {
            return "模型配置未完成，已返回短剧文本生成占位结果。请配置 DRAMA_TEXT_BASE_URL / DRAMA_TEXT_API_KEY / DRAMA_TEXT_MODEL 后接入真实生成。";
        }
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", properties.getText().getModel());
            requestBody.put("temperature", 0.75);
            requestBody.put("max_tokens", resolveMaxTokens(properties.getText().getMaxTokens()));
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", "你是企业级短剧编剧和制作策划，只输出用户要求的内容。"),
                    Map.of("role", "user", "content", prompt)
            ));
            Map<?, ?> response = webClient.post()
                    .uri(resolveChatCompletionsUrl(properties.getText().getBaseUrl(), properties.getText().getCompletionsPath()))
                    .header("Authorization", "Bearer " + properties.getText().getApiKey())
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(240));
            return extractContent(response);
        } catch (Exception ex) {
            return "模型调用失败：" + ex.getMessage();
        }
    }

    @Override
    public String submitImageTask(String prompt) {
        return "mock-image-" + UUID.randomUUID();
    }

    @Override
    public ImageResult generateImage(String prompt) {
        return generateImage(prompt, null, null, null);
    }

    @Override
    public ImageResult generateImage(String prompt, String imageSize, String imageQuality, String imageFormat) {
        if (!properties.getImage().isReady()) {
            return null;
        }
        String url = resolveImagesUrl(properties.getImage().getBaseUrl(), properties.getImage().getImagesPath());
        String requestBody = buildImageGenerationRequestBody(prompt, imageSize, imageQuality, imageFormat);
        byte[] bodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);
        try {
            // 图片供应商对请求格式比较敏感，这里使用 Java HttpClient，并让请求体与已验证通过的独立 Java 探针保持一致。
            log.info("[DRAMA_IMAGE_REQUEST] url={}, bytes={}, prompt={}", url, bodyBytes.length, abbreviate(prompt));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(180))
                    .header("Authorization", "Bearer " + properties.getImage().getApiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "*/*")
                    .header("User-Agent", "python-requests/2.31.0")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                    .build();
            long startMillis = System.currentTimeMillis();
            HttpResponse<byte[]> response = imageHttpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            log.info(
                    "[DRAMA_IMAGE_RESPONSE] status={}, elapsedMs={}, contentType={}, bytes={}",
                    response.statusCode(),
                    System.currentTimeMillis() - startMillis,
                    response.headers().firstValue("content-type").orElse(""),
                    response.body() == null ? 0 : response.body().length
            );
            return handleHttpImageResponse(response, "图片生成接口");
        } catch (WebClientResponseException ex) {
            throw new IllegalStateException("图片生成接口调用失败：" + ex.getStatusCode() + " " + ex.getResponseBodyAsString(), ex);
        } catch (IOException ex) {
            throw new IllegalStateException("图片生成接口调用失败：" + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("图片生成接口调用被中断", ex);
        }
    }
    @Override
    public ImageResult editImage(String prompt, List<Path> referenceImages) {
        return editImage(prompt, referenceImages, null, null, null);
    }

    @Override
    public ImageResult editImage(String prompt, List<Path> referenceImages, String imageSize, String imageQuality, String imageFormat) {
        if (!properties.getImage().isReady()) {
            return null;
        }
        if (referenceImages == null || referenceImages.isEmpty()) {
            return generateImage(prompt, imageSize, imageQuality, imageFormat);
        }
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("model", properties.getImage().getModel());
            for (Path imagePath : referenceImages.stream().limit(16).toList()) {
                builder.part("image", new FileSystemResource(imagePath));
            }
            builder.part("prompt", prompt);
            builder.part("n", "1");
            builder.part("size", imageSize(imageSize));
            builder.part("quality", imageQuality(imageQuality));
            builder.part("output_format", imageOutputFormat(imageFormat));
            builder.part("output_compression", String.valueOf(imageOutputCompression()));
            builder.part("background", "auto");
            builder.part("moderation", "auto");
            log.info(
                    "[DRAMA_IMAGE_EDIT_REQUEST] url={}, refs={}, prompt={}",
                    resolveImagesUrl(properties.getImage().getBaseUrl(), properties.getImage().getImageEditsPath()),
                    referenceImages.size(),
                    abbreviate(prompt)
            );
            return webClient.post()
                    .uri(resolveImagesUrl(properties.getImage().getBaseUrl(), properties.getImage().getImageEditsPath()))
                    .header("Authorization", "Bearer " + properties.getImage().getApiKey())
                    .header("Accept", "*/*")
                    .header("User-Agent", "python-requests/2.31.0")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .exchangeToMono(response -> handleImageResponse(response, "图片编辑接口"))
                    .block(Duration.ofSeconds(420));
        } catch (WebClientResponseException ex) {
            throw new IllegalStateException("图片编辑接口调用失败：" + ex.getStatusCode() + " " + ex.getResponseBodyAsString(), ex);
        }
    }

    @Override
    public VideoTask submitVideoTask(VideoRequest request) {
        if (!properties.getVideo().isReady()) {
            throw new IllegalStateException("视频模型未配置，无法生成真实视频");
        }
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("model", properties.getVideo().getModel());
            builder.part("prompt", request.prompt());
            builder.part("input_reference", buildVideoReferenceDataUri(request.referenceImage(), request.ratio()));
            int videoSeconds = resolveVideoSeconds(request.seconds());
            builder.part("duration", String.valueOf(videoSeconds));
            builder.part("seconds", String.valueOf(videoSeconds));
            builder.part("duration_seconds", String.valueOf(videoSeconds));
            builder.part("width", String.valueOf(resolveVideoWidth(request.ratio(), request.resolution())));
            builder.part("height", String.valueOf(resolveVideoHeight(request.ratio(), request.resolution())));
            builder.part("fps", String.valueOf(resolveVideoFps(request.fps())));
            builder.part("n", "1");
            builder.part("response_format", "url");
            Map<?, ?> response = webClient.post()
                    .uri(resolveVideosUrl(properties.getVideo().getBaseUrl(), properties.getVideo().getVideosPath()))
                    .header("Authorization", "Bearer " + properties.getVideo().getApiKey())
                    .header("Accept", "application/json,*/*")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(180));
            String providerTaskId = extractVideoTaskId(response);
            if (providerTaskId == null || providerTaskId.isBlank()) {
                throw new IllegalStateException("视频模型没有返回任务ID：" + abbreviate(response == null ? "" : response.toString()));
            }
            return new VideoTask(providerTaskId);
        } catch (WebClientResponseException ex) {
            throw new IllegalStateException("视频生成任务提交失败：" + ex.getStatusCode() + " " + abbreviate(ex.getResponseBodyAsString()), ex);
        } catch (IOException ex) {
            throw new IllegalStateException("视频首帧图处理失败：" + ex.getMessage(), ex);
        }
    }

    @Override
    public VideoTaskStatus queryVideoTask(String providerTaskId) {
        if (!properties.getVideo().isReady()) {
            throw new IllegalStateException("视频模型未配置，无法查询视频任务");
        }
        try {
            Map<?, ?> response = webClient.get()
                    .uri(resolveVideosUrl(properties.getVideo().getBaseUrl(), properties.getVideo().getVideosPath()) + "/" + providerTaskId)
                    .header("Authorization", "Bearer " + properties.getVideo().getApiKey())
                    .header("Accept", "application/json,*/*")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(60));
            return extractVideoTaskStatus(providerTaskId, response);
        } catch (WebClientResponseException ex) {
            String body = ex.getResponseBodyAsString();
            // NewAPI 某些渠道在任务未完全可查时会返回 model 为空的 403；这里交给上层继续轮询。
            throw new IllegalStateException("视频任务查询失败：" + ex.getStatusCode() + " " + abbreviate(body), ex);
        }
    }

    @Override
    public VideoFile downloadVideo(String videoUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(videoUrl))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(300))
                    .header("Accept", "video/mp4,video/*,*/*")
                    .header("User-Agent", "python-requests/2.31.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = imageHttpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] bytes = response.body() == null ? new byte[0] : response.body();
            String contentType = response.headers().firstValue("content-type").orElse("application/octet-stream");
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("视频文件下载失败：" + response.statusCode() + " " + abbreviate(toUtf8(bytes)));
            }
            if (bytes.length < 1024) {
                throw new IllegalStateException("视频文件下载结果过小，疑似无效响应：" + abbreviate(toUtf8(bytes)));
            }
            return new VideoFile(bytes, contentType == null || contentType.isBlank() ? "video/mp4" : contentType, ".mp4");
        } catch (IOException ex) {
            throw new IllegalStateException("视频文件下载失败：" + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("视频文件下载被中断", ex);
        }
    }

    @Override
    public float[] embed(String text) {
        return new float[properties.getVectorStore().getEmbeddingDimension()];
    }

    private int resolveMaxTokens(Integer maxTokens) {
        if (maxTokens == null || maxTokens <= 0) {
            return 12000;
        }
        return Math.min(maxTokens, 32000);
    }

    private String resolveChatCompletionsUrl(String baseUrl, String completionsPath) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        String path = completionsPath == null || completionsPath.isBlank() ? "/chat/completions" : completionsPath.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (normalized.endsWith(path)) {
            return normalized;
        }
        return normalized + path;
    }

    private String resolveImagesUrl(String baseUrl, String imagesPath) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        String path = imagesPath == null || imagesPath.isBlank() ? "/images/generations" : imagesPath.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (normalized.endsWith(path)) {
            return normalized;
        }
        return normalized + path;
    }

    private String resolveVideosUrl(String baseUrl, String videosPath) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        String path = videosPath == null || videosPath.isBlank() ? "/videos" : videosPath.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (normalized.endsWith(path)) {
            return normalized;
        }
        return normalized + path;
    }

    private String buildVideoReferenceDataUri(Path referenceImage, String ratio) throws IOException {
        if (referenceImage == null || !Files.exists(referenceImage) || !Files.isRegularFile(referenceImage)) {
            throw new IOException("视频首帧图不存在：" + referenceImage);
        }
        BufferedImage source = ImageIO.read(referenceImage.toFile());
        if (source == null) {
            throw new IOException("视频首帧图不是可识别图片：" + referenceImage);
        }
        int canvasWidth = resolveVideoWidth(ratio, "720p");
        int canvasHeight = resolveVideoHeight(ratio, "720p");
        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setColor(new Color(10, 10, 10));
            graphics.fillRect(0, 0, canvasWidth, canvasHeight);
            double scale = Math.min((double) canvasWidth / source.getWidth(), (double) canvasHeight / source.getHeight());
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            int x = (canvasWidth - width) / 2;
            int y = (canvasHeight - height) / 2;
            graphics.drawImage(source, x, y, width, height, null);
        } finally {
            graphics.dispose();
        }
        byte[] jpeg = encodeJpeg(canvas, 0.72f);
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg);
    }

    private byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(Math.max(0.1f, Math.min(quality, 1.0f)));
            }
            writer.write(null, new IIOImage(image, null, null), param);
            return output.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private String extractVideoTaskId(Map<?, ?> response) {
        if (response == null) {
            return null;
        }
        Object taskId = response.get("task_id");
        if (taskId == null) {
            taskId = response.get("id");
        }
        if (taskId == null && response.get("data") instanceof Map<?, ?> data) {
            taskId = data.get("task_id") == null ? data.get("id") : data.get("task_id");
        }
        return taskId == null ? null : taskId.toString();
    }

    private VideoTaskStatus extractVideoTaskStatus(String providerTaskId, Map<?, ?> response) {
        if (response == null) {
            return new VideoTaskStatus(providerTaskId, "unknown", 0, null, "视频任务查询返回为空");
        }
        Object statusValue = response.get("status");
        Object progressValue = response.get("progress");
        Object errorValue = response.get("error");
        String videoUrl = extractVideoUrl(response);
        if (statusValue == null && response.get("data") instanceof Map<?, ?> data) {
            statusValue = data.get("status");
            progressValue = data.get("progress");
            errorValue = data.get("error");
            videoUrl = videoUrl == null ? extractVideoUrl(data) : videoUrl;
        }
        Integer progress = null;
        if (progressValue instanceof Number number) {
            progress = number.intValue();
        } else if (progressValue != null) {
            try {
                progress = Integer.parseInt(progressValue.toString());
            } catch (NumberFormatException ignored) {
                progress = null;
            }
        }
        return new VideoTaskStatus(
                providerTaskId,
                statusValue == null ? "" : statusValue.toString(),
                progress,
                videoUrl,
                errorValue == null ? null : errorValue.toString()
        );
    }

    private String extractVideoUrl(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object content = map.get("content");
            if (content instanceof Map<?, ?> contentMap) {
                Object videoUrl = contentMap.get("video_url");
                if (videoUrl != null && videoUrl.toString().startsWith("http")) {
                    return videoUrl.toString();
                }
            }
            for (String key : List.of("video_url", "url", "download_url", "content_url")) {
                Object url = map.get(key);
                if (url != null && url.toString().startsWith("http")) {
                    return url.toString();
                }
            }
            for (Object item : map.values()) {
                String nested = extractVideoUrl(item);
                if (nested != null) {
                    return nested;
                }
            }
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String nested = extractVideoUrl(item);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private int resolveVideoSeconds(Integer seconds) {
        if (seconds == null || seconds <= 0) {
            return 10;
        }
        return Math.max(9, Math.min(seconds, 12));
    }

    private int resolveVideoWidth(String ratio, String resolution) {
        int shortSide = resolveVideoShortSide(resolution);
        return "9:16".equals(ratio) ? shortSide : Math.round(shortSide * 16f / 9f);
    }

    private int resolveVideoHeight(String ratio, String resolution) {
        int shortSide = resolveVideoShortSide(resolution);
        return "9:16".equals(ratio) ? Math.round(shortSide * 16f / 9f) : shortSide;
    }

    private int resolveVideoShortSide(String resolution) {
        if ("1080p".equalsIgnoreCase(resolution)) {
            return 1080;
        }
        if ("480p".equalsIgnoreCase(resolution)) {
            return 480;
        }
        return 720;
    }

    private int resolveVideoFps(Integer fps) {
        if (fps == null || fps <= 0) {
            return 24;
        }
        return Math.max(12, Math.min(fps, 30));
    }

    private ImageResult extractImageResult(Map<?, ?> response) {
        if (response == null) {
            throw new IllegalStateException("图片模型没有返回结果");
        }
        Object dataValue = response.get("data");
        if (!(dataValue instanceof List<?> data) || data.isEmpty() || !(data.get(0) instanceof Map<?, ?> first)) {
            throw new IllegalStateException("图片模型返回格式不正确");
        }
        Object b64 = first.get("b64_json");
        if (b64 != null && !b64.toString().isBlank()) {
            String outputFormat = imageOutputFormat();
            return new ImageResult(Base64.getDecoder().decode(b64.toString()), imageContentType(outputFormat), "." + outputFormat, null);
        }
        Object url = first.get("url");
        if (url != null && !url.toString().isBlank()) {
            return downloadImage(url.toString());
        }
        throw new IllegalStateException("图片模型没有返回 b64_json 或 url");
    }

    private Mono<ImageResult> handleImageResponse(ClientResponse response, String apiName) {
        MediaType contentType = response.headers().contentType().orElse(MediaType.APPLICATION_OCTET_STREAM);
        return response.bodyToMono(byte[].class)
                .defaultIfEmpty(new byte[0])
                .flatMap(bytes -> {
                    if (response.statusCode().isError()) {
                        throw new IllegalStateException(apiName + "调用失败：" + response.statusCode() + " " + abbreviate(toUtf8(bytes)));
                    }
                    if (bytes.length == 0) {
                        throw new IllegalStateException(apiName + "返回空响应");
                    }
                    if ("image".equalsIgnoreCase(contentType.getType()) || looksLikeImage(bytes)) {
                        return Mono.just(buildDirectImageResult(bytes, contentType));
                    }
                    return extractImageResultAsync(parseImageResponse(toUtf8(bytes), apiName));
                });
    }

    private Mono<ImageResult> extractImageResultAsync(Map<?, ?> response) {
        if (response == null) {
            return Mono.error(new IllegalStateException("图片模型没有返回结果"));
        }
        Object dataValue = response.get("data");
        if (!(dataValue instanceof List<?> data) || data.isEmpty() || !(data.get(0) instanceof Map<?, ?> first)) {
            return Mono.error(new IllegalStateException("图片模型返回格式不正确"));
        }
        Object b64 = first.get("b64_json");
        if (b64 != null && !b64.toString().isBlank()) {
            String outputFormat = imageOutputFormat();
            return Mono.just(new ImageResult(Base64.getDecoder().decode(b64.toString()), imageContentType(outputFormat), "." + outputFormat, null));
        }
        Object url = first.get("url");
        if (url != null && !url.toString().isBlank()) {
            return Mono.fromCallable(() -> downloadImage(url.toString()));
        }
        return Mono.error(new IllegalStateException("图片模型没有返回 b64_json 或 url"));
    }

    private ImageResult downloadImage(String imageUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(300))
                    .header("Accept", "image/*,*/*")
                    .header("User-Agent", "python-requests/2.31.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = imageHttpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            MediaType contentType = response.headers()
                    .firstValue("content-type")
                    .map(MediaType::parseMediaType)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);
            byte[] bytes = response.body() == null ? new byte[0] : response.body();
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("图片下载失败：" + response.statusCode() + " " + abbreviate(toUtf8(bytes)));
            }
            if (bytes.length == 0) {
                throw new IllegalStateException("图片下载返回空响应");
            }
            if (!"image".equalsIgnoreCase(contentType.getType()) && !looksLikeImage(bytes)) {
                throw new IllegalStateException("图片下载返回非图片内容：" + abbreviate(toUtf8(bytes)));
            }
            return buildDirectImageResult(bytes, contentType);
        } catch (IOException ex) {
            throw new IllegalStateException("图片下载失败：" + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("图片下载被中断", ex);
        }
    }

    private Mono<ImageResult> downloadImageAsync(String imageUrl) {
        return webClient.get()
                .uri(imageUrl)
                .exchangeToMono(response -> {
                    MediaType contentType = response.headers().contentType().orElse(MediaType.APPLICATION_OCTET_STREAM);
                    return response.bodyToMono(byte[].class)
                            .defaultIfEmpty(new byte[0])
                            .map(bytes -> {
                                if (response.statusCode().isError()) {
                                    throw new IllegalStateException("图片下载失败：" + response.statusCode() + " " + abbreviate(toUtf8(bytes)));
                                }
                                if (bytes.length == 0) {
                                    throw new IllegalStateException("图片下载返回空响应");
                                }
                                if (!"image".equalsIgnoreCase(contentType.getType()) && !looksLikeImage(bytes)) {
                                    throw new IllegalStateException("图片下载返回非图片内容：" + abbreviate(toUtf8(bytes)));
                                }
                                return buildDirectImageResult(bytes, contentType);
                            });
                });
    }

    private ImageResult buildDirectImageResult(byte[] bytes, MediaType contentType) {
        String contentTypeValue = resolveImageContentType(bytes, contentType);
        return new ImageResult(bytes, contentTypeValue, resolveImageExtension(contentTypeValue), null);
    }

    private ImageResult handleHttpImageResponse(HttpResponse<byte[]> response, String apiName) {
        byte[] bytes = response.body() == null ? new byte[0] : response.body();
        MediaType contentType = response.headers()
                .firstValue("content-type")
                .map(MediaType::parseMediaType)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        if (response.statusCode() >= 400) {
            throw new IllegalStateException(apiName + "调用失败：" + response.statusCode() + " " + abbreviate(toUtf8(bytes)));
        }
        if (bytes.length == 0) {
            throw new IllegalStateException(apiName + "返回空响应");
        }
        if ("image".equalsIgnoreCase(contentType.getType()) || looksLikeImage(bytes)) {
            return buildDirectImageResult(bytes, contentType);
        }
        return extractImageResult(parseImageResponse(toUtf8(bytes), apiName));
    }

    private String buildImageGenerationRequestBody(String prompt, String requestedImageSize, String requestedQuality, String requestedFormat) {
        return "{"
                + "\"model\":\"" + escapeJson(properties.getImage().getModel()) + "\","
                + "\"prompt\":\"" + escapeJson(prompt) + "\","
                + "\"n\":1,"
                + "\"size\":\"" + escapeJson(imageSize(requestedImageSize)) + "\","
                + "\"quality\":\"" + escapeJson(imageQuality(requestedQuality)) + "\","
                + "\"output_format\":\"" + escapeJson(imageOutputFormat(requestedFormat)) + "\","
                + "\"output_compression\":" + imageOutputCompression() + ","
                + "\"background\":\"auto\","
                + "\"moderation\":\"auto\","
                + "\"stream\":" + imageStream() + ","
                + "\"partial_images\":" + imagePartialImages()
                + "}";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else if (ch > 0x7F) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.toString();
    }
    private String imageSize() {
        return imageSize(null);
    }

    private String imageSize(String requestedImageSize) {
        if (requestedImageSize != null && !requestedImageSize.isBlank()) {
            return requestedImageSize;
        }
        String value = properties.getImage().getImageSize();
        return value == null || value.isBlank() ? "1024x1536" : value;
    }

    private String imageQuality() {
        return imageQuality(null);
    }

    private String imageQuality(String requestedQuality) {
        if (requestedQuality != null && !requestedQuality.isBlank()) {
            return requestedQuality;
        }
        String value = properties.getImage().getImageQuality();
        return value == null || value.isBlank() ? "low" : value;
    }

    private String imageOutputFormat() {
        return imageOutputFormat(null);
    }

    private String imageOutputFormat(String requestedFormat) {
        if (requestedFormat != null && !requestedFormat.isBlank()) {
            return requestedFormat.toLowerCase();
        }
        String value = properties.getImage().getImageOutputFormat();
        return value == null || value.isBlank() ? "jpeg" : value.toLowerCase();
    }

    private int imageOutputCompression() {
        Integer value = properties.getImage().getImageOutputCompression();
        return value == null ? 85 : Math.max(0, Math.min(value, 100));
    }

    private boolean imageStream() {
        return Boolean.TRUE.equals(properties.getImage().getImageStream());
    }

    private int imagePartialImages() {
        Integer value = properties.getImage().getImagePartialImages();
        return value == null ? 0 : Math.max(0, value);
    }

    private String imageContentType(String outputFormat) {
        return switch (outputFormat) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            default -> "image/png";
        };
    }

    private Map<?, ?> parseImageResponse(String responseBody, String apiName) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException(apiName + "返回空响应");
        }
        if (responseBody.contains("data:")) {
            return parseSseImageResponse(responseBody, apiName);
        }
        return parseJsonResponse(responseBody, apiName);
    }

    private Map<?, ?> parseJsonResponse(String responseBody, String apiName) {
        try {
            return objectMapper.readValue(responseBody, Map.class);
        } catch (Exception ex) {
            throw new IllegalStateException(apiName + "返回非 JSON 内容：" + abbreviate(responseBody), ex);
        }
    }

    private Map<?, ?> parseSseImageResponse(String responseBody, String apiName) {
        Map<?, ?> latestImageEvent = null;
        for (String rawLine : responseBody.split("\\R")) {
            String line = rawLine.trim();
            if (!line.startsWith("data:")) {
                continue;
            }
            String data = line.substring("data:".length()).trim();
            if (data.isBlank() || "[DONE]".equals(data)) {
                continue;
            }
            Map<?, ?> event = parseJsonResponse(data, apiName + "流式事件");
            if (containsImageData(event)) {
                latestImageEvent = event;
            }
        }
        if (latestImageEvent == null) {
            throw new IllegalStateException(apiName + "流式响应没有返回图片数据：" + abbreviate(responseBody));
        }
        return latestImageEvent;
    }

    private boolean containsImageData(Map<?, ?> event) {
        Object dataValue = event.get("data");
        if (!(dataValue instanceof List<?> data) || data.isEmpty() || !(data.get(0) instanceof Map<?, ?> first)) {
            return false;
        }
        Object b64 = first.get("b64_json");
        Object url = first.get("url");
        return b64 != null && !b64.toString().isBlank() || url != null && !url.toString().isBlank();
    }

    private String resolveImageContentType(byte[] bytes, MediaType contentType) {
        if (contentType != null && "image".equalsIgnoreCase(contentType.getType())) {
            return contentType.toString();
        }
        if (bytes.length >= 8
                && bytes[0] == (byte) 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47) {
            return "image/png";
        }
        if (bytes.length >= 3
                && bytes[0] == (byte) 0xFF
                && bytes[1] == (byte) 0xD8
                && bytes[2] == (byte) 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 12
                && bytes[0] == 0x52
                && bytes[1] == 0x49
                && bytes[2] == 0x46
                && bytes[3] == 0x46
                && bytes[8] == 0x57
                && bytes[9] == 0x45
                && bytes[10] == 0x42
                && bytes[11] == 0x50) {
            return "image/webp";
        }
        return imageContentType(imageOutputFormat());
    }

    private String resolveImageExtension(String contentTypeValue) {
        if (contentTypeValue == null || contentTypeValue.isBlank()) {
            return "." + imageOutputFormat();
        }
        String normalized = contentTypeValue.toLowerCase();
        if (normalized.contains("jpeg") || normalized.contains("jpg")) {
            return ".jpg";
        }
        if (normalized.contains("webp")) {
            return ".webp";
        }
        if (normalized.contains("png")) {
            return ".png";
        }
        return "." + imageOutputFormat();
    }

    private boolean looksLikeImage(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return false;
        }
        boolean png = bytes.length >= 8
                && bytes[0] == (byte) 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47;
        boolean jpeg = bytes.length >= 3
                && bytes[0] == (byte) 0xFF
                && bytes[1] == (byte) 0xD8
                && bytes[2] == (byte) 0xFF;
        boolean webp = bytes.length >= 12
                && bytes[0] == 0x52
                && bytes[1] == 0x49
                && bytes[2] == 0x46
                && bytes[3] == 0x46
                && bytes[8] == 0x57
                && bytes[9] == 0x45
                && bytes[10] == 0x42
                && bytes[11] == 0x50;
        return png || jpeg || webp;
    }

    private String toUtf8(byte[] bytes) {
        return bytes == null || bytes.length == 0 ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    private String abbreviate(String value) {
        String text = value == null ? "" : value.replace("\r", " ").replace("\n", " ").trim();
        return text.length() <= 600 ? text : text.substring(0, 600) + "...";
    }

    private String extractContent(Map<?, ?> response) {
        if (response == null) {
            return "";
        }
        Object choicesValue = response.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()) {
            return "";
        }
        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            return "";
        }
        Object messageValue = choiceMap.get("message");
        if (messageValue instanceof Map<?, ?> messageMap) {
            Object content = messageMap.get("content");
            return content == null ? "" : content.toString();
        }
        Object text = choiceMap.get("text");
        return text == null ? "" : text.toString();
    }
}

