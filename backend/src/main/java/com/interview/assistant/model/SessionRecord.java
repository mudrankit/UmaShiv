package com.interview.assistant.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;

@Entity
@Table(name = "sessions", indexes = {
        @Index(name = "idx_sessions_user_id", columnList = "user_id"),
        @Index(name = "idx_sessions_expires_at", columnList = "expires_at")
})
public class SessionRecord {

    @Id
    private String token;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    @Column(name = "expires_at", nullable = false)
    private String expiresAt;

    @Column(name = "last_seen_at", nullable = false)
    private String lastSeenAt;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
    public String getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(String lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}