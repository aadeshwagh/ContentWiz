package com.aadeshwagh.ContentWiz.upload.instagram;

import com.aadeshwagh.ContentWiz.util.ContentProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Component
public class InstagramReelUploader {

    private static final int POLL_INTERVAL_MS = 10_000;

    @Value("${instagram.graph-base:https://graph.instagram.com/v25.0}")
    private String graphBase;

    @Value("${instagram.max-poll-attempts:30}")
    private int maxPollAttempts;

    private final ContentProperties contentProperties;
    private final HttpClient httpClient;

    public InstagramReelUploader(ContentProperties contentProperties) {
        this.contentProperties = contentProperties;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * @return published media ID
     * @throws IllegalArgumentException if channel config is missing
     * @throws RuntimeException         if any API step fails
     */
    public String publishReel(String channelType, String videoUrl, String caption)
            throws IOException, InterruptedException {
        ContentProperties.InstagramConfig config = resolveConfig(channelType);
        String containerId = createContainer(config, videoUrl, caption);
        pollUntilFinished(containerId, config.getAccessToken());
        return publish(containerId, config);
    }

    // ── Config resolution ─────────────────────────────────────────────────

    private ContentProperties.InstagramConfig resolveConfig(String channelType) {
        ContentProperties.TypeConfig typeConfig = contentProperties.getTypes().get(channelType);
        if (typeConfig == null) {
            throw new IllegalArgumentException("No config found for channel type: " + channelType);
        }

        ContentProperties.InstagramConfig instagram = typeConfig.getInstagram();
        if (instagram.getAccessToken() == null || instagram.getBusinessAccountId() == null) {
            throw new IllegalArgumentException("access-token or business-account-id missing for: " + channelType);
        }

        return instagram;
    }

    // ── Step 1: Create container ──────────────────────────────────────────

    private String createContainer(ContentProperties.InstagramConfig config,
                                   String videoUrl, String caption)
            throws IOException, InterruptedException {

        String body = "media_type=REELS"
                + "&video_url=" + URLEncoder.encode(videoUrl, StandardCharsets.UTF_8)
                + "&caption="   + URLEncoder.encode(caption,  StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(graphBase + "/" + config.getBusinessAccountId() + "/media"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Bearer " + config.getAccessToken())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new RuntimeException("Container creation failed. HTTP "
                    + res.statusCode() + ": " + res.body());
        }

        return extractField(res.body(), "id");
    }

    // ── Step 2: Poll until FINISHED ───────────────────────────────────────

    private void pollUntilFinished(String containerId, String accessToken)
            throws IOException, InterruptedException {

        String url = graphBase + "/" + containerId + "?fields=status_code";

        for (int attempt = 1; attempt <= maxPollAttempts; attempt++) {
            Thread.sleep(POLL_INTERVAL_MS);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            String status = extractField(res.body(), "status_code");

            switch (status) {
                case "FINISHED" -> { return; }
                case "ERROR"    -> throw new RuntimeException("Container processing error: " + res.body());
                case "EXPIRED"  -> throw new RuntimeException("Container expired before publish.");
            }
        }

        throw new RuntimeException("Timed out waiting for container after " + maxPollAttempts + " attempts.");
    }

    // ── Step 3: Publish ───────────────────────────────────────────────────

    private String publish(String containerId, ContentProperties.InstagramConfig config)
            throws IOException, InterruptedException {

        String body = "creation_id=" + containerId;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(graphBase + "/" + config.getBusinessAccountId() + "/media_publish"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Bearer " + config.getAccessToken())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new RuntimeException("Publish failed. HTTP " + res.statusCode() + ": " + res.body());
        }

        return extractField(res.body(), "id");
    }

    // ── JSON extractor ────────────────────────────────────────────────────

    private String extractField(String json, String field) {
        String keyStr = "\"" + field + "\":\"";
        int start = json.indexOf(keyStr);
        if (start != -1) {
            start += keyStr.length();
            return json.substring(start, json.indexOf('"', start));
        }
        String keyNum = "\"" + field + "\":";
        start = json.indexOf(keyNum);
        if (start != -1) {
            start += keyNum.length();
            int end = Math.min(indexOrMax(json, ',', start), indexOrMax(json, '}', start));
            return json.substring(start, end).trim();
        }
        throw new RuntimeException("Field '" + field + "' not found in JSON: " + json);
    }

    private int indexOrMax(String s, char c, int from) {
        int i = s.indexOf(c, from);
        return i == -1 ? Integer.MAX_VALUE : i;
    }
}