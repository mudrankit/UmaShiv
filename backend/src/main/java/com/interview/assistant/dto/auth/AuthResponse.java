package com.interview.assistant.dto.auth;

public class AuthResponse {

    private String token;
    private AuthUserResponse user;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public AuthUserResponse getUser() {
        return user;
    }

    public void setUser(AuthUserResponse user) {
        this.user = user;
    }
}