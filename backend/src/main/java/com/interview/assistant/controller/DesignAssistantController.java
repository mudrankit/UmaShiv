package com.interview.assistant.controller;

import com.interview.assistant.dto.DesignRequest;
import com.interview.assistant.dto.DesignResponse;
import com.interview.assistant.dto.RatingRequest;
import com.interview.assistant.dto.RatingResponse;
import com.interview.assistant.service.AuthService;
import com.interview.assistant.service.DesignAssistantService;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/design")
@CrossOrigin(origins = "*")
public class DesignAssistantController {

    private final DesignAssistantService designAssistantService;
    private final AuthService authService;

    public DesignAssistantController(DesignAssistantService designAssistantService, AuthService authService) {
        this.designAssistantService = designAssistantService;
        this.authService = authService;
    }

    @PostMapping("/generate")
    public ResponseEntity<DesignResponse> generate(@RequestHeader("Authorization") String authorizationHeader,
                                                   @Valid @RequestBody DesignRequest request) {
        authService.requireUser(authorizationHeader);
        return ResponseEntity.ok(designAssistantService.generateDesign(request));
    }

    @PostMapping("/rate")
    public ResponseEntity<RatingResponse> rate(@RequestHeader("Authorization") String authorizationHeader,
                                               @Valid @RequestBody RatingRequest request) {
        authService.requireUser(authorizationHeader);
        return ResponseEntity.ok(designAssistantService.rateDesign(request));
    }
}