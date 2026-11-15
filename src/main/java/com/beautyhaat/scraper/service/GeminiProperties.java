package com.beautyhaat.scraper.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings from application.properties (prefix "gemini"). The API key is read from the GEMINI_API_KEY variable. */
@ConfigurationProperties(prefix = "gemini")
public class GeminiProperties {

    private String apiKey = "";
    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
    /** "gemini-flash-latest" always points at Google's current Flash model. Change it if you prefer a fixed one. */
    private String model = "gemini-flash-latest";
    /** Used for a product when the main model keeps answering "overloaded" (HTTP 503). Leave empty to disable. */
    private String fallbackModel = "gemini-flash-lite-latest";
    /** First wait after a 503; it doubles on every retry (5 s, 10 s, 20 s, 40 s). */
    private long serverRetryBaseMs = 5000;
    /** Pause between requests in a batch. 6500 ms stays under 10 requests/minute. */
    private long delayMs = 6500;
    private int maxOutputTokens = 4096;
    private int timeoutMs = 60000;

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getFallbackModel() { return fallbackModel; }
    public void setFallbackModel(String fallbackModel) { this.fallbackModel = fallbackModel; }
    public long getServerRetryBaseMs() { return serverRetryBaseMs; }
    public void setServerRetryBaseMs(long serverRetryBaseMs) { this.serverRetryBaseMs = serverRetryBaseMs; }
    public long getDelayMs() { return delayMs; }
    public void setDelayMs(long delayMs) { this.delayMs = delayMs; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
}
