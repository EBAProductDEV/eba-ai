package com.qctv1.ai.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai.chat")
public class ChatMemoryProperties {

    private int redisTtlDays = 7;
    private int recentMessageLimit = 12;
    private int promptRoundLimit = 6;
    private int summaryTriggerInitialCount = 6;
    private int summaryTriggerStep = 4;

    public int getRedisTtlDays() {
        return redisTtlDays;
    }

    public void setRedisTtlDays(int redisTtlDays) {
        this.redisTtlDays = redisTtlDays;
    }

    public int getRecentMessageLimit() {
        return recentMessageLimit;
    }

    public void setRecentMessageLimit(int recentMessageLimit) {
        this.recentMessageLimit = recentMessageLimit;
    }

    public int getPromptRoundLimit() {
        return promptRoundLimit;
    }

    public void setPromptRoundLimit(int promptRoundLimit) {
        this.promptRoundLimit = promptRoundLimit;
    }

    public int getSummaryTriggerInitialCount() {
        return summaryTriggerInitialCount;
    }

    public void setSummaryTriggerInitialCount(int summaryTriggerInitialCount) {
        this.summaryTriggerInitialCount = summaryTriggerInitialCount;
    }

    public int getSummaryTriggerStep() {
        return summaryTriggerStep;
    }

    public void setSummaryTriggerStep(int summaryTriggerStep) {
        this.summaryTriggerStep = summaryTriggerStep;
    }
}
