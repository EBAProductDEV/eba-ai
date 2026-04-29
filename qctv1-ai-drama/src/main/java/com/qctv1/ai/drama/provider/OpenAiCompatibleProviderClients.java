package com.qctv1.ai.drama.provider;

import com.qctv1.ai.drama.config.DramaProperties;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OpenAiCompatibleProviderClients implements TextGenerationClient, ImageGenerationClient, VideoGenerationClient, EmbeddingClient {

    private final DramaProperties properties;

    public OpenAiCompatibleProviderClients(DramaProperties properties) {
        this.properties = properties;
    }

    @Override
    public String generate(String prompt) {
        // v1 先保留统一 Provider 入口，后续在这里接入真实 OpenAI-compatible chat/completions 接口。
        if (!properties.getText().isReady()) {
            return "模型配置未完成，已返回短剧文本生成占位结果。请配置 DRAMA_TEXT_BASE_URL / DRAMA_TEXT_API_KEY / DRAMA_TEXT_MODEL 后接入真实生成。";
        }
        return "待接入真实文本模型：" + properties.getText().getModel();
    }

    @Override
    public String submitImageTask(String prompt) {
        // 图片生成通常是异步任务，先返回内部任务号，真实 providerTaskId 后续由具体供应商客户端填充。
        return "mock-image-" + UUID.randomUUID();
    }

    @Override
    public String submitVideoTask(String prompt) {
        // 视频生成耗时更长，接口按异步提交和前端轮询设计。
        return "mock-video-" + UUID.randomUUID();
    }

    @Override
    public float[] embed(String text) {
        // Redis Stack 索引要求固定维度；真实 Embedding 接入后应返回同维度向量。
        return new float[properties.getVectorStore().getEmbeddingDimension()];
    }
}
