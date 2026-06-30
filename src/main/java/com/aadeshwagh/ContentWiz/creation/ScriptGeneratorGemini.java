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
import java.util.List;

@Service
@Slf4j
public class ScriptGeneratorGemini implements ScriptGenerator {

    private final String geminiModel;
    private final ObjectMapper objectMapper;
    private final Client geminiClient;


    @Autowired
    public ScriptGeneratorGemini(
            @Value("${google.gemini.model}") String geminiModel,
            ObjectMapper objectMapper,
            @Value("${google.gemini.api.key}") String apiKey) {
        this.geminiModel = geminiModel;
        this.objectMapper = objectMapper;
        this.geminiClient = Client.builder()
                .apiKey(apiKey)
                .build();
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
                .properties(ImmutableMap.<String, Schema>builder()
                        .put("sceneNumber",
                                Schema.builder()
                                        .type(Type.Known.INTEGER)
                                        .description("Sequential scene number starting from 1.")
                                        .build())
                        .put("narration",
                                Schema.builder()
                                        .type(Type.Known.STRING)
                                        .description("Spoken narration for Chatterbox Turbo TTS. Written with rhythm variation, contractions, and optional paralinguistic tags.")
                                        .build())
                        .put("mood",
                                Schema.builder()
                                        .type(Type.Known.STRING)
                                        .enum_(List.of(
                                                "HAPPY", "EXCITED", "CHEERFUL", "AMUSED",
                                                "SAD", "ANGRY", "ANXIOUS", "MELANCHOLIC",
                                                "NEUTRAL", "CALM", "SINCERE", "DRAMATIC",
                                                "WHISPER", "WONDER", "CONFUSED", "SARCASTIC"
                                        ))
                                        .description("Emotional register of the scene. Must be exactly one of the approved uppercase values.")
                                        .build())
                        .put("imagePrompt",
                                Schema.builder()
                                        .type(Type.Known.STRING)
                                        .description("Scene image description for Gemini Imagen. No style words. Describes subject, action, setting, and camera framing.")
                                        .build())
                        .put("cameraMovement",
                                Schema.builder()
                                        .type(Type.Known.STRING)
                                        .enum_(List.of(
                                                "ZOOM_IN", "ZOOM_OUT",
                                                "PAN_LEFT", "PAN_RIGHT",
                                                "PAN_UP", "PAN_DOWN",
                                                "STATIC"
                                        ))
                                        .description("Ken Burns camera movement applied to the scene image. Chosen based on the emotional direction of the scene.")
                                        .build())
                        .put("transitionEffect",
                                Schema.builder()
                                        .type(Type.Known.STRING)
                                        .enum_(List.of(
                                                "FADE", "DISSOLVE",
                                                "WIPE_LEFT", "WIPE_RIGHT",
                                                "SLIDE_LEFT", "SLIDE_RIGHT",
                                                "PIXELIZE", "RADIAL", "NONE"
                                        ))
                                        .description("Visual transition from this scene into the next. Chosen based on the editorial relationship between this scene and the next.")
                                        .build())
                        .build())
                .required(List.of("sceneNumber", "narration", "mood", "imagePrompt", "cameraMovement", "transitionEffect"))
                .build();

        return Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(ImmutableMap.<String, Schema>builder()
                        .put("title",
                                Schema.builder()
                                        .type(Type.Known.STRING)
                                        .description("Engaging video title under 80 characters.")
                                        .build())
                        .put("description",
                                Schema.builder()
                                        .type(Type.Known.STRING)
                                        .description("Description of whole topic what its is about and all")
                                        .build())
                        .put("musicPrompt",
                                Schema.builder()
                                        .type(Type.Known.STRING)
                                        .description("Background music description for AI music generation. Prose only, 60-120 words, instrumental only.")
                                        .build())
                        .put("scenes",
                                Schema.builder()
                                        .type(Type.Known.ARRAY)
                                        .items(sceneSchema)
                                        .description("Ordered list of scenes. Between 35 and 80 scenes.")
                                        .build())
                        .build())
                .required(List.of("title", "musicPrompt", "scenes","description"))
                .build();
    }

    @Override
    public void generateScript(String storyOrPrompt, String scriptPromptPath ,String outputDir) {
        try {
            Content systemInstruction = Content.fromParts(
                    Part.fromText(loadPromptFromFile(scriptPromptPath))
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

            Script script = objectMapper.readValue(response.text(), Script.class);
            log.info(response.text());
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(outputDir + "/script.json"), script);
            log.info("Script generation completed — {} scenes", script.getScenes().size());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}