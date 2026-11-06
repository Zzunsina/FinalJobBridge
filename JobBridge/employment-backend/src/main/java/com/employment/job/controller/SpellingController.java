package com.employment.job.controller;

import com.employment.global.common.Problem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tools")
public class SpellingController {
    private final ObjectMapper json;
    private final String apiKey;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(4)).build();

    public SpellingController(ObjectMapper json, @Value("${BAREUN_API_KEY:}") String apiKey) {
        this.json = json;
        this.apiKey = apiKey.strip();
    }

    @PostMapping("/spelling")
    Map<String, Object> check(@RequestBody Map<String, Object> body) {
        if (!(body.get("text") instanceof String source) || source.isBlank()) {
            throw new Problem(400, "검사할 내용을 입력해 주세요.");
        }
        if (source.length() > 5000) {
            throw new Problem(400, "한 번에 5,000자까지 검사할 수 있습니다.");
        }
        if (apiKey.isEmpty()) throw new Problem(503, "맞춤법 검사 서비스가 아직 설정되지 않았습니다.");

        try {
            var payload = Map.of("document", Map.of("content", source, "language", "ko-KR"),
                "encoding_type", "UTF32");
            var request = HttpRequest.newBuilder(URI.create("https://api.bareun.ai/bareun.RevisionService/CorrectError"))
                .timeout(Duration.ofSeconds(12))
                .header("api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
                .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                throw new Problem(503, "맞춤법 검사 사용 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.");
            }
            if (response.statusCode() != 200) throw unavailable();
            return Map.of("source", source, "issues", issues(source, json.readTree(response.body())));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException ex) {
            throw unavailable();
        }
    }

    static List<Issue> issues(String source, JsonNode response) {
        if (response == null || !response.isObject() || !source.equals(response.path("origin").asText())) {
            throw unavailable();
        }
        var candidates = new ArrayList<Issue>();
        JsonNode blocks = response.path("revisedBlocks");
        if (blocks.isMissingNode()) blocks = response.path("revised_blocks");
        int codePoints = source.codePointCount(0, source.length());
        for (JsonNode block : blocks) {
            var origin = block.path("origin");
            String before = origin.path("content").asText();
            var revised = block.path("revised");
            if (before.isEmpty() || !revised.isTextual() || before.equals(revised.asText())) continue;
            int offset = origin.path("beginOffset").asInt(origin.path("begin_offset").asInt(0));
            if (offset < 0 || offset >= codePoints) throw unavailable();
            int start = source.offsetByCodePoints(0, offset);
            if (!source.startsWith(before, start)) throw unavailable();
            var revision = block.path("revisions").path(0);
            String helpId = revision.path("helpId").asText(revision.path("help_id").asText());
            String category = revision.path("category").asText();
            String type = "SPACING".equals(category) || "3".equals(category) ? "띄어쓰기" : "맞춤법";
            String message = response.path("helps").path(helpId).path("comment").asText("바른AI 교정 제안");
            candidates.add(new Issue("bareun-" + start, start, start + before.length(),
                before, revised.asText(), type, message));
        }
        candidates.sort(Comparator.comparingInt(Issue::start)
            .thenComparing(Comparator.comparingInt(Issue::end).reversed()));
        var issues = new ArrayList<Issue>();
        int end = 0;
        for (Issue issue : candidates) {
            if (issue.start() < end) continue;
            issues.add(issue);
            end = issue.end();
        }
        return issues;
    }

    private static Problem unavailable() {
        return new Problem(502, "맞춤법 검사 서비스에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }

    record Issue(String id, int start, int end, String before, String after, String type, String message) {}
}
