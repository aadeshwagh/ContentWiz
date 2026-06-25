package com.aadeshwagh.ContentWiz.upload.youtube;

import com.aadeshwagh.ContentWiz.util.ContentProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class YouTubeVideoUploader {

    @Value("${youtube.upload-base:https://www.googleapis.com/upload/youtube/v3}")
    private String uploadBase;

    @Value("${youtube.token-url:https://oauth2.googleapis.com/token}")
    private String tokenUrl;

    private final ContentProperties contentProperties;
    private final HttpClient httpClient;

    public YouTubeVideoUploader(ContentProperties contentProperties) {
        this.contentProperties = contentProperties;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * @param channelType matches content.types.{channelType}.youtube.*
     * @param videoPath   local path to video file
     * @param title       video title (max 100 chars)
     * @param description video description with hashtags (max 5000 chars)
     * @param tags        list of tags (max 500 chars total across all tags)
     * @return YouTube video ID
     * @throws IllegalArgumentException if channel config is missing or incomplete
     * @throws RuntimeException         if any API step fails
     */
    public String uploadVideo(String channelType, Path videoPath, Path thumbnailPath,
                              String title, String description, List<String> tags)
            throws IOException, InterruptedException {

        ContentProperties.YouTubeConfig config = resolveConfig(channelType);
        String accessToken = refreshAccessToken(config);
        String uploadUrl   = initiateResumableUpload(config, accessToken, videoPath, title, description, tags);
        String videoId     = uploadBinary(uploadUrl, videoPath);

        if (thumbnailPath != null) {
            uploadThumbnail(videoId, thumbnailPath, accessToken);
        }

        return videoId;
    }

    // ── Step 4: Set custom thumbnail ──────────────────────────────────────────
// Requires channel to be verified (youtube.com/verify) for custom thumbnails
// Recommended: JPG/PNG, 1280x720, 16:9, under 2MB

    private void uploadThumbnail(String videoId, Path thumbnailPath, String accessToken)
            throws IOException, InterruptedException {

        String contentType = Files.probeContentType(thumbnailPath);
        if (contentType == null) contentType = "image/jpeg";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(uploadBase + "/thumbnails/set?videoId=" + videoId))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofFile(thumbnailPath))
                .build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new RuntimeException("Thumbnail upload failed. HTTP "
                    + res.statusCode() + ": " + res.body());
        }
    }

    // ── Config resolution ─────────────────────────────────────────────────

    private ContentProperties.YouTubeConfig resolveConfig(String channelType) {
        ContentProperties.TypeConfig typeConfig = contentProperties.getTypes().get(channelType);
        if (typeConfig == null) {
            throw new IllegalArgumentException("No config found for channel type: " + channelType);
        }

        ContentProperties.YouTubeConfig youtube = typeConfig.getYoutube();
        if (youtube.getClientId() == null
                || youtube.getClientSecret() == null
                || youtube.getRefreshToken() == null
                || youtube.getChannelId() == null) {        // ← add this
            throw new IllegalArgumentException(
                    "client-id, client-secret, refresh-token, or channel-id missing for channel: " + channelType);
        }

        return youtube;
    }

    // ── Step 1: Exchange refresh token for access token ───────────────────
    // YouTube access tokens expire after 1 hour — always refresh before uploading

    private String refreshAccessToken(ContentProperties.YouTubeConfig config)
            throws IOException, InterruptedException {

        String body = "grant_type=refresh_token"
                + "&client_id="     + config.getClientId()
                + "&client_secret=" + config.getClientSecret()
                + "&refresh_token=" + config.getRefreshToken();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new RuntimeException("Token refresh failed. HTTP "
                    + res.statusCode() + ": " + res.body());
        }

        return extractField(res.body(), "access_token");
    }

    // ── Step 2: Initiate resumable upload session ─────────────────────────
    // Sends metadata, gets back a one-time upload URL from the Location header

    private String initiateResumableUpload(ContentProperties.YouTubeConfig config,
                                           String accessToken, Path videoPath,
                                           String title, String description, List<String> tags)
            throws IOException, InterruptedException {

        long fileSize  = Files.size(videoPath);
        String tagsJson = tags.stream()
                .map(t -> "\"" + t + "\"")
                .collect(Collectors.joining(",", "[", "]"));

        String metadata = "{"
                + "\"snippet\":{"
                +   "\"channelId\":\""    + config.getChannelId()     + "\","   // ← add this
                +   "\"title\":\""        + escapeJson(title)          + "\","
                +   "\"description\":\""  + escapeJson(description)   + "\","
                +   "\"tags\":"           + tagsJson                  + ","
                +   "\"categoryId\":\""   + config.getCategoryId()    + "\""
                + "},"
                + "\"status\":{"
                +   "\"privacyStatus\":\"" + config.getPrivacyStatus() + "\","
                +   "\"madeForKids\":false,"
                +   "\"selfDeclaredMadeForKids\":false"
                + "}"
                + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(uploadBase + "/videos?uploadType=resumable&part=snippet,status"))
                .header("Authorization",          "Bearer " + accessToken)
                .header("Content-Type",           "application/json; charset=UTF-8")
                .header("X-Upload-Content-Type",  "video/mp4")
                .header("X-Upload-Content-Length", String.valueOf(fileSize))
                .POST(HttpRequest.BodyPublishers.ofString(metadata))
                .build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new RuntimeException("Upload initiation failed. HTTP "
                    + res.statusCode() + ": " + res.body());
        }

        return res.headers().firstValue("Location").orElseThrow(
                () -> new RuntimeException("No Location header in upload initiation response"));
    }

    // ── Step 3: Stream video binary to the upload URL ─────────────────────
    // Uses ofFile() to stream directly from disk — no full file load into memory

    private String uploadBinary(String uploadUrl, Path videoPath)
            throws IOException, InterruptedException {

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("Content-Type", "video/mp4")
                .PUT(HttpRequest.BodyPublishers.ofFile(videoPath))
                .build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        // YouTube returns 200 or 201 on success
        if (res.statusCode() != 200 && res.statusCode() != 201) {
            throw new RuntimeException("Binary upload failed. HTTP "
                    + res.statusCode() + ": " + res.body());
        }

        return extractField(res.body(), "id");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String extractField(String json, String field) {
        String key = "\"" + field + "\":";
        int start = json.indexOf(key);

        if (start == -1) {
            throw new RuntimeException("Field '" + field + "' not found in JSON: " + json);
        }

        start += key.length();

        // Skip whitespace after colon — YouTube responses use "key": "value" with spaces
        while (start < json.length() && json.charAt(start) == ' ') start++;

        if (json.charAt(start) == '"') {
            // String value — skip opening quote, read until closing quote
            start++;
            return json.substring(start, json.indexOf('"', start));
        } else {
            // Numeric / boolean value
            int end = Math.min(indexOrMax(json, ',', start), indexOrMax(json, '}', start));
            return json.substring(start, end).trim();
        }
    }

    private int indexOrMax(String s, char c, int from) {
        int i = s.indexOf(c, from);
        return i == -1 ? Integer.MAX_VALUE : i;
    }
}