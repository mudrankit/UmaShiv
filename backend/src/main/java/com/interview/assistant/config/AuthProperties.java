package com.interview.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private String googleClientId;
    private int sessionDays = 30;

    public String getGoogleClientId() {
        return googleClientId;
    }

    public void setGoogleClientId(String googleClientId) {
        this.googleClientId = googleClientId;
    }

    public int getSessionDays() {
        return sessionDays;
    }

    public void setSessionDays(int sessionDays) {
        this.sessionDays = sessionDays;
    }
}