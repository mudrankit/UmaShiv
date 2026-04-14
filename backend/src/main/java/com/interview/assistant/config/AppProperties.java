package com.interview.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public class AppProperties {

    private String defaultProvider = "demo";
    private final ProviderSettings openai = new ProviderSettings();
    private final ProviderSettings groq = new ProviderSettings();
    private final ProviderSettings gemini = new ProviderSettings();

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public ProviderSettings getOpenai() {
        return openai;
    }

    public ProviderSettings getGroq() {
        return groq;
    }

    public ProviderSettings getGemini() {
        return gemini;
    }

    public static class ProviderSettings {

        private String apiKey;
        private String model;
        private String baseUrl;

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

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}