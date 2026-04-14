package com.interview.assistant.dto;

import javax.validation.constraints.NotBlank;

public class DesignRequest {

    @NotBlank
    private String prompt;

    private boolean interviewerMode;
    private String provider;
    private String model;

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public boolean isInterviewerMode() {
        return interviewerMode;
    }

    public void setInterviewerMode(boolean interviewerMode) {
        this.interviewerMode = interviewerMode;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}