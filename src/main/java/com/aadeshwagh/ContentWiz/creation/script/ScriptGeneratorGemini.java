package com.aadeshwagh.ContentWiz.creation.script;

import com.aadeshwagh.ContentWiz.creation.entity.Script;
import com.aadeshwagh.ContentWiz.creation.entity.Shorts;
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
    public void generateScript(String storyOrPrompt, String scriptPromptPath, String outputDir, String name) {
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
                    .writeValue(new File(outputDir +"/"+ name+"-script.json"), script);
            log.info("Script generation completed — {} scenes", script.getScenes().size());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── Shorts generation ──────────────────────────────────────────────

    private Schema buildShortsSchema() {

        Schema shortSchema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(ImmutableMap.<String, Schema>builder()
                        .put("startScene",
                                Schema.builder()
                                        .type(Type.Known.INTEGER)
                                        .description("sceneNumber of the first scene included in this short. Must exactly match a sceneNumber from the source script.")
                                        .build())
                        .put("endScene",
                                Schema.builder()
                                        .type(Type.Known.INTEGER)
                                        .description("sceneNumber of the last scene included in this short. Must be >= startScene and must exactly match a sceneNumber from the source script.")
                                        .build())
                        .put("title",
                                Schema.builder()
                                        .type(Type.Known.STRING)
                                        .description("Catchy, scroll-stopping title for the short, under 60 characters. Written for YouTube Shorts / Instagram Reels, not the original video's title style.")
                                        .build())
                        .put("description",
                                Schema.builder()
                                        .type(Type.Known.STRING)
                                        .description("description that would be set to reel/short in youtube and instagram")
                                        .build())
                        .build())
                .required(List.of("startScene","endScene", "title","description"))
                .build();

        return Schema.builder()
                .type(Type.Known.ARRAY)
                .items(shortSchema)
                .description("Ordered list of standalone short clips derived from the source script.")
                .build();
    }

    private String buildShortsSystemPrompt() {
        return """
                You are a short-form video editor. You take a full long-form video script \
                (a JSON object with scenes, each scene having a sceneNumber, narration, mood, \
                cameraMovement, and transitionEffect) and identify which contiguous runs of \
                scenes would work as standalone YouTube Shorts / Instagram Reels.

                A short is defined as a contiguous range [startScene, endScene] from the source \
                script. You are not rewriting narration or inventing new content — you are \
                selecting which existing scenes belong together as one short and giving that \
                short a title.

                SELECTION RULES:

                1. Self-contained: a short must make sense to a viewer who has seen NONE of the \
                   other scenes. It needs its own setup and its own payoff inside the range you \
                   pick. Do not select a range that depends on context from scenes outside it.
                2. Hook first: the narration of startScene must work as a strong opening line on \
                   its own — it should not begin with a continuation word or phrase that implies \
                   prior context (e.g. "So then...", "That's why...", "Because of this...").
                3. Clean boundaries: prefer startScene/endScene values where the PRECEDING scene's \
                   transitionEffect is FADE or NONE rather than DISSOLVE, WIPE_LEFT, WIPE_RIGHT, \
                   SLIDE_LEFT, SLIDE_RIGHT, PIXELIZE, or RADIAL. Cutting a short out from the \
                   middle of a non-fade transition will look visually broken once rendered, so \
                   only do this if no clean boundary exists nearby and the content is strong \
                   enough to justify it.
                4. Length: target a TOTAL NARRATION WORD COUNT across the selected scene range of \
                   roughly 50 to 220 words. At natural spoken pacing this renders to approximately \
                   20 to 90 seconds, which is the effective range for Shorts/Reels. Do not select \
                   a range whose combined narration is shorter than ~50 words (too thin to stand \
                   alone) or longer than ~220 words (too long for the format).
                5. No overlap: scene ranges across different shorts in your output MAY overlap if \
                   the same moment genuinely serves two different standalone narratives, but do \
                   not produce near-duplicate shorts. Prefer non-overlapping ranges unless there \
                   is a clear creative reason.
                6. Coverage is not the goal: do not try to turn every scene into part of some \
                   short. Most long-form scripts will only contain a handful of segments strong \
                   enough to work as standalone shorts. Skip filler, setup, and connective scenes \
                   that don't have a self-contained hook/payoff.
                7. Title: each short's title must be written for Shorts/Reels — punchy, curiosity- \
                   or stakes-driven, under 60 characters. It should NOT simply reuse the parent \
                   video's title or description. It should reflect what THIS specific clip is \
                   about, not the whole video's topic.

                OUTPUT:
                Return between 3 and 8 shorts, ordered by startScene ascending. If the script \
                genuinely does not contain enough self-contained material for 3 good shorts, \
                return fewer rather than forcing weak selections — quality over quantity.

                Every startScene and endScene value you output MUST correspond exactly to a real \
                sceneNumber present in the input script. Do not invent scene numbers.
                """;
    }

    @Override
    public List<Shorts> generateShorts(Script script) {
        try {
            String scriptJson = objectMapper.writeValueAsString(script);

            Content systemInstruction = Content.fromParts(
                    Part.fromText(buildShortsSystemPrompt())
            );

            GenerateContentConfig config = GenerateContentConfig.builder()
                    .responseMimeType("application/json")
                    .responseSchema(buildShortsSchema())
                    .systemInstruction(systemInstruction)
                    .temperature(0.7f)
                    .build();

            GenerateContentResponse response = geminiClient.models.generateContent(
                    geminiModel,
                    scriptJson,
                    config
            );
            System.out.println(response.text());
            List<Shorts> shorts = objectMapper.readValue(

                    response.text(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Shorts.class)
            );

            log.info("Shorts generation completed — {} shorts derived from {} scenes",
                    shorts.size(), script.getScenes().size());

            return shorts;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}