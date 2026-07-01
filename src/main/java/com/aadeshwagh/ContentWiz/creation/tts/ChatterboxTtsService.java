package com.aadeshwagh.ContentWiz.creation.tts;

import com.aadeshwagh.ContentWiz.creation.entity.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatterboxTtsService {

    private String pythonExecutable;

    @Value("${chatterbox.python.script-path}")
    private String scriptPath;

    @PostConstruct
    public void init() {
        setupPythonEnvironment();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Generate TTS for every scene in the script in a single Python process.
     * The model is loaded once; each scene is saved as <outputFolder>/<sceneNumber>.wav
     * The Emotion applies to the ENTIRE script — every scene is voiced with the
     * same exaggeration / temperature / cfg_weight.
     *
     * @param script       the full script with scenes
     * @param emotion      the single emotion/tone applied across all scenes
     * @param voice        which built-in voice to use
     * @param outputFolder directory where scene WAV files will be written
     */
    public void generateForScript(Script script, Emotion emotion, ChatterboxVoice voice, String outputFolder) {
        if (script == null || script.getScenes() == null || script.getScenes().isEmpty()) {
            throw new IllegalArgumentException("Script must contain at least one scene");
        }

        Emotion tone = emotion != null ? emotion : Emotion.NEUTRAL;
        ChatterboxVoice selectedVoice = voice != null ? voice : ChatterboxVoice.LUCY;

        log.info("Starting TTS batch | script='{}' | scenes={} | emotion={} | voice={} | output={}",
                script.getTitle(), script.getScenes().size(), tone, selectedVoice, outputFolder);

        // Serialise scenes to a JSON array — Python will iterate them with one model load
        String scenesJson = buildScenesJson(script.getScenes());

        List<String> command = buildBatchCommand(scenesJson, tone, selectedVoice, null, outputFolder);
        runProcess(command);

        log.info("TTS batch complete | {} scenes written to {}",
                script.getScenes().size(), outputFolder);
    }

    /**
     * Generate TTS for every scene of a movie recap script in a single Python process.
     * The model is loaded once; each scene is saved as <outputFolder>/<sceneNumber>.wav,
     * where sceneNumber is the scene's 1-indexed position in script.getScenes() — this
     * matches the numbering RecapVideoAssembly expects when it looks up narration files.
     * The Emotion applies to the ENTIRE script — every scene is voiced with the same
     * exaggeration / temperature / cfg_weight. Voicing uses a custom voice reference WAV,
     * since MovieRecapScript has no per-scene or script-level built-in voice concept.
     *
     * @param script             the movie recap script with ordered scenes
     * @param emotion            the single emotion/tone applied across all scenes
     * @param voiceReferencePath path to a reference WAV used to clone the narrator voice
     * @param outputFolder       directory where scene WAV files will be written
     */
    public void generateForMovieScript(MovieRecapScript script, Emotion emotion, String voiceReferencePath, String outputFolder) {
        if (script == null || script.getScenes() == null || script.getScenes().isEmpty()) {
            throw new IllegalArgumentException("Script must contain at least one scene");
        }
        if (voiceReferencePath == null || voiceReferencePath.isBlank()) {
            throw new IllegalArgumentException("voiceReferencePath must not be blank");
        }

        Emotion tone = emotion != null ? emotion : Emotion.NEUTRAL;

        log.info("Starting TTS batch | script='{}' | scenes={} | emotion={} | voiceRef={} | output={}",
                script.getTitle(), script.getScenes().size(), tone, voiceReferencePath, outputFolder);

        String scenesJson = buildMovieScenesJson(script.getScenes());
        List<String> command = buildBatchCommand(scenesJson, tone, null, voiceReferencePath, outputFolder);
        runProcess(command);

        log.info("TTS batch complete | {} scenes written to {}",
                script.getScenes().size(), outputFolder);
    }

    /**
     * Generate TTS for every scene using a custom voice reference WAV.
     * The Emotion applies to the ENTIRE script — every scene is voiced with the
     * same exaggeration / temperature / cfg_weight.
     */
    public void generateForScript(Script script, Emotion emotion, String voiceReferencePath, String outputFolder) {
        if (script == null || script.getScenes() == null || script.getScenes().isEmpty()) {
            throw new IllegalArgumentException("Script must contain at least one scene");
        }

        Emotion tone = emotion != null ? emotion : Emotion.NEUTRAL;

        log.info("Starting TTS batch | script='{}' | scenes={} | emotion={} | voiceRef={} | output={}",
                script.getTitle(), script.getScenes().size(), tone, voiceReferencePath, outputFolder);

        String scenesJson = buildScenesJson(script.getScenes());
        List<String> command = buildBatchCommand(scenesJson, tone, null, voiceReferencePath, outputFolder);
        runProcess(command);

        log.info("TTS batch complete | {} scenes written to {}",
                script.getScenes().size(), outputFolder);
    }

    /**
     * Generate a single clip using a built-in ChatterboxVoice.
     */
    public void generateAndSave(String text, Emotion emotion, String folder,
                                String fileName, ChatterboxVoice voice) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Text must not be blank");

        Emotion tone = emotion != null ? emotion : Emotion.NEUTRAL;
        String outputPath = Paths.get(folder, fileName).toAbsolutePath().toString();

        log.info("Generating single clip | emotion={} | voice={} | output={}", tone, voice, outputPath);

        List<String> command = buildSingleCommand(text, tone, outputPath, voice, null);
        runProcess(command);

        log.info("Audio saved → {}", outputPath);
    }

    /**
     * Generate a single clip using a custom voice reference WAV path.
     */
    public void generateAndSaveWithRef(String text, Emotion emotion, String folder,
                                       String fileName, String voiceReferencePath) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Text must not be blank");

        Emotion tone = emotion != null ? emotion : Emotion.NEUTRAL;
        String outputPath = Paths.get(folder, fileName).toAbsolutePath().toString();

        log.info("Generating single clip | emotion={} | voiceRef={} | output={}", tone, voiceReferencePath, outputPath);

        List<String> command = buildSingleCommand(text, tone, outputPath, null, voiceReferencePath);
        runProcess(command);

        log.info("Audio saved → {}", outputPath);
    }

    // -------------------------------------------------------------------------
    // Command builders
    // -------------------------------------------------------------------------

    /**
     * Build the batch command: passes all scenes as a JSON array in one go,
     * along with a single exaggeration/temperature/cfg_weight derived from the
     * script-level Emotion. Python applies these same params to every scene.
     */
    private List<String> buildBatchCommand(String scenesJson, Emotion tone, ChatterboxVoice voice,
                                           String voiceRef, String outputFolder) {
        List<String> cmd = new ArrayList<>();
        cmd.add(pythonExecutable);
        cmd.add(scriptPath);

        cmd.add("--scenes-json");
        cmd.add(scenesJson);

        cmd.add("--output-folder");
        cmd.add(outputFolder);

        cmd.add("--exaggeration");
        cmd.add(String.valueOf(tone.getExaggeration()));

        cmd.add("--temperature");
        cmd.add(String.valueOf(tone.getTemperature()));

        cmd.add("--cfg_weight");
        cmd.add(String.valueOf(tone.getCfgWeight()));

        addVoiceArgs(cmd, voice, voiceRef);
        return cmd;
    }

    /**
     * Build the single-scene command (for one-off generation).
     */
    private List<String> buildSingleCommand(String text, Emotion tone, String outputPath,
                                            ChatterboxVoice voice, String voiceRef) {
        List<String> cmd = new ArrayList<>();
        cmd.add(pythonExecutable);
        cmd.add(scriptPath);

        cmd.add("--text");
        cmd.add(text);

        cmd.add("--exaggeration");
        cmd.add(String.valueOf(tone.getExaggeration()));

        cmd.add("--temperature");
        cmd.add(String.valueOf(tone.getTemperature()));

        cmd.add("--cfg_weight");
        cmd.add(String.valueOf(tone.getCfgWeight()));

        cmd.add("--output_path");
        cmd.add(outputPath);

        addVoiceArgs(cmd, voice, voiceRef);
        return cmd;
    }

    private void addVoiceArgs(List<String> cmd, ChatterboxVoice voice, String voiceRef) {
        if (voice != null) {
            cmd.add("--voice");
            cmd.add(voice.getVoiceName());
        } else if (voiceRef != null && !voiceRef.isBlank()) {
            cmd.add("--voice_ref");
            cmd.add(voiceRef);
        }
    }

    /**
     * Serialise a list of scenes to a compact JSON array.
     * Avoids pulling in Jackson/Gson — the fields are simple enough to build manually.
     * Format: [{"sceneNumber":1,"narration":"..."}, ...]
     * No mood field — emotion is now applied once for the whole script via CLI args.
     */
    private String buildScenesJson(List<Scene> scenes) {
        return scenes.stream()
                .filter(s -> s.getNarration() != null && !s.getNarration().isBlank())
                .map(s -> String.format(
                        "{\"sceneNumber\":%d,\"narration\":%s}",
                        s.getSceneNumber(),
                        jsonString(s.getNarration())
                ))
                .collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * Serialise a list of MovieRecapScene objects to the same compact JSON array format
     * the Python script expects: [{"sceneNumber":1,"narration":"..."}, ...]
     * <p>
     * Uses each scene's own sceneNo field (as produced by the script generation model)
     * rather than list position, so scene N here always corresponds to scene N's
     * clipStartTime/clipEndTime in the script and to "N.wav" as consumed downstream by
     * RecapVideoAssembly.
     */
    private String buildMovieScenesJson(List<MovieRecapScene> scenes) {
        List<String> jsonScenes = new ArrayList<>();

        for (MovieRecapScene scene : scenes) {
            String sceneNo = scene.getSceneNo();
            String narration = scene.getNarration();

            if (narration == null || narration.isBlank()) {
                log.warn("Scene {} has blank narration — skipping TTS generation for this scene", sceneNo);
                continue;
            }
            if (sceneNo == null || sceneNo.isBlank()) {
                throw new IllegalArgumentException("MovieRecapScene is missing sceneNo (narration starts: \""
                        + narration.substring(0, Math.min(40, narration.length())) + "...\")");
            }

            int sceneNumber;
            try {
                sceneNumber = Integer.parseInt(sceneNo.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("MovieRecapScene has a non-numeric sceneNo: " + sceneNo, e);
            }

            jsonScenes.add(String.format(
                    "{\"sceneNumber\":%d,\"narration\":%s}",
                    sceneNumber,
                    jsonString(narration)
            ));
        }

        return jsonScenes.stream().collect(Collectors.joining(",", "[", "]"));
    }

    /** Minimal JSON string escaping for narration text. */
    private String jsonString(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }

    // -------------------------------------------------------------------------
    // Process execution
    // -------------------------------------------------------------------------

    private void runProcess(List<String> command) {
        try {
            // Ensure output directory exists for single-scene mode
            int outIdx = command.indexOf("--output_path");
            if (outIdx != -1) {
                Files.createDirectories(Paths.get(command.get(outIdx + 1)).getParent());
            }
            int folderIdx = command.indexOf("--output-folder");
            if (folderIdx != -1) {
                Files.createDirectories(Paths.get(command.get(folderIdx + 1)));
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            StringBuilder errOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errOutput.append(line).append("\n");
                    if (line.startsWith("INFO:") || line.startsWith("OK:")) {
                        log.info("[chatterbox] {}", line);
                    } else if (line.startsWith("WARN:")) {
                        log.warn("[chatterbox] {}", line);
                    } else if (line.startsWith("ERROR:")) {
                        log.error("[chatterbox] {}", line);
                    } else {
                        log.debug("[chatterbox] {}", line);
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new ChatterboxTtsException(
                        "Python script exited with code " + exitCode + "\n" + errOutput);
            }

        } catch (ChatterboxTtsException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ChatterboxTtsException(
                    "Failed to run Chatterbox Python script: " + ex.getMessage(), ex);
        }
    }

    // -------------------------------------------------------------------------
    // Environment setup
    // -------------------------------------------------------------------------

    private void setupPythonEnvironment() {
        try {
            String projectDir = System.getProperty("user.dir");
            Path venvPath = Paths.get(projectDir, "venv");
            Path venvPython = venvPath.resolve("bin").resolve("python");

            if (!Files.exists(venvPython)) {
                log.info("Python venv not found — creating at {}", venvPath);
                runCommand(List.of("python3.11", "-m", "venv", venvPath.toString()));

                log.info("Upgrading pip...");
                runCommand(List.of(venvPython.toString(), "-m", "pip", "install", "--upgrade", "pip"));

                // 1. Force install dependencies that need fresh downloads, bypassing the cache
                log.info("Force-installing core dependencies without cache...");
                runCommand(List.of(
                        venvPython.toString(), "-m", "pip", "install",
                        "--no-cache-dir", "--force-reinstall",
                        "torch", "torchaudio", "soundfile", "peft"
                ));

                // 2. Standard install for chatterbox-tts so it uses existing/cached instances if available
                log.info("Installing chatterbox-tts...");
                runCommand(List.of(
                        venvPython.toString(), "-m", "pip", "install",
                        "chatterbox-tts"
                ));
            }

            pythonExecutable = venvPython.toString();
            log.info("Using Python executable: {}", pythonExecutable);

        } catch (Exception ex) {
            throw new RuntimeException("Failed to setup Python environment", ex);
        }
    }

    private void runCommand(List<String> command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[setup] {}", line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed: " + String.join(" ", command));
        }
    }

    // -------------------------------------------------------------------------
    // Exception
    // -------------------------------------------------------------------------

    public static class ChatterboxTtsException extends RuntimeException {
        public ChatterboxTtsException(String message) { super(message); }
        public ChatterboxTtsException(String message, Throwable cause) { super(message, cause); }
    }
}