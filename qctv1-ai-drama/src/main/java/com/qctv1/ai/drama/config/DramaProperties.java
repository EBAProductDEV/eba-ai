package com.qctv1.ai.drama.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai.drama")
public class DramaProperties {

    /** 素材根目录，固定为 D:/AI视频。 */
    private String assetRoot = "D:/AI视频";

    /** 文本生成模型配置，用于分集大纲、脚本、镜头提示词等。 */
    private ModelConfig text = new ModelConfig();

    /** 图片生成模型配置，用于角色图、场景图、镜头参考图。 */
    private ModelConfig image = new ModelConfig();

    /** 视频生成模型配置，用于镜头级视频片段。 */
    private ModelConfig video = new ModelConfig();

    /** Embedding 模型配置，用于 RAG 文本分块向量化。 */
    private ModelConfig embedding = new ModelConfig();

    /** Redis Stack 向量库配置。 */
    private VectorStore vectorStore = new VectorStore();

    public String getAssetRoot() {
        return assetRoot;
    }

    public void setAssetRoot(String assetRoot) {
        this.assetRoot = assetRoot;
    }

    public ModelConfig getText() {
        return text;
    }

    public void setText(ModelConfig text) {
        this.text = text;
    }

    public ModelConfig getImage() {
        return image;
    }

    public void setImage(ModelConfig image) {
        this.image = image;
    }

    public ModelConfig getVideo() {
        return video;
    }

    public void setVideo(ModelConfig video) {
        this.video = video;
    }

    public ModelConfig getEmbedding() {
        return embedding;
    }

    public void setEmbedding(ModelConfig embedding) {
        this.embedding = embedding;
    }

    public VectorStore getVectorStore() {
        return vectorStore;
    }

    public void setVectorStore(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public static class ModelConfig {
        private String provider = "openai-compatible";
        private String baseUrl = "";
        private String completionsPath = "/chat/completions";
        private String imagesPath = "/images/generations";
        private String imageEditsPath = "/images/edits";
        private String videosPath = "/videos";
        private String apiKey = "";
        private String model = "";
        private Integer maxTokens = 12000;
        private String imageSize = "1024x1536";
        private String imageQuality = "low";
        private String imageOutputFormat = "jpeg";
        private Integer imageOutputCompression = 85;
        private Boolean imageStream = false;
        private Integer imagePartialImages = 0;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getCompletionsPath() {
            return completionsPath;
        }

        public void setCompletionsPath(String completionsPath) {
            this.completionsPath = completionsPath;
        }

        public String getImagesPath() {
            return imagesPath;
        }

        public void setImagesPath(String imagesPath) {
            this.imagesPath = imagesPath;
        }

        public String getImageEditsPath() {
            return imageEditsPath;
        }

        public void setImageEditsPath(String imageEditsPath) {
            this.imageEditsPath = imageEditsPath;
        }

        public String getVideosPath() {
            return videosPath;
        }

        public void setVideosPath(String videosPath) {
            this.videosPath = videosPath;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }

        public boolean isReady() {
            return baseUrl != null && !baseUrl.isBlank()
                    && apiKey != null && !apiKey.isBlank()
                    && model != null && !model.isBlank();
        }

        public String getImageSize() {
            return imageSize;
        }

        public void setImageSize(String imageSize) {
            this.imageSize = imageSize;
        }

        public String getImageQuality() {
            return imageQuality;
        }

        public void setImageQuality(String imageQuality) {
            this.imageQuality = imageQuality;
        }

        public String getImageOutputFormat() {
            return imageOutputFormat;
        }

        public void setImageOutputFormat(String imageOutputFormat) {
            this.imageOutputFormat = imageOutputFormat;
        }

        public Integer getImageOutputCompression() {
            return imageOutputCompression;
        }

        public void setImageOutputCompression(Integer imageOutputCompression) {
            this.imageOutputCompression = imageOutputCompression;
        }

        public Boolean getImageStream() {
            return imageStream;
        }

        public void setImageStream(Boolean imageStream) {
            this.imageStream = imageStream;
        }

        public Integer getImagePartialImages() {
            return imagePartialImages;
        }

        public void setImagePartialImages(Integer imagePartialImages) {
            this.imagePartialImages = imagePartialImages;
        }
    }

    public static class VectorStore {
        private String type = "redis-stack";
        private String indexName = "qctv1_ai_drama_chunk_idx";
        private String keyPrefix = "qctv1:ai:drama:chunk:";
        private int embeddingDimension = 1536;
        private String distanceMetric = "COSINE";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getIndexName() {
            return indexName;
        }

        public void setIndexName(String indexName) {
            this.indexName = indexName;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        public int getEmbeddingDimension() {
            return embeddingDimension;
        }

        public void setEmbeddingDimension(int embeddingDimension) {
            this.embeddingDimension = embeddingDimension;
        }

        public String getDistanceMetric() {
            return distanceMetric;
        }

        public void setDistanceMetric(String distanceMetric) {
            this.distanceMetric = distanceMetric;
        }
    }
}
