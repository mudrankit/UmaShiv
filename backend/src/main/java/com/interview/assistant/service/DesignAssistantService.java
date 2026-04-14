package com.interview.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.assistant.config.AppProperties;
import com.interview.assistant.dto.DesignRequest;
import com.interview.assistant.dto.DesignResponse;
import com.interview.assistant.dto.RatingRequest;
import com.interview.assistant.dto.RatingResponse;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Service
public class DesignAssistantService {

    private static final Logger logger = LoggerFactory.getLogger(DesignAssistantService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public DesignAssistantService(RestTemplate restTemplate, ObjectMapper objectMapper, AppProperties appProperties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    public DesignResponse generateDesign(DesignRequest request) {
        ProviderSelection selection = resolveSelection(request);

        if ("demo".equals(selection.getProvider())) {
            return buildDemoResponse(request, selection.getProvider(), selection.getModel());
        }

        try {
            if ("openai".equals(selection.getProvider())) {
                return generateWithChatCompletions(request, selection, appProperties.getOpenai());
            }
            if ("groq".equals(selection.getProvider())) {
                return generateWithChatCompletions(request, selection, appProperties.getGroq());
            }
            if ("gemini".equals(selection.getProvider())) {
                return generateWithGemini(request, selection, appProperties.getGemini());
            }
        } catch (RestClientResponseException exception) {
            logger.error("{} API returned an error response. status={}, body={}",
                    selection.getProvider(), exception.getRawStatusCode(), exception.getResponseBodyAsString(), exception);
            throw new IllegalStateException(buildRemoteErrorMessage(selection.getProvider(), exception));
        } catch (Exception exception) {
            logger.error("{} design generation failed for prompt: {}", selection.getProvider(), request.getPrompt(), exception);
            throw new IllegalStateException(buildGenericProviderError(selection.getProvider(), exception), exception);
        }

        throw new IllegalStateException("Unsupported provider: " + selection.getProvider());
    }

    public RatingResponse rateDesign(RatingRequest request) {
        RatingResponse response = new RatingResponse();
        response.setScore(Math.max(72, Math.min(98, request.getRating() * 18 + 8)));
        response.setVerdict(request.getRating() >= 4 ? "Interview ready with minor refinements" : "Promising, but needs more depth");
        response.setStrengths(Arrays.asList(
                "Problem framing is clear and interview-friendly",
                "Core scaling components are identified early",
                "The response balances APIs, data, and operational concerns"
        ));
        response.setImprovements(Arrays.asList(
                "Quantify traffic assumptions before choosing storage and partitioning strategy",
                "Call out failure handling and multi-region behavior more explicitly",
                "Tie each trade-off back to latency, consistency, and cost"
        ));
        return response;
    }

    private DesignResponse generateWithChatCompletions(DesignRequest request,
                                                       ProviderSelection selection,
                                                       AppProperties.ProviderSettings settings) throws Exception {
        ensureConfigured(selection.getProvider(), settings);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(settings.getApiKey());

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("model", selection.getModel());
        payload.put("temperature", 0.35);

        Map<String, Object> responseFormat = new LinkedHashMap<String, Object>();
        responseFormat.put("type", "json_object");
        payload.put("response_format", responseFormat);

        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
        messages.add(message("system", buildSystemPrompt()));
        messages.add(message("user", buildUserPrompt(request)));
        payload.put("messages", messages);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<Map<String, Object>>(payload, headers);
        String body = restTemplate.postForObject(settings.getBaseUrl(), entity, String.class);
        JsonNode root = objectMapper.readTree(body);
        String content = root.path("choices").path(0).path("message").path("content").asText();
        return parseAiResponse(content, request, selection);
    }

    private DesignResponse generateWithGemini(DesignRequest request,
                                              ProviderSelection selection,
                                              AppProperties.ProviderSettings settings) throws Exception {
        ensureConfigured(selection.getProvider(), settings);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        Map<String, Object> generationConfig = new LinkedHashMap<String, Object>();
        generationConfig.put("temperature", 0.35);
        generationConfig.put("responseMimeType", "application/json");
        payload.put("generationConfig", generationConfig);

        List<Map<String, Object>> contents = new ArrayList<Map<String, Object>>();
        contents.add(contentPart(buildSystemPrompt() + "\n\n" + buildUserPrompt(request)));
        payload.put("contents", contents);

        String url = buildGeminiUrl(settings.getBaseUrl(), selection.getModel(), settings.getApiKey());
        HttpEntity<Map<String, Object>> entity = new HttpEntity<Map<String, Object>>(payload, headers);
        String body = restTemplate.postForObject(url, entity, String.class);
        JsonNode root = objectMapper.readTree(body);
        String content = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
        return parseAiResponse(content, request, selection);
    }

    private Map<String, Object> contentPart(String text) {
        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("text", text);

        Map<String, Object> content = new LinkedHashMap<String, Object>();
        content.put("parts", Arrays.<Object>asList(part));
        return content;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<String, String>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private ProviderSelection resolveSelection(DesignRequest request) {
        String provider = normalize(request.getProvider());
        if (!StringUtils.hasText(provider)) {
            provider = normalize(appProperties.getDefaultProvider());
        }
        if (!StringUtils.hasText(provider)) {
            provider = "demo";
        }

        String model = request.getModel();
        if (!StringUtils.hasText(model)) {
            if ("openai".equals(provider)) {
                model = appProperties.getOpenai().getModel();
            } else if ("groq".equals(provider)) {
                model = appProperties.getGroq().getModel();
            } else if ("gemini".equals(provider)) {
                model = appProperties.getGemini().getModel();
            } else {
                model = "demo-template";
            }
        }

        return new ProviderSelection(provider, model);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }

    private void ensureConfigured(String provider, AppProperties.ProviderSettings settings) {
        if (!StringUtils.hasText(settings.getApiKey())) {
            throw new IllegalStateException("No API key configured for provider '" + provider
                    + "'. Set the matching environment variable before using this model.");
        }
        if (!StringUtils.hasText(settings.getBaseUrl())) {
            throw new IllegalStateException("No base URL configured for provider '" + provider + "'.");
        }
    }

    private String buildGeminiUrl(String baseUrl, String model, String apiKey) throws UnsupportedEncodingException {
        return baseUrl + "/" + model + ":generateContent?key=" + URLEncoder.encode(apiKey, "UTF-8");
    }

    private String buildSystemPrompt() {
        return "You are a senior distributed systems interviewer. Return only valid JSON with keys: "
                + "title, functionalRequirements, nonFunctionalRequirements, entities, apis, diagram, summary, tradeOffs, followUpQuestions, rawMarkdown. "
                + "Use arrays of concise strings for every list key. The diagram must be a full Mermaid flowchart or Graphviz-style diagram string. "
                + "Do not wrap the response or any field in markdown code fences, and do not use triple backticks anywhere. "
                + "The diagram field must be a plain JSON string value, not a fenced code block. "
                + "Every diagram edge must mention the action on that connection, such as request routing, cache lookup, event publishing, persistence, or notification fanout.";
    }

    private String buildUserPrompt(DesignRequest request) {
        return "Create a system design interview answer for: " + request.getPrompt()
                + ". Return these sections exactly: functional requirements, non-functional requirements, entities, APIs, full Mermaid or Graphviz-style HLD diagram, summary, trade-offs, and follow-up questions."
                + (request.isInterviewerMode() ? " Make the trade-offs and follow-up questions feel interviewer-grade." : "");
    }

    private DesignResponse parseAiResponse(String content,
                                           DesignRequest request,
                                           ProviderSelection selection) throws Exception {
        String sanitizedContent = sanitizeJsonContent(content);
        JsonNode node = objectMapper.readTree(sanitizedContent);
        DesignResponse response = new DesignResponse();
        response.setTitle(node.path("title").asText(extractTitle(request.getPrompt())));
        response.setFunctionalRequirements(readList(node, "functionalRequirements"));
        response.setNonFunctionalRequirements(readList(node, "nonFunctionalRequirements"));
        response.setEntities(readList(node, "entities"));
        response.setApis(readList(node, "apis"));
        response.setDiagram(node.path("diagram").asText(buildDefaultDiagram(request.getPrompt())));
        response.setSummary(node.path("summary").asText("Interview-ready system design overview."));
        response.setTradeOffs(readList(node, "tradeOffs"));
        response.setFollowUpQuestions(readList(node, "followUpQuestions"));
        response.setRawMarkdown(node.path("rawMarkdown").asText(buildMarkdown(response)));
        response.setProvider(selection.getProvider());
        response.setModel(selection.getModel());
        return response;
    }

    private String sanitizeJsonContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("The provider returned an empty response.");
        }

        String trimmed = content.trim();
        trimmed = trimmed.replace("\r\n", "\n");
        trimmed = trimmed.replaceAll("^```(?:json|mermaid|graphviz)?\\s*", "");
        trimmed = trimmed.replaceAll("\\s*```$", "");
        trimmed = trimmed.replace("```json", "");
        trimmed = trimmed.replace("```mermaid", "");
        trimmed = trimmed.replace("```graphviz", "");
        trimmed = trimmed.replace("```", "");
        trimmed = trimmed.trim();

        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            trimmed = trimmed.substring(firstBrace, lastBrace + 1);
        }

        if (trimmed.contains("\"diagram\": ```")) {
            throw new IllegalStateException("The provider returned JSON-like content with an unfenced diagram block. Please retry or switch models.");
        }

        return trimmed;
    }

    private String buildGenericProviderError(String provider, Exception exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return provider + " request failed before a usable response was returned.";
        }
        return provider + " request failed: " + message;
    }

    private String buildRemoteErrorMessage(String provider, RestClientResponseException exception) {
        String responseBody = exception.getResponseBodyAsString();
        if (StringUtils.hasText(responseBody)) {
            return provider + " request failed with status " + exception.getRawStatusCode() + ": " + responseBody;
        }
        return provider + " request failed with status " + exception.getRawStatusCode() + ".";
    }

    private List<String> readList(JsonNode node, String field) {
        List<String> values = new ArrayList<String>();
        for (JsonNode item : node.path(field)) {
            values.add(item.asText());
        }
        return values;
    }

    private DesignResponse buildDemoResponse(DesignRequest request, String provider, String model) {
        DesignResponse response = new DesignResponse();
        String title = extractTitle(request.getPrompt());
        response.setTitle(title);
        response.setFunctionalRequirements(Arrays.asList(
                "Allow clients to submit the primary action for the product with authentication and validation.",
                "Support retrieval of the latest state with pagination, filtering, and idempotent retries.",
                "Trigger downstream workflows such as notifications, analytics, and asynchronous processing."
        ));
        response.setNonFunctionalRequirements(Arrays.asList(
                "Keep p95 read latency low for hot user flows and cacheable traffic.",
                "Scale horizontally across application and worker tiers without downtime.",
                "Provide observability, retries, and graceful degradation during traffic spikes or dependency failure."
        ));
        response.setEntities(Arrays.asList(
                "User: identity, profile, auth context, and preferences.",
                "Primary domain object: the core trip, message, order, or content unit being created and queried.",
                "Event: append-only audit or workflow event emitted to asynchronous consumers.",
                "Notification job: queued task representing fanout, delivery, or retry state."
        ));
        response.setApis(Arrays.asList(
                "POST /v1/resources to create the primary domain object with idempotency key support.",
                "GET /v1/resources/{id} to fetch the latest state for a single entity.",
                "GET /v1/resources?cursor=... to page through recent objects for a user or partition.",
                "POST /v1/resources/{id}/actions to trigger secondary workflow transitions."
        ));
        response.setDiagram(buildDefaultDiagram(request.getPrompt()));
        response.setSummary("A scalable interview-ready design with clear traffic assumptions, bounded services, and an event-driven backbone for non-critical work.");
        response.setTradeOffs(Arrays.asList(
                "A write-through cache improves reads but adds invalidation complexity after state changes.",
                "Async queues protect user latency but introduce eventual consistency for secondary workflows.",
                "Service decomposition helps scaling and ownership, but increases debugging and operational overhead."
        ));
        response.setFollowUpQuestions(Arrays.asList(
                "How would you shard the primary data store once a single partition becomes hot?",
                "Which user-visible guarantees require strong consistency and which can tolerate eventual consistency?",
                "What would change if the product had to support multi-region active-active traffic?"
        ));
        response.setProvider(provider);
        response.setModel(model);
        response.setRawMarkdown(buildMarkdown(response));
        return response;
    }

    private String buildDefaultDiagram(String prompt) {
        return "flowchart LR\n"
                + "    Client[Client App] -->|send authenticated request| Gateway[API Gateway]\n"
                + "    Gateway -->|route domain call| Service[Core Service]\n"
                + "    Service -->|check hot data| Cache[Redis Cache]\n"
                + "    Service -->|persist source of truth| Database[Primary Database]\n"
                + "    Service -->|publish async workflow event| Queue[Message Queue]\n"
                + "    Queue -->|process background tasks| Worker[Async Worker]\n"
                + "    Worker -->|deliver side effects and notifications| Downstream[Notification or Analytics Service]\n"
                + "    Database -->|serve later reads and recovery| Service\n"
                + "    %% Diagram generated for " + extractTitle(prompt);
    }

    private String extractTitle(String prompt) {
        String normalized = prompt == null ? "System Design" : prompt.trim();
        if (normalized.isEmpty()) {
            return "System Design";
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String buildMarkdown(DesignResponse response) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(response.getTitle()).append("\n\n");
        appendSection(markdown, "Functional requirements", response.getFunctionalRequirements());
        appendSection(markdown, "Non-functional requirements", response.getNonFunctionalRequirements());
        appendSection(markdown, "Entities", response.getEntities());
        appendSection(markdown, "APIs", response.getApis());
        markdown.append("## Diagram\n");
        markdown.append("```mermaid\n").append(response.getDiagram()).append("\n```\n\n");
        markdown.append("## Summary\n").append(response.getSummary()).append("\n\n");
        appendSection(markdown, "Trade-offs", response.getTradeOffs());
        appendSection(markdown, "Follow-up questions", response.getFollowUpQuestions());
        return markdown.toString();
    }

    private void appendSection(StringBuilder builder, String heading, List<String> items) {
        builder.append("## ").append(heading).append("\n");
        for (String item : items) {
            builder.append("- ").append(item).append("\n");
        }
        builder.append("\n");
    }

    private static class ProviderSelection {

        private final String provider;
        private final String model;

        private ProviderSelection(String provider, String model) {
            this.provider = provider;
            this.model = model;
        }

        private String getProvider() {
            return provider;
        }

        private String getModel() {
            return model;
        }
    }
}