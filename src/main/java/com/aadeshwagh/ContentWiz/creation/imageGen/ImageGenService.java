package com.aadeshwagh.ContentWiz.creation.imageGen;

import com.aadeshwagh.ContentWiz.creation.entity.Scene;
import com.aadeshwagh.ContentWiz.creation.entity.Script;
import com.google.genai.Client;
import com.google.genai.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class ImageGenService {

    private static final Logger log = LoggerFactory.getLogger(ImageGenService.class);

    @Value("${google.gemini.api.key}")
    private String apiKey;

    // Make sure this points to gemini-3.1-flash-image in your properties file
    @Value("${google.gemini.image.model}")
    private String imageModel;

    // Create a thread pool to handle multiple image requests at once
    // Adjust the pool size based on your API rate limits
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Generates and downloads one PNG per scene concurrently.
     */
    public void generateImages(Script script, String artStyle, String outputFolder) throws IOException {
        Path outputPath = Paths.get(outputFolder);
        Files.createDirectories(outputPath);

        Client client = Client.builder()
                .apiKey(apiKey)
                .build();

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseModalities(List.of("IMAGE"))
                .build();

        log.info("Starting concurrent image generation for {} scenes...", script.getScenes().size());

        // Process all scenes in parallel to save time
        List<CompletableFuture<Void>> futures = script.getScenes().stream()
                .map(scene -> CompletableFuture.runAsync(() -> {
                    String prompt = buildPrompt(scene, artStyle);
                    log.info("Scene {} - Requesting image...", scene.getSceneNumber());

                    try {
                        GenerateContentResponse response = client.models.generateContent(
                                imageModel,
                                prompt,
                                config
                        );

                        byte[] imageBytes = extractImageBytes(response, scene.getSceneNumber());
                        String filename = String.format("scene_%02d.png", scene.getSceneNumber());
                        Path imagePath = outputPath.resolve(filename);

                        Files.write(imagePath, imageBytes);
                        log.info("Scene {} - Saved → {}", scene.getSceneNumber(), imagePath);

                    } catch (Exception e) {
                        log.error("Scene {} - Failed to generate image", scene.getSceneNumber(), e);
                        throw new RuntimeException("Scene " + scene.getSceneNumber() + " failed", e);
                    }
                }, executor))
                .collect(Collectors.toList());

        // Wait for all async tasks to complete before finishing the method
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.info("Successfully generated all images for the script!");
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Combines the scene's imagePrompt and mood with the global art style into a
     * rich, directive prompt that keeps every image visually consistent.
     */
    private String buildPrompt(Scene scene, String artStyle) {
        return String.format(
                "Create a single image in a strictly 16:9 aspect ratio. " +
                        "Art style: %s. " +
                        "The mood of this scene is: %s. " +
                        "Scene description: %s. " +
                        "Important: stay strictly in the defined art style for visual consistency across all scenes. " +
                        "No text, watermarks, or UI elements in the image.",
                artStyle,
                scene.getMood(),
                scene.getImagePrompt()
        );
    }

    private byte[] extractImageBytes(GenerateContentResponse response, int sceneNumber) {
        Candidate candidate = response.candidates()
                .filter(c -> !c.isEmpty())
                .map(List::getFirst)
                .orElseThrow(() -> new RuntimeException("No candidates in response for scene " + sceneNumber));

        return candidate.content()
                .flatMap(Content::parts)
                .flatMap(parts -> parts.stream()
                        .filter(part -> part.inlineData().flatMap(Blob::data).isPresent())
                        .findFirst())
                .flatMap(Part::inlineData)
                .flatMap(Blob::data)
                .orElseThrow(() -> new RuntimeException("No image data in response for scene " + sceneNumber));
    }
}