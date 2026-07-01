package com.aadeshwagh.ContentWiz.creation.script;

import com.aadeshwagh.ContentWiz.creation.entity.MovieRecapScene;
import com.aadeshwagh.ContentWiz.creation.entity.MovieRecapScript;
import com.google.common.collect.ImmutableMap;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
@Slf4j
public class MovieRecapScriptWriter {

    private final String geminiModel;
    private final ObjectMapper objectMapper;
    private final Client geminiClient;
    private final String recapPromptPath;

    @Autowired
    public MovieRecapScriptWriter(
            @Value("${google.gemini.model}") String geminiModel,
            ObjectMapper objectMapper,
            @Value("${google.gemini.api.key}") String apiKey,
            @Value("${movie.recap.prompt.path}") String recapPromptPath) {
        this.geminiModel = geminiModel;
        this.objectMapper = objectMapper;
        this.geminiClient = Client.builder()
                .apiKey(apiKey)
                .build();
        this.recapPromptPath = recapPromptPath;
    }

    private String loadFileAsString(String path) {
        try {
            Resource resource = new FileSystemResource(path);
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load file: " + path, e);
        }
    }

    private Schema buildMovieRecapSchema() {

        Schema sceneSchema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(ImmutableMap.<String, Schema>builder()
                        .put("sceneNo",
                                Schema.builder()
                                        .type(Type.Known.INTEGER)
                                        .description("number of a scene starts from 1")
                                        .build())
                        .put("clipStartTime",
                                Schema.builder()
                                        .type(Type.Known.STRING)
                                        .description("Start timestamp of this scene in the original movie, format HH:MM:SS,mmm, taken directly from the SRT file. Must not be blank.")
                                        .build())
                        .put("clipEndTime",
                                Schema.builder()
                                        .type(Type.Known.STRING)
                                        .description("End timestamp of this scene in the original movie, format HH:MM:SS,mmm, taken directly from the SRT file. Must be after clipStartTime and not overlap the next scene.")
                                        .build())
                        .put("narration",
                                Schema.builder()
                                        .type(Type.Known.STRING)
                                        .description("Narration for this scene written for the Chatterbox TTS Full model: paraphrased (never verbatim subtitle dialogue), TTS-safe spoken English, describing what happens in the scene, who is involved, and why it matters.")
                                        .build())
                        .build())
                .required(List.of("clipStartTime", "clipEndTime", "narration"))
                .build();

        return Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(ImmutableMap.<String, Schema>builder()
                        .put("title",
                                Schema.builder()
                                        .type(Type.Known.STRING)
                                        .description("Catchy, curiosity-driven YouTube title for the movie recap video. Not a generic 'Movie Recap of X' title.")
                                        .build())
                        .put("description",
                                Schema.builder()
                                        .type(Type.Known.STRING)
                                        .description("YouTube description for the recap video, 50-120 words, hooks the viewer without spoiling the ending.")
                                        .build())
                        .put("scenes",
                                Schema.builder()
                                        .type(Type.Known.ARRAY)
                                        .items(sceneSchema)
                                        .description("Ordered, non-overlapping list of scenes covering the entire movie from start to finish, between 40 and 120 scenes.")
                                        .build())
                        .build())
                .required(List.of("title", "description", "scenes"))
                .build();
    }

    /**
     * Generates a full movie recap script (title, description, and timestamped scenes with
     * TTS-ready narration) from a movie synopsis and its SRT subtitle file, then writes the
     * resulting JSON to outputDir/{name}-recap-script.json.
     *
     * @param synopsis      plain text plot summary of the movie
     * @param srtFilePath   path to the movie's .srt subtitle file on disk
     * @param outputDir     directory the resulting JSON script file will be written to
     * @param name          base filename used for the output script (without extension)
     * @return the parsed MovieRecapScript object that was also written to disk
     */
    public MovieRecapScript generateScript(String synopsis, String srtFilePath, String outputDir, String name) {
        try {
            String srtContent = Files.readString(Path.of(srtFilePath), StandardCharsets.UTF_8);

            String userContent = """
                    MOVIE SYNOPSIS:
                    %s

                    MOVIE SRT:
                    %s
                    """.formatted(synopsis, srtContent);

            Content systemInstruction = Content.fromParts(
                    Part.fromText(loadFileAsString(recapPromptPath))
            );

            GenerateContentConfig config = GenerateContentConfig.builder()
                    .responseMimeType("application/json")
                    .responseSchema(buildMovieRecapSchema())
                    .systemInstruction(systemInstruction)
                    .temperature(0.8f)
                    .build();

            GenerateContentResponse response = geminiClient.models.generateContent(
                    geminiModel,
                    userContent,
                    config
            );

            MovieRecapScript script = objectMapper.readValue(response.text(), MovieRecapScript.class);
            log.info(response.text());

            File outputFile = new File(outputDir + "/" + name + "-recap-script.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, script);

            log.info("Movie recap script generation completed — {} scenes", script.getScenes().size());
            return script;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}