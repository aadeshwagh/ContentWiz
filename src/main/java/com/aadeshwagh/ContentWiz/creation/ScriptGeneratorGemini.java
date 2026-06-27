package com.aadeshwagh.ContentWiz.creation;

import com.aadeshwagh.ContentWiz.creation.entity.Script;
import com.google.common.collect.ImmutableMap;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class ScriptGeneratorGemini implements ScriptGenerator {

    private final String geminiModel;
    private final ObjectMapper objectMapper;
    private final Client geminiClient;
    private final String systemPrompt;

    @Autowired
    public ScriptGeneratorGemini(@Value("${google.gemini.model}") String geminiModel, ObjectMapper objectMapper, @Value("${google.gemini.api.key}") String apiKey, @Value("${content.script.system.prompt}") String scriptPromptPath) {
        this.geminiModel = geminiModel;
        this.objectMapper = objectMapper;
        this.geminiClient = Client.builder()
                .apiKey(apiKey)
                .build();

        this.systemPrompt = loadPromptFromFile(scriptPromptPath);
    }

    private String loadPromptFromFile(String path) {
        try {
            Resource resource = new FileSystemResource(path);
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load system prompt file: " + path, e);
        }
    }

    private Schema buildScriptSchema() {

        Schema sceneSchema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(ImmutableMap.of(
                        "sceneNumber",
                        Schema.builder()
                                .type(Type.Known.INTEGER)
                                .description("Sequential scene number starting from 1.")
                                .build(),

                        "duration",
                        Schema.builder()
                                .type(Type.Known.INTEGER)
                                .description("Duration in seconds. Must be between 8 and 20.")
                                .build(),

                        "narration",
                        Schema.builder()
                                .type(Type.Known.STRING)
                                .description("Spoken narration for Orpheus TTS 3B.")
                                .build(),

                        "mood",
                        Schema.builder()
                                .type(Type.Known.STRING)
                                .description("Scene mood.")
                                .build(),

                        "imagePrompt",
                        Schema.builder()
                                .type(Type.Known.STRING)
                                .description("Prompt for image generation.")
                                .build()
                ))
                .required(List.of("sceneNumber", "duration", "narration", "mood", "imagePrompt"))
                .build();

        return Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(ImmutableMap.of(
                        "title",
                        Schema.builder()
                                .type(Type.Known.STRING)
                                .description("Engaging video title.")
                                .build(),

                        "musicPrompt",
                        Schema.builder()
                                .type(Type.Known.STRING)
                                .description("Background music prompt.")
                                .build(),

                        "scenes",
                        Schema.builder()
                                .type(Type.Known.ARRAY)
                                .items(sceneSchema)
                                .description("List of scenes.")
                                .build()
                ))
                .required(List.of("title", "musicPrompt", "scenes"))
                .build();
    }
    @Override
    public Script generateScript(String storyOrPrompt) {
        try{
            Content systemInstruction = Content.fromParts(
                    Part.fromText(systemPrompt)
            );

            GenerateContentConfig config = GenerateContentConfig.builder()
                    .responseMimeType("application/json")
                    .responseSchema(buildScriptSchema())
                    .systemInstruction(systemInstruction)
                    .temperature(0.8f)
                    .build();

            GenerateContentResponse response = geminiClient.models.generateContent(
                    geminiModel,
                    storyOrPrompt,
                    config
            );
            return objectMapper.readValue(response.text(),Script.class);
        }catch (Exception e){
            throw new RuntimeException(e);
        }

    }
}