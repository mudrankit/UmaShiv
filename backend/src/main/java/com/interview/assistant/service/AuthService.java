package com.interview.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.assistant.config.AuthProperties;
import com.interview.assistant.dto.auth.AuthResponse;
import com.interview.assistant.dto.auth.AuthUserResponse;
import com.interview.assistant.dto.auth.GoogleLoginRequest;
import com.interview.assistant.dto.auth.LoginRequest;
import com.interview.assistant.dto.auth.RegisterRequest;
import com.interview.assistant.model.SessionRecord;
import com.interview.assistant.model.UserAccount;
import com.interview.assistant.repository.SessionRecordRepository;
import com.interview.assistant.repository.UserAccountRepository;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final RestTemplate restTemplate;
    private final AuthProperties authProperties;
    private final UserAccountRepository userAccountRepository;
    private final SessionRecordRepository sessionRecordRepository;

    public AuthService(ObjectMapper objectMapper,
                       PasswordEncoder passwordEncoder,
                       RestTemplate restTemplate,
                       AuthProperties authProperties,
                       UserAccountRepository userAccountRepository,
                       SessionRecordRepository sessionRecordRepository) {
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
        this.restTemplate = restTemplate;
        this.authProperties = authProperties;
        this.userAccountRepository = userAccountRepository;
        this.sessionRecordRepository = sessionRecordRepository;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String username = request.getUsername().trim();

        if (!username.matches("[A-Za-z0-9_]{3,30}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Username must be 3-30 characters and contain only letters, numbers, or underscore.");
        }

        ensureUnique(email, username);

        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID().toString());
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setProvider("local");
        user.setCreatedAt(Instant.now().toString());
        userAccountRepository.save(user);

        SessionRecord session = createSession(user);
        return buildAuthResponse(user, session.getToken());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String identifier = request.getIdentifier().trim();
        UserAccount user = findByIdentifier(identifier)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials."));

        if (!"local".equals(user.getProvider()) || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials.");
        }

        SessionRecord session = createSession(user);
        return buildAuthResponse(user, session.getToken());
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        if (!StringUtils.hasText(authProperties.getGoogleClientId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Google sign-in is not configured on the backend. Set APP_AUTH_GOOGLE_CLIENT_ID first.");
        }

        JsonNode tokenInfo = verifyGoogleToken(request.getIdToken());
        String email = tokenInfo.path("email").asText().toLowerCase(Locale.ROOT);
        String subject = tokenInfo.path("sub").asText();
        boolean emailVerified = tokenInfo.path("email_verified").asBoolean(false);
        String audience = tokenInfo.path("aud").asText();

        if (!emailVerified) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google account email is not verified.");
        }
        if (!authProperties.getGoogleClientId().equals(audience)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google sign-in token was issued for a different client ID.");
        }

        UserAccount user = userAccountRepository.findByProviderAndProviderSubject("google", subject)
                .orElseGet(() -> userAccountRepository.findByEmailIgnoreCase(email).orElse(null));

        if (user == null) {
            user = new UserAccount();
            user.setId(UUID.randomUUID().toString());
            user.setEmail(email);
            user.setUsername(generateUniqueUsername(tokenInfo.path("name").asText(), email));
            user.setProvider("google");
            user.setProviderSubject(subject);
            user.setCreatedAt(Instant.now().toString());
        } else {
            user.setProvider("google");
            user.setProviderSubject(subject);
        }

        userAccountRepository.save(user);
        SessionRecord session = createSession(user);
        return buildAuthResponse(user, session.getToken());
    }

    @Transactional
    public AuthUserResponse requireUser(String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        SessionRecord session = sessionRecordRepository.findById(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in to continue."));

        Instant expiresAt = Instant.parse(session.getExpiresAt());
        if (expiresAt.isBefore(Instant.now())) {
            sessionRecordRepository.delete(session);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Your session has expired. Please log in again.");
        }

        UserAccount user = userAccountRepository.findById(session.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account was not found."));

        session.setLastSeenAt(Instant.now().toString());
        sessionRecordRepository.save(session);
        return toUserResponse(user);
    }

    @Transactional
    public void logout(String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        sessionRecordRepository.deleteById(token);
    }

    private JsonNode verifyGoogleToken(String idToken) {
        String endpoint = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
        String body = restTemplate.getForObject(endpoint, String.class);
        try {
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to verify Google login token.");
        }
    }

    private void ensureUnique(String email, String username) {
        if (userAccountRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That email is already registered.");
        }
        if (userAccountRepository.findByUsernameIgnoreCase(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already taken.");
        }
    }

    private Optional<UserAccount> findByIdentifier(String identifier) {
        Optional<UserAccount> byEmail = userAccountRepository.findByEmailIgnoreCase(identifier);
        return byEmail.isPresent() ? byEmail : userAccountRepository.findByUsernameIgnoreCase(identifier);
    }

    private SessionRecord createSession(UserAccount user) {
        SessionRecord session = new SessionRecord();
        session.setToken(UUID.randomUUID().toString());
        session.setUserId(user.getId());
        session.setCreatedAt(Instant.now().toString());
        session.setLastSeenAt(Instant.now().toString());
        session.setExpiresAt(Instant.now().plus(authProperties.getSessionDays(), ChronoUnit.DAYS).toString());
        return sessionRecordRepository.save(session);
    }

    private String generateUniqueUsername(String name, String email) {
        String base = StringUtils.hasText(name) ? name : email.substring(0, email.indexOf('@'));
        base = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        if (base.length() < 3) {
            base = "user" + base;
        }
        if (base.length() > 24) {
            base = base.substring(0, 24);
        }

        String candidate = base;
        int suffix = 1;
        while (findByIdentifier(candidate).isPresent()) {
            candidate = base + suffix;
            suffix++;
        }
        return candidate;
    }

    private AuthResponse buildAuthResponse(UserAccount user, String token) {
        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setUser(toUserResponse(user));
        return response;
    }

    private AuthUserResponse toUserResponse(UserAccount user) {
        AuthUserResponse response = new AuthUserResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setUsername(user.getUsername());
        response.setProvider(user.getProvider());
        return response;
    }

    private String extractToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid authorization token.");
        }
        return authorizationHeader.substring(7).trim();
    }
}