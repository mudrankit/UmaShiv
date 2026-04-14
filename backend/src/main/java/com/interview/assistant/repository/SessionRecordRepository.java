package com.interview.assistant.repository;

import com.interview.assistant.model.SessionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRecordRepository extends JpaRepository<SessionRecord, String> {

    void deleteByUserId(String userId);
}