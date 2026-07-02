package com.aadeshwagh.ContentWiz.creation.videoAssembly;

import com.aadeshwagh.ContentWiz.creation.entity.CameraMovement;
import com.aadeshwagh.ContentWiz.creation.entity.Scene;
import com.aadeshwagh.ContentWiz.creation.entity.Script;
import com.aadeshwagh.ContentWiz.creation.entity.TransitionEffect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class VideoAssemblyService {

    private static final int FPS = 25;
    private static final String VIDEO_CODEC = "libx264";
    private static final String AUDIO_CODEC = "aac";
    private static final String PIXEL_FORMAT = "yuv420p";
    private static final double PAUSE_BASE      = 0.4;  // minimum pause for any scene
    private static final double PAUSE_PER_SEC   = 0.06; // extra pause per second of audio
    private static final double PAUSE_MAX       = 1.2;  // cap so long scenes don't over-linger
    private static final double PAUSE_FINAL_EXTRA = 0.5; // extra buffer on the last scene only

    private static final int SHORT_W = 1080;
    private static final int SHORT_H = 1920;
    private static final int BACKDROP_BLUR_SIGMA = 20;

    // --- Shorts: speed-up ---
    private static final double SHORT_SPEED_FACTOR = 1.25;

    // --- Shorts: title (drawn in the top letterbox band) ---
    private static final String TITLE_FONT_FILE = "/System/Library/Fonts/Supplemental/Poppins-SemiBold.ttf";
    private static final int TITLE_FONT_SIZE = 44;
    private static final int TITLE_WRAP_CHARS = 26;

    // --- Shorts: karaoke-style subtitles (drawn in the bottom letterbox band) ---
    private static final int SUBTITLE_WORDS_PER_GROUP = 3;
    private static final String SUBTITLE_FONT_FAMILY = "Poppins SemiBold"; // fontconfig family name
    private static final int SUBTITLE_FONT_SIZE = 44;
    private static final String SUBTITLE_HIGHLIGHT_COLOR = "&H0000FFFF&"; // ASS &HAABBGGRR& — yellow, spoken word
    private static final String SUBTITLE_BASE_COLOR = "&H00FFFFFF&";      // white, not-yet-spoken words


    private double pauseFor(double audioDuration, boolean isLast) {
        double pause = Math.min(PAUSE_BASE + audioDuration * PAUSE_PER_SEC, PAUSE_MAX);
        return isLast ? pause + PAUSE_FINAL_EXTRA : pause;
    }

    // Mood keywords → transition duration (seconds)
    private static final Map<String, Double> MOOD_TRANSITION_DURATION = Map.ofEntries(
            Map.entry("action",      0.3),
            Map.entry("intense",     0.3),
            Map.entry("energetic",   0.4),
            Map.entry("exciting",    0.4),
            Map.entry("dramatic",    0.6),
            Map.entry("suspense",    0.6),
            Map.entry("mysterious",  0.7),
            Map.entry("neutral",     0.7),
            Map.entry("informative", 0.7),
            Map.entry("calm",        1.0),
            Map.entry("peaceful",    1.0),
            Map.entry("reflective",  1.1),
            Map.entry("emotional",   1.2),
            Map.entry("melancholic", 1.2),
            Map.entry("serene",      1.3)
    );

    private static final double DEFAULT_TRANSITION_DURATION = 0.7;
    private static final double MIN_SCENE_DURATION_FOR_TRANSITION = 1.5;

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------
    public List<Path> assembleShortVideos(Script script, List<com.aadeshwagh.ContentWiz.creation.entity.Shorts> shorts,
                                          String workingDir, String outputDir)
            throws IOException, InterruptedException {

        if (shorts == null || shorts.isEmpty()) {
            throw new IllegalArgumentException("No shorts provided");
        }

        Map<Integer, Scene> sceneByNumber = script.getScenes().stream()
                .collect(java.util.stream.Collectors.toMap(Scene::getSceneNumber, s -> s));

        Files.createDirectories(Paths.get(outputDir));

        List<Path> outputs = new ArrayList<>();
        for (com.aadeshwagh.ContentWiz.creation.entity.Shorts shortDef : shorts) {
            outputs.add(assembleSingleShort(shortDef, sceneByNumber, workingDir, outputDir));
        }
        return outputs;
    }

    private Path assembleSingleShort(com.aadeshwagh.ContentWiz.creation.entity.Shorts shortDef,
                                     Map<Integer, Scene> sceneByNumber,
                                     String workingDir, String outputDir)
            throws IOException, InterruptedException {

        List<Scene> scenes = new ArrayList<>();
        for (int n = shortDef.getStartScene(); n <= shortDef.getEndScene(); n++) {
            Scene scene = sceneByNumber.get(n);
            if (scene == null) {
                throw new IllegalStateException(
                        "Short \"" + shortDef.getTitle() + "\" references sceneNumber " + n
                                + " which does not exist in the source script");
            }
            scenes.add(scene);
        }

        if (scenes.isEmpty()) {
            throw new IllegalArgumentException(
                    "Short \"" + shortDef.getTitle() + "\" resolves to zero scenes (startScene="
                            + shortDef.getStartScene() + ", endScene=" + shortDef.getEndScene() + ")");
        }

        validateAssets(scenes, workingDir);

        double[] audioDurations = probeAudioDurations(scenes, workingDir);
        double[] pauseDurations = derivePauseDurations(scenes, audioDurations);
        double[] transitionDurations = deriveTransitionDurations(scenes, audioDurations, pauseDurations);

        // Figure out the letterbox band size from the actual source image aspect ratio
        int[] baseDims = probeImageDimensions(scenes.get(0), workingDir);
        int bandHeight = computeLetterboxBandHeight(baseDims[0], baseDims[1]);

        String sanitized = sanitizeTitle(shortDef.getTitle());
        Path titleFile = writeTextFile(wrapText(shortDef.getTitle(), TITLE_WRAP_CHARS),
                workingDir, "short_title_" + sanitized + ".txt");

        log.info("Assembling short \"{}\" — scenes {}-{}, {} scene(s)",
                shortDef.getTitle(), shortDef.getStartScene(), shortDef.getEndScene(), scenes.size());

        Path outputPath = Paths.get(outputDir, sanitized + ".mp4");

        List<String> command = buildShortFfmpegCommand(
                scenes, audioDurations, transitionDurations, pauseDurations,
                titleFile, bandHeight, workingDir, sanitized, outputPath.toString());

        runProcess(command, workingDir);

        log.info("Short assembled: {}", outputPath);
        return outputPath;
    }

    private List<String> buildShortFfmpegCommand(List<Scene> scenes, double[] audioDurations,
                                                 double[] transitionDurations, double[] pauseDurations,
                                                 Path titleFile, int bandHeight,
                                                 String workingDir, String shortSanitizedName,
                                                 String outputPath) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-y");

        for (int i = 0; i < scenes.size(); i++) {
            int n = scenes.get(i).getSceneNumber();
            double videoDuration = audioDurations[i] + pauseDurations[i];
            String sceneNum = formatSceneNumber(n);

            cmd.addAll(List.of("-loop", "1", "-t", String.valueOf(videoDuration),
                    "-i", workingDir + "/scene_" + sceneNum + ".png"));
            cmd.addAll(List.of("-i", workingDir + "/" + n + ".wav"));
        }

        cmd.addAll(List.of("-filter_complex",
                buildShortFilterComplex(scenes, audioDurations, transitionDurations, pauseDurations,
                        titleFile, bandHeight, workingDir, shortSanitizedName)));
        cmd.addAll(List.of("-map", "[vfinal]", "-map", "[afinal]"));
        cmd.addAll(List.of(
                "-c:v", VIDEO_CODEC,
                "-preset", "medium",
                "-crf", "23",
                "-c:a", AUDIO_CODEC,
                "-b:a", "192k",
                "-pix_fmt", PIXEL_FORMAT,
                "-movflags", "+faststart",
                outputPath
        ));

        return cmd;
    }

    private String buildShortFilterComplex(List<Scene> scenes, double[] audioDurations,
                                           double[] transitionDurations, double[] pauseDurations,
                                           Path titleFile, int bandHeight,
                                           String workingDir, String shortSanitizedName) throws IOException {
        StringBuilder fc = new StringBuilder();
        int count = scenes.size();

        for (int i = 0; i < count; i++) {
            Scene scene = scenes.get(i);
            int videoInput = i * 2;
            int audioInput = i * 2 + 1;
            double leadIn = (i > 0) ? transitionDurations[i - 1] : 0.0;

            Path assFile = buildAssSubtitleFile(scene, audioDurations[i], leadIn,
                    workingDir, shortSanitizedName, bandHeight);

            fc.append(buildShortCameraFilter(videoInput, i, scene, audioDurations[i], pauseDurations[i], leadIn,
                    titleFile, assFile, bandHeight));
            fc.append(String.format(
                    "[%d:a]atrim=0:%.3f,asetpts=PTS-STARTPTS,apad=whole_dur=%.3f[a%d];%n",
                    audioInput, audioDurations[i], audioDurations[i] + pauseDurations[i], i
            ));
        }

        fc.append(buildXfadeChain(scenes, audioDurations, transitionDurations, pauseDurations));
        fc.append(buildAudioConcat(count));

        // Speed up the fully assembled short by SHORT_SPEED_FACTOR
        fc.append(String.format("[vout]setpts=PTS/%.3f[vfinal];%n", SHORT_SPEED_FACTOR));
        fc.append(String.format("[aout]atempo=%.3f[afinal];%n", SHORT_SPEED_FACTOR));

        return fc.toString();
    }

    private String buildShortCameraFilter(int inputIndex, int sceneIndex, Scene scene,
                                          double audioDuration, double pauseDuration, double leadIn,
                                          Path titleFile, Path subtitleAssFile, int bandHeight) {
        int motionFrames = (int) Math.ceil(audioDuration * FPS);
        int totalFrames  = (int) Math.ceil((leadIn + audioDuration + pauseDuration) * FPS);

        String zoomExpr = buildZoomExpression(scene.getCameraMovement(), motionFrames);
        String xExpr    = buildXExpression(scene.getCameraMovement(), motionFrames);
        String yExpr    = buildYExpression(scene.getCameraMovement(), motionFrames);

        String titleFontPath = escapeDrawtextPath(TITLE_FONT_FILE);
        String titlePath     = escapeDrawtextPath(titleFile.toString());
        String assPath       = escapeDrawtextPath(subtitleAssFile.toString());

        // Title fixed in the top letterbox band. Drawn AFTER zoompan/trim so it stays put
        // regardless of camera movement.
        String titleDraw = String.format(
                "drawtext=fontfile='%s':textfile='%s':fontsize=%d:fontcolor=white:" +
                        "x=(w-text_w)/2:y=(%d-text_h)/2:line_spacing=4:" +
                        "shadowcolor=black@0.6:shadowx=1:shadowy=1",
                titleFontPath, titlePath, TITLE_FONT_SIZE, bandHeight
        );

        // Karaoke-style subtitles rendered by libass from the per-scene .ass file.
        String subtitleAss = String.format("ass=filename='%s'", assPath);

        return String.format(
                "[%d:v]" +
                        "scale=%d:%d:force_original_aspect_ratio=decrease," +
                        "pad=%d:%d:(ow-iw)/2:(oh-ih)/2:color=black," +
                        "scale=iw*2:ih*2," +
                        "zoompan=z='%s':x='%s':y='%s':d=%d:s=%dx%d:fps=%d," +
                        "trim=0:%.3f,setpts=PTS-STARTPTS," +
                        "%s,%s" +
                        "[v%d];%n",
                inputIndex,
                SHORT_W, SHORT_H,
                SHORT_W, SHORT_H,
                zoomExpr, xExpr, yExpr, totalFrames, SHORT_W, SHORT_H, FPS,
                leadIn + audioDuration + pauseDuration,
                subtitleAss, titleDraw,
                sceneIndex
        );
    }

    // -------------------------------------------------------------------------
    // Shorts helpers: letterbox band sizing, title text, karaoke subtitles
    // -------------------------------------------------------------------------

    /**
     * Computes the height of the black band that will appear above (and below) the
     * scaled image once it's fit into the SHORT_W x SHORT_H portrait canvas while
     * preserving aspect ratio (mirrors what scale=force_original_aspect_ratio=decrease does).
     */
    private int computeLetterboxBandHeight(int imgW, int imgH) {
        double scale = Math.min((double) SHORT_W / imgW, (double) SHORT_H / imgH);
        int scaledH = (int) Math.round(imgH * scale);
        return Math.max((SHORT_H - scaledH) / 2, 0);
    }

    private Path writeTextFile(String content, String workingDir, String filename) throws IOException {
        Path path = Paths.get(workingDir, filename);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    /** Simple greedy word-wrap so drawtext (which doesn't auto-wrap) gets sane line breaks. */
    private String wrapText(String text, int maxCharsPerLine) {
        if (text == null || text.isBlank()) return "";
        String[] words = text.trim().split("\\s+");
        StringBuilder result = new StringBuilder();
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (line.length() > 0 && line.length() + word.length() + 1 > maxCharsPerLine) {
                result.append(line).append("\n");
                line.setLength(0);
            }
            if (line.length() > 0) line.append(" ");
            line.append(word);
        }
        if (line.length() > 0) result.append(line);
        return result.toString();
    }

    /** Escapes a filesystem path for safe use as a drawtext filter option value. */
    private String escapeDrawtextPath(String path) {
        return path.replace("\\", "\\\\").replace(":", "\\:");
    }

    private record WordTiming(String word, double startSec, double durationSec) {}

    /**
     * Estimates per-word timing across the scene's narration audio, proportional to word
     * length (longer words get slightly more time; a small constant is added per word to
     * approximate the natural gap between words). This is an approximation — if Chatterbox
     * TTS ever exposes real word-level timestamps, swap this out for those instead.
     */
    private List<WordTiming> computeWordTimings(String narration, double audioDuration) {
        if (narration == null || narration.isBlank() || audioDuration <= 0) return List.of();
        String[] words = narration.trim().split("\\s+");
        double[] weights = new double[words.length];
        double totalWeight = 0;
        for (int i = 0; i < words.length; i++) {
            weights[i] = Math.max(words[i].length(), 1) + 1.0; // +1 approximates the gap after each word
            totalWeight += weights[i];
        }
        List<WordTiming> timings = new ArrayList<>();
        double cursor = 0;
        for (int i = 0; i < words.length; i++) {
            double dur = audioDuration * (weights[i] / totalWeight);
            timings.add(new WordTiming(words[i], cursor, dur));
            cursor += dur;
        }
        return timings;
    }

    /**
     * Builds a per-scene .ass subtitle file that shows SUBTITLE_WORDS_PER_GROUP words at a
     * time, with each word highlighted via ASS karaoke (\k) tags as it's "spoken".
     */
    private Path buildAssSubtitleFile(Scene scene, double audioDuration, double leadIn,
                                      String workingDir, String shortSanitizedName,
                                      int bandHeight) throws IOException {
        // NOTE: adjust this getter if narration text lives on a different field/method.
        String narration = scene.getNarration() != null ? scene.getNarration() : "";
        List<WordTiming> timings = computeWordTimings(narration, audioDuration);

        int fontSize = SUBTITLE_FONT_SIZE;
        int marginV = Math.max((bandHeight - (int) (fontSize * 1.3)) / 2, 20);

        StringBuilder ass = new StringBuilder();
        ass.append("[Script Info]\n");
        ass.append("ScriptType: v4.00+\n");
        ass.append("PlayResX: ").append(SHORT_W).append("\n");
        ass.append("PlayResY: ").append(SHORT_H).append("\n");
        ass.append("WrapStyle: 2\n\n");
        ass.append("[V4+ Styles]\n");
        ass.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n");
        ass.append(String.format(
                "Style: Caption,%s,%d,%s,%s,&H00000000&,&H00000000&,1,0,0,0,100,100,0,0,1,3,0,2,60,60,%d,1%n",
                SUBTITLE_FONT_FAMILY, fontSize, SUBTITLE_HIGHLIGHT_COLOR, SUBTITLE_BASE_COLOR, marginV
        ));
        ass.append("\n[Events]\n");
        ass.append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n");

        for (int i = 0; i < timings.size(); i += SUBTITLE_WORDS_PER_GROUP) {
            List<WordTiming> group = timings.subList(i, Math.min(i + SUBTITLE_WORDS_PER_GROUP, timings.size()));
            double groupStart = leadIn + group.get(0).startSec();
            double groupEnd   = leadIn + group.get(group.size() - 1).startSec() + group.get(group.size() - 1).durationSec();

            StringBuilder text = new StringBuilder();
            for (WordTiming wt : group) {
                int centiseconds = (int) Math.round(wt.durationSec() * 100);
                text.append("{\\k").append(Math.max(centiseconds, 1)).append("}").append(wt.word()).append(" ");
            }

            ass.append(String.format("Dialogue: 0,%s,%s,Caption,,0,0,0,,%s%n",
                    formatAssTime(groupStart), formatAssTime(groupEnd), text.toString().trim()));
        }

        Path path = Paths.get(workingDir, "sub_" + shortSanitizedName + "_" + scene.getSceneNumber() + ".ass");
        Files.writeString(path, ass.toString(), StandardCharsets.UTF_8);
        return path;
    }

    private String formatAssTime(double seconds) {
        if (seconds < 0) seconds = 0;
        int hours = (int) (seconds / 3600);
        int minutes = (int) ((seconds % 3600) / 60);
        double secsRemainder = seconds % 60;
        int secs = (int) secsRemainder;
        int centis = (int) Math.round((secsRemainder - secs) * 100);
        if (centis == 100) {
            centis = 0;
            secs += 1;
            if (secs == 60) {
                secs = 0;
                minutes += 1;
                if (minutes == 60) {
                    minutes = 0;
                    hours += 1;
                }
            }
        }
        return String.format("%d:%02d:%02d.%02d", hours, minutes, secs, centis);
    }

    public Path assembleVideo(Script script, String workingDir, String outputDir)
            throws IOException, InterruptedException {

        List<Scene> scenes = script.getScenes();
        if (scenes == null || scenes.isEmpty()) {
            throw new IllegalArgumentException("Script has no scenes");
        }

        validateAssets(scenes, workingDir);

        // Dynamically probe base dimensions from the first scene's image
        int[] dimensions = probeImageDimensions(scenes.get(0), workingDir);
        int baseW = dimensions[0];
        int baseH = dimensions[1];

        // Probe all audio durations up front
        double[] audioDurations = probeAudioDurations(scenes, workingDir);
        double[] pauseDurations      = derivePauseDurations(scenes, audioDurations);
        double[] transitionDurations = deriveTransitionDurations(scenes, audioDurations, pauseDurations);

        log.info("Base Video Dimensions: {}x{}", baseW, baseH);
        log.info("Scene durations: {}", formatDurations(audioDurations));
        log.info("Transition durations: {}", formatDurations(transitionDurations));

        Path outputPath = Paths.get(outputDir, sanitizeTitle(script.getTitle()) + ".mp4");
        Files.createDirectories(outputPath.getParent());

        List<String> command = buildFfmpegCommand(scenes, audioDurations, transitionDurations, pauseDurations,baseW, baseH, workingDir, outputPath.toString());

        log.info("Running FFmpeg — total scenes: {}, estimated video length: {:.1f}s",
                scenes.size(), estimateTotalDuration(audioDurations, transitionDurations));

        runProcess(command, workingDir);

        log.info("Video assembled: {}", outputPath);
        return outputPath;
    }
    private double[] derivePauseDurations(List<Scene> scenes, double[] audioDurations) {
        double[] pauses = new double[scenes.size()];
        for (int i = 0; i < scenes.size(); i++) {
            pauses[i] = pauseFor(audioDurations[i], i == scenes.size() - 1);
        }
        return pauses;
    }

    // -------------------------------------------------------------------------
    // Probing Methods (Audio & Image)
    // -------------------------------------------------------------------------

    private int[] probeImageDimensions(Scene firstScene, String workingDir) throws IOException {
        String sceneNum = formatSceneNumber(firstScene.getSceneNumber());
        Path imgPath = Paths.get(workingDir, "scene_" + sceneNum + ".png");
        BufferedImage img = ImageIO.read(imgPath.toFile());

        if (img == null) {
            throw new IOException("Failed to read image to determine dimensions: " + imgPath);
        }

        int w = img.getWidth();
        int h = img.getHeight();

        // yuv420p encoding strictly requires even height and width numbers
        if (w % 2 != 0) w--;
        if (h % 2 != 0) h--;

        return new int[]{w, h};
    }

    private double[] probeAudioDurations(List<Scene> scenes, String workingDir)
            throws IOException, InterruptedException {
        double[] durations = new double[scenes.size()];
        for (int i = 0; i < scenes.size(); i++) {
            int n = scenes.get(i).getSceneNumber();
            String audioPath = workingDir + "/" + n + ".wav";
            durations[i] = probeFileDuration(audioPath);
        }
        return durations;
    }

    private double probeFileDuration(String filePath) throws IOException, InterruptedException {
        List<String> cmd = List.of(
                "ffprobe", "-v", "error", "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1", filePath
        );

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        Process process = pb.start();

        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            output = reader.readLine();
        }

        int exitCode = process.waitFor();
        if (exitCode != 0 || output == null || output.isBlank()) {
            throw new RuntimeException("ffprobe failed for: " + filePath);
        }
        return Double.parseDouble(output.trim());
    }

    // -------------------------------------------------------------------------
    // Transition duration derivation
    // -------------------------------------------------------------------------

    private double[] deriveTransitionDurations(List<Scene> scenes, double[] audioDurations, double[] pauseDurations) {
        double[] transitions = new double[scenes.size()];
        for (int i = 0; i < scenes.size(); i++) {
            double moodDuration = resolveMoodDuration(scenes.get(i).getMood());
            // Cap transition so it never eats into narration — bounded by the pause on both sides
            double cap = Math.min(pauseDurations[i], i + 1 < scenes.size() ? pauseDurations[i + 1] : pauseDurations[i]);
            if (audioDurations[i] < MIN_SCENE_DURATION_FOR_TRANSITION) {
                transitions[i] = 0.0;
            } else {
                transitions[i] = Math.min(moodDuration, cap);
            }
        }
        return transitions;
    }

    private double resolveMoodDuration(String mood) {
        if (mood == null || mood.isBlank()) return DEFAULT_TRANSITION_DURATION;
        String lowerMood = mood.toLowerCase();
        if (MOOD_TRANSITION_DURATION.containsKey(lowerMood)) {
            return MOOD_TRANSITION_DURATION.get(lowerMood);
        }
        return MOOD_TRANSITION_DURATION.entrySet().stream()
                .filter(e -> lowerMood.contains(e.getKey()))
                .mapToDouble(Map.Entry::getValue)
                .average()
                .orElse(DEFAULT_TRANSITION_DURATION);
    }

    private String formatSceneNumber(int n) {
        return String.format("%02d", n);
    }

    // -------------------------------------------------------------------------
    // FFmpeg command builder
    // -------------------------------------------------------------------------

    // buildFfmpegCommand signature
    private List<String> buildFfmpegCommand(List<Scene> scenes, double[] audioDurations,
                                            double[] transitionDurations, double[] pauseDurations,
                                            int baseW, int baseH, String workingDir, String outputPath) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-y");

        for (int i = 0; i < scenes.size(); i++) {
            int n = scenes.get(i).getSceneNumber();
            double videoDuration = audioDurations[i] + pauseDurations[i];
            String sceneNum = formatSceneNumber(n);

            cmd.addAll(List.of("-loop", "1", "-t", String.valueOf(videoDuration),
                    "-i", workingDir + "/scene_" + sceneNum + ".png"));
            cmd.addAll(List.of("-i", workingDir + "/" + n + ".wav"));
        }

        cmd.addAll(List.of("-filter_complex", buildFilterComplex(scenes, audioDurations, transitionDurations, pauseDurations,baseW, baseH)));
        cmd.addAll(List.of("-map", "[vout]", "-map", "[aout]"));
        cmd.addAll(List.of(
                "-c:v", VIDEO_CODEC,
                "-preset", "medium",
                "-crf", "23",
                "-c:a", AUDIO_CODEC,
                "-b:a", "192k",
                "-pix_fmt", PIXEL_FORMAT,
                "-movflags", "+faststart",
                outputPath
        ));

        return cmd;
    }

    // -------------------------------------------------------------------------
    // filter_complex builder
    // -------------------------------------------------------------------------

    private String buildFilterComplex(List<Scene> scenes, double[] audioDurations,
                                      double[] transitionDurations, double[] pauseDurations,
                                      int baseW, int baseH) {
        StringBuilder fc = new StringBuilder();
        int count = scenes.size();

        for (int i = 0; i < count; i++) {
            Scene scene = scenes.get(i);
            int videoInput = i * 2;
            int audioInput = i * 2 + 1;
            double leadIn = (i>0) ? transitionDurations[i-1] : 0.0;

            // Pass pauseDurations[i] into buildCameraFilter
            fc.append(buildCameraFilter(videoInput, i, scene, audioDurations[i], pauseDurations[i], baseW, baseH , leadIn));
            fc.append(String.format(
                    "[%d:a]atrim=0:%.3f,asetpts=PTS-STARTPTS,apad=whole_dur=%.3f[a%d];%n",
                    audioInput, audioDurations[i], audioDurations[i] + pauseDurations[i], i
            ));
        }

        fc.append(buildXfadeChain(scenes, audioDurations, transitionDurations,pauseDurations));
        fc.append(buildAudioConcat(count));

        return fc.toString();
    }

    private String buildCameraFilter(int inputIndex, int sceneIndex, Scene scene,
                                     double audioDuration, double pauseDuration,
                                     int baseW, int baseH, double leadIn) {
        int motionFrames = (int) Math.ceil(audioDuration * FPS);       // camera moves during narration
        int totalFrames  = (int) Math.ceil((leadIn + audioDuration + pauseDuration) * FPS); // stream runs full length

        String zoomExpr = buildZoomExpression(scene.getCameraMovement(), motionFrames);
        String xExpr    = buildXExpression(scene.getCameraMovement(), motionFrames);
        String yExpr    = buildYExpression(scene.getCameraMovement(), motionFrames);

        return String.format(
                "[%d:v]" +
                        "scale=%d:%d:force_original_aspect_ratio=increase," +
                        "crop=%d:%d," +
                        "scale=iw*2:ih*2," +
                        "zoompan=z='%s':x='%s':y='%s':d=%d:s=%dx%d:fps=%d," +
                        "trim=0:%.3f,setpts=PTS-STARTPTS" +
                        "[v%d];%n",
                inputIndex,
                baseW, baseH,
                baseW, baseH,
                zoomExpr, xExpr, yExpr, totalFrames, baseW, baseH, FPS,  // d=totalFrames
                leadIn + audioDuration + pauseDuration,
                sceneIndex
        );
    }

    private String buildZoomExpression(CameraMovement movement, int totalFrames) {
        return switch (movement) {
            case ZOOM_IN  -> String.format("1.0+(0.3*(on/%d.0))", totalFrames);
            case ZOOM_OUT -> String.format("1.3-(0.3*(on/%d.0))", totalFrames);
            case PAN_LEFT, PAN_RIGHT, PAN_UP, PAN_DOWN -> "1.15"; // Fixed zoom to avoid black borders during pan
            default       -> "1.0";
        };
    }

    private String buildXExpression(CameraMovement movement, int totalFrames) {
        return switch (movement) {
            case PAN_LEFT  -> String.format("(iw-iw/zoom)*(1-(on/%d.0))", totalFrames); // Start right, move left
            case PAN_RIGHT -> String.format("(iw-iw/zoom)*(on/%d.0)", totalFrames);     // Start left, move right
            default        -> "iw/2-(iw/zoom/2)"; // Center X
        };
    }

    private String buildYExpression(CameraMovement movement, int totalFrames) {
        return switch (movement) {
            case PAN_UP   -> String.format("(ih-ih/zoom)*(1-(on/%d.0))", totalFrames); // Start bottom, move up
            case PAN_DOWN -> String.format("(ih-ih/zoom)*(on/%d.0)", totalFrames);     // Start top, move down
            default       -> "ih/2-(ih/zoom/2)"; // Center Y
        };
    }

    private String buildXfadeChain(List<Scene> scenes, double[] audioDurations, double[] transitionDurations ,double[] pauseDurations) {
        int count = scenes.size();
        if (count == 1) return "[v0]copy[vout];\n";

        StringBuilder sb = new StringBuilder();
        double boundary = 0;

        for (int i = 0; i < count - 1; i++) {
            boundary += audioDurations[i] + pauseDurations[i];
            double offset = boundary - transitionDurations[i];

            String inputA     = (i == 0) ? "[v0]" : "[xf" + (i - 1) + "]";
            String inputB     = "[v" + (i + 1) + "]";
            String outputLabel = (i == count - 2) ? "[vout]" : "[xf" + i + "]";

            double td = transitionDurations[i];
            String transition = (td == 0.0) ? "fade" : toXfadeName(scenes.get(i + 1).getTransitionEffect());

            sb.append(String.format(
                    "%s%sxfade=transition=%s:duration=%.3f:offset=%.3f%s;%n",
                    inputA, inputB, transition, td, offset, outputLabel
            ));
        }

        return sb.toString();
    }

    private String buildAudioConcat(int sceneCount) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sceneCount; i++) sb.append("[a").append(i).append("]");
        sb.append(String.format("concat=n=%d:v=0:a=1[aout];%n", sceneCount));
        return sb.toString();
    }

    private String toXfadeName(TransitionEffect effect) {
        return switch (effect) {
            case FADE        -> "fade";
            case WIPE_LEFT   -> "wipeleft";
            case WIPE_RIGHT  -> "wiperight";
            case SLIDE_LEFT  -> "slideleft";
            case SLIDE_RIGHT -> "slideright";
            case DISSOLVE    -> "dissolve";
            case PIXELIZE    -> "pixelize";
            case RADIAL      -> "radial";
            case NONE        -> "fade";
        };
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private void validateAssets(List<Scene> scenes, String workingDir) {
        for (Scene scene : scenes) {
            int n = scene.getSceneNumber();
            String sceneNum = formatSceneNumber(n);
            Path img   = Paths.get(workingDir, "scene_" + sceneNum + ".png");
            Path audio = Paths.get(workingDir, n + ".wav");
            if (!Files.exists(img))   throw new IllegalStateException("Missing image: " + img);
            if (!Files.exists(audio)) throw new IllegalStateException("Missing audio: " + audio);
        }
    }

    private void runProcess(List<String> command, String workingDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(Paths.get(workingDir).toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (var reader = process.inputReader()) {
            reader.lines().forEach(line -> {
                log.debug("[ffmpeg] {}", line);
                output.append(line).append(System.lineSeparator());
            });
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            // ffmpeg's actual error is usually in the last ~30 lines
            String tail = lastLines(output.toString(), 30);
            throw new RuntimeException("FFmpeg exited with code " + exitCode + "\n--- ffmpeg output (tail) ---\n" + tail);
        }
    }

    private String lastLines(String text, int n) {
        String[] lines = text.split(System.lineSeparator());
        int start = Math.max(0, lines.length - n);
        return String.join(System.lineSeparator(), java.util.Arrays.copyOfRange(lines, start, lines.length));
    }

    private double estimateTotalDuration(double[] audioDurations, double[] transitionDurations) {
        double total = 0;
        for (double d : audioDurations) total += d;
        for (double t : transitionDurations) total -= t;
        return total;
    }

    private String formatDurations(double[] durations) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < durations.length; i++) {
            sb.append(String.format("%.2fs", durations[i]));
            if (i < durations.length - 1) sb.append(", ");
        }
        return sb.append("]").toString();
    }

    private String sanitizeTitle(String title) {
        if (title == null || title.isBlank()) return "output";
        return title.replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase();
    }
}