package com.interview.assistant.dto.auth;

import javax.validation.constraints.NotBlank;

public class GoogleLoginRequest {

    @NotBlank
    private String idToken;

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }
}