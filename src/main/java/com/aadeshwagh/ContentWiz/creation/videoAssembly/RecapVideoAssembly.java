package com.aadeshwagh.ContentWiz.creation.videoAssembly;

import com.aadeshwagh.ContentWiz.creation.entity.MovieRecapScene;
import com.aadeshwagh.ContentWiz.creation.entity.MovieRecapScript;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RecapVideoAssembly {

    /**
     * Maximum length, in seconds, of each intermediate cut segment when frame-accurately
     * re-encoding a range out of the source movie. This is purely a technical chunking detail
     * for accurate cuts and is unrelated to SAMPLE_KEEP_SECONDS below.
     */
    private static final double SEGMENT_DURATION_SECONDS = 5.0;

    private static final long FFMPEG_TIMEOUT_MINUTES = 10;

    /**
     * Below this many seconds of remaining gap, no slowdown/trimming correction is applied —
     * treated as already in sync.
     */
    private static final double SYNC_TOLERANCE_SECONDS = 0.05;

    // --- Encode quality --------------------------------------------------------------------
    // Every stage that re-encodes video (segment cuts, crossfade merges, final mux) uses these.
    // "slow" preset was the big cost — several times slower than "veryfast" at the same CRF —
    // so it's back to "veryfast" (same speed as the original pipeline). CRF 19 is still clearly
    // better than the unspecified default (23) with barely any visible difference from CRF 16,
    // but encodes noticeably faster. Go to CRF ~16-17 only if you have render time to spare.
    private static final String VIDEO_CRF = "19";
    private static final String VIDEO_PRESET = "veryfast";
    private static final String AUDIO_BITRATE = "192k";

    // --- Scene sampling: short cuts + small crossfades instead of one long take --------------

    /** Seconds of footage kept per sample. */
    private static final double SAMPLE_KEEP_SECONDS = 5.0;

    /** Seconds skipped between consecutive samples. */
    private static final double SAMPLE_SKIP_SECONDS = 2.0;

    /** Length of the crossfade used to join consecutive sampled pieces. */
    private static final double TRANSITION_DURATION_SECONDS = 0.4;

    /** Don't bother cutting a sample shorter than this — not worth the extra jump cut. */
    private static final double MIN_SAMPLE_SECONDS = 1.0;

    /**
     * Assembles the final recap video.
     * <p>
     * For every scene in the script (in order):
     * <ul>
     *     <li>If the scene's narration is as long as (or longer than) the [clipStartTime,
     *     clipEndTime] range, the full range is cut from the source movie (frame-accurate,
     *     no speed change).</li>
     *     <li>If the narration is shorter than the clip range, the clip is NOT sped up and is
     *     NOT shown as one continuous take. Instead the range is sampled: keep
     *     {@code SAMPLE_KEEP_SECONDS} of footage, skip {@code SAMPLE_SKIP_SECONDS}, keep the
     *     next {@code SAMPLE_KEEP_SECONDS}, and so on, until enough footage has been gathered
     *     to (roughly) cover the narration or the range runs out. Consecutive kept pieces are
     *     joined with a short crossfade rather than a hard jump cut.</li>
     *     <li>Whichever branch ran, the resulting visuals are then measured against the
     *     narration's actual duration and corrected to match it exactly: if the visuals are
     *     shorter than the narration, the clip's own playback is slowed down (via
     *     {@code setpts}) just enough to stretch it to the full narration length; if longer,
     *     it's trimmed.</li>
     *     <li>Every cut is framed to 16:9 by padding (letterbox/pillarbox) to a canvas derived
     *     from the source movie's own resolution — never by scaling/downscaling — so no
     *     original quality is lost to resampling.</li>
     * </ul>
     * In all cases the clip's original audio is muted and replaced with the scene's narration
     * audio file (named "{sceneNumber}.mp3/.wav/.m4a" in workingDir), untouched and unpadded.
     * Once every scene has been processed this way, all per-scene clips are concatenated in
     * order into the final output video.
     *
     * @param videoPath  path to the full source movie mp4 file
     * @param script     the recap script containing ordered scenes with clipStartTime/clipEndTime
     * @param outputDir  directory the final stitched recap video will be written to
     * @param workingDir directory containing the narration audio files (1.mp3, 2.mp3, ... one
     *                   per scene, matching scene order) and used to store intermediate files
     * @return the final assembled recap video file
     */
    public File assemble(String videoPath, MovieRecapScript script, String outputDir, String workingDir) {
        try {
            File tmpDir = new File(workingDir, "recap_assembly_tmp");
            if (!tmpDir.exists() && !tmpDir.mkdirs()) {
                throw new IOException("Failed to create temp working directory: " + tmpDir.getAbsolutePath());
            }

            int[] sourceResolution = probeResolution(videoPath);
            int[] canvas = compute16x9Canvas(sourceResolution[0], sourceResolution[1]);
            String padFilter = build16x9PadFilter(canvas[0], canvas[1]);

            log.info("Source resolution {}x{} -> 16:9 canvas {}x{} (padding only, no downscale)",
                    sourceResolution[0], sourceResolution[1], canvas[0], canvas[1]);

            List<MovieRecapScene> scenes = script.getScenes();
            List<File> sceneFinalClips = new ArrayList<>();

            for (int i = 0; i < scenes.size(); i++) {
                int sceneNumber = i + 1;
                MovieRecapScene scene = scenes.get(i);

                double startSeconds = timestampToSeconds(scene.getClipStartTime());
                double endSeconds = timestampToSeconds(scene.getClipEndTime());
                double totalDuration = endSeconds - startSeconds;

                if (totalDuration <= 0) {
                    throw new IllegalArgumentException("Scene " + sceneNumber
                            + " has a non-positive duration: start=" + scene.getClipStartTime()
                            + " end=" + scene.getClipEndTime());
                }

                File narrationFile = resolveNarrationFile(workingDir, sceneNumber);
                double narrationDuration = getMediaDuration(narrationFile);

                log.info("Processing scene {}/{}  [{} -> {}]  clipDuration={}s narrationDuration={}s",
                        sceneNumber, scenes.size(), scene.getClipStartTime(), scene.getClipEndTime(),
                        String.format("%.2f", totalDuration), String.format("%.2f", narrationDuration));

                File sceneRawClip;
                if (narrationDuration + SYNC_TOLERANCE_SECONDS >= totalDuration) {
                    // Narration covers the whole clip (or more) — use the full range.
                    sceneRawClip = cutAndMergeScene(videoPath, tmpDir, "scene_" + sceneNumber,
                            startSeconds, totalDuration, padFilter);
                } else {
                    // Narration is shorter than the clip — sample short pieces across the
                    // range instead of showing one continuous take.
                    sceneRawClip = cutSampledScene(videoPath, tmpDir, sceneNumber,
                            startSeconds, endSeconds, narrationDuration, padFilter);
                }

                // Whatever the branch above produced, make the final visuals match the
                // narration length exactly. If the visuals are shorter than the narration,
                // slow the clip's own playback down (via setpts) just enough to stretch it to
                // cover the full narration — no frozen last frame. If longer, trim it.
                double rawDuration = getMediaDuration(sceneRawClip);
                double slowdownFactor = 0;
                double trimDuration = 0;
                if (rawDuration + SYNC_TOLERANCE_SECONDS < narrationDuration) {
                    slowdownFactor = narrationDuration / rawDuration;
                } else if (rawDuration > narrationDuration + SYNC_TOLERANCE_SECONDS) {
                    trimDuration = narrationDuration;
                }

                File sceneFinalClip = new File(tmpDir, "scene_" + sceneNumber + "_final.mp4");
                muteAndAddNarration(sceneRawClip, narrationFile, sceneFinalClip, slowdownFactor, trimDuration);

                sceneFinalClips.add(sceneFinalClip);
            }

            File outputDirFile = new File(outputDir);
            if (!outputDirFile.exists() && !outputDirFile.mkdirs()) {
                throw new IOException("Failed to create output directory: " + outputDirFile.getAbsolutePath());
            }

            File finalOutput = new File(outputDirFile, "recap-final.mp4");
            concatClips(sceneFinalClips, tmpDir, finalOutput);

            log.info("Recap video assembly completed: {}", finalOutput.getAbsolutePath());
            return finalOutput;

        } catch (Exception e) {
            throw new RuntimeException("Failed to assemble recap video", e);
        }
    }

    /**
     * Reads the source video's native width/height via ffprobe, so the 16:9 canvas can be
     * derived from it — letterbox/pillarbox padding only, never a downscale — to preserve
     * original quality.
     */
    private int[] probeResolution(String videoPath) throws IOException, InterruptedException {
        List<String> cmd = List.of(
                "ffprobe", "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=width,height",
                "-of", "csv=s=x:p=0",
                videoPath
        );

        ProcessBuilder processBuilder = new ProcessBuilder(cmd);
        Process process = processBuilder.start();

        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            output = reader.readLine();
        }

        boolean finished = process.waitFor(1, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("ffprobe timed out reading resolution for " + videoPath);
        }
        if (process.exitValue() != 0 || output == null || output.isBlank()) {
            throw new RuntimeException("ffprobe failed to read resolution for " + videoPath);
        }

        String[] parts = output.trim().split("x");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }

    /**
     * Derives a 16:9 canvas from the source resolution by padding only — never scaling — so no
     * quality is lost to resampling. If the source is wider than 16:9 (e.g. 2.35:1 cinematic),
     * the original width is kept and black bars are added top/bottom. If narrower, the original
     * height is kept and bars are added left/right. Dimensions are rounded up to even numbers,
     * as required for yuv420p encoding.
     */
    private int[] compute16x9Canvas(int sourceWidth, int sourceHeight) {
        double sourceRatio = (double) sourceWidth / sourceHeight;
        double targetRatio = 16.0 / 9.0;

        int width;
        int height;
        if (sourceRatio >= targetRatio) {
            width = sourceWidth;
            height = (int) Math.round(sourceWidth / targetRatio);
        } else {
            height = sourceHeight;
            width = (int) Math.round(sourceHeight * targetRatio);
        }

        if (width % 2 != 0) {
            width++;
        }
        if (height % 2 != 0) {
            height++;
        }

        return new int[]{width, height};
    }

    private String build16x9PadFilter(int targetWidth, int targetHeight) {
        return "pad=" + targetWidth + ":" + targetHeight + ":(ow-iw)/2:(oh-ih)/2:color=black,setsar=1";
    }

    /**
     * Builds a scene clip when the narration is shorter than the [startSeconds, endSeconds]
     * range. Rather than one continuous stretch of footage, the range is sampled in a
     * repeating pattern — keep {@code SAMPLE_KEEP_SECONDS}, skip {@code SAMPLE_SKIP_SECONDS},
     * repeat — until enough footage has been gathered (or the range runs out). Consecutive
     * kept pieces are joined with a short crossfade instead of a hard jump cut. The caller
     * trims/stretches the result to match the narration length exactly afterward, so this
     * method doesn't need to be frame-perfect about the total.
     */
    private File cutSampledScene(String videoPath, File tmpDir, int sceneNumber,
                                 double startSeconds, double endSeconds, double narrationDuration,
                                 String padFilter)
            throws IOException, InterruptedException {

        List<File> keptSegments = new ArrayList<>();
        double cursor = startSeconds;
        double keptTotal = 0;
        int sampleIndex = 0;

        while (keptTotal < narrationDuration - SYNC_TOLERANCE_SECONDS && cursor < endSeconds - 0.001) {
            double remainingNeeded = narrationDuration - keptTotal;
            double availableInClip = endSeconds - cursor;
            double keepDuration = Math.min(Math.min(SAMPLE_KEEP_SECONDS, remainingNeeded), availableInClip);

            if (keepDuration < MIN_SAMPLE_SECONDS) {
                break;
            }

            File segment = cutAndMergeScene(videoPath, tmpDir,
                    "scene_" + sceneNumber + "_smp_" + sampleIndex, cursor, keepDuration, padFilter);
            keptSegments.add(segment);
            keptTotal += keepDuration;

            cursor += keepDuration + SAMPLE_SKIP_SECONDS;
            sampleIndex++;
        }

        if (keptSegments.isEmpty()) {
            // Range too short to sample meaningfully — fall back to a single straight cut.
            keptSegments.add(cutAndMergeScene(videoPath, tmpDir, "scene_" + sceneNumber + "_smp_0",
                    startSeconds, Math.min(narrationDuration, endSeconds - startSeconds), padFilter));
        }

        log.info("Scene {} sampled into {} piece(s) covering ~{}s of footage for {}s of narration",
                sceneNumber, keptSegments.size(), String.format("%.2f", keptTotal),
                String.format("%.2f", narrationDuration));

        File sceneSampledClip = new File(tmpDir, "scene_" + sceneNumber + "_sampled.mp4");

        if (keptSegments.size() == 1) {
            Files.copy(keptSegments.get(0).toPath(), sceneSampledClip.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } else {
            concatWithCrossfade(keptSegments, sceneSampledClip, TRANSITION_DURATION_SECONDS);
        }

        return sceneSampledClip;
    }

    /**
     * Joins multiple clips into one continuous clip, replacing hard cuts between consecutive
     * pieces with a short crossfade. Video-only: each input's own audio is discarded here since
     * the scene's narration is muxed in separately afterward.
     */
    private void concatWithCrossfade(List<File> clips, File outputFile, double transitionDuration)
            throws IOException, InterruptedException {

        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-y");
        for (File clip : clips) {
            cmd.add("-i");
            cmd.add(clip.getAbsolutePath());
        }

        StringBuilder filter = new StringBuilder();
        String previousLabel = "0:v";
        double cumulativeOffset = getMediaDuration(clips.get(0)) - transitionDuration;

        for (int i = 1; i < clips.size(); i++) {
            String outputLabel = (i == clips.size() - 1) ? "outv" : "vx" + i;

            filter.append("[").append(previousLabel).append("][").append(i).append(":v]")
                    .append("xfade=transition=fade:duration=").append(transitionDuration)
                    .append(":offset=").append(String.format("%.3f", cumulativeOffset))
                    .append("[").append(outputLabel).append("];");

            if (i < clips.size() - 1) {
                cumulativeOffset += getMediaDuration(clips.get(i)) - transitionDuration;
            }
            previousLabel = outputLabel;
        }

        filter.setLength(filter.length() - 1); // drop trailing semicolon

        cmd.add("-filter_complex");
        cmd.add(filter.toString());
        cmd.add("-map");
        cmd.add("[outv]");
        cmd.add("-c:v");
        cmd.add("libx264");
        cmd.add("-preset");
        cmd.add(VIDEO_PRESET);
        cmd.add("-crf");
        cmd.add(VIDEO_CRF);
        cmd.add(outputFile.getAbsolutePath());

        runProcess(cmd);
    }

    /**
     * Cuts [startSeconds, startSeconds + totalDuration] out of the source video in segments of
     * up to SEGMENT_DURATION_SECONDS each, re-encoding each segment for frame-accurate cuts
     * (padding each to the shared 16:9 canvas in the process — no scaling, so no quality loss),
     * then concatenates the segments back into a single continuous clip.
     *
     * @param clipLabel unique label used to namespace intermediate files for this clip piece
     *                  (e.g. "scene_3", "scene_3_smp_0")
     * @param padFilter the ffmpeg pad filter string bringing this segment to the shared 16:9
     *                  canvas, as produced by {@link #build16x9PadFilter}
     */
    private File cutAndMergeScene(String videoPath, File tmpDir, String clipLabel,
                                  double startSeconds, double totalDuration, String padFilter)
            throws IOException, InterruptedException {

        List<File> segmentFiles = new ArrayList<>();
        double remaining = totalDuration;
        double segmentStart = startSeconds;
        int segmentIndex = 0;

        while (remaining > 0.001) {
            double segmentDuration = Math.min(SEGMENT_DURATION_SECONDS, remaining);
            File segmentFile = new File(tmpDir, clipLabel + "_part_" + segmentIndex + ".mp4");

            List<String> cmd = List.of(
                    "ffmpeg", "-y",
                    "-ss", String.valueOf(segmentStart),
                    "-i", videoPath,
                    "-t", String.valueOf(segmentDuration),
                    "-vf", padFilter,
                    "-c:v", "libx264",
                    "-preset", VIDEO_PRESET,
                    "-crf", VIDEO_CRF,
                    "-c:a", "aac",
                    "-avoid_negative_ts", "make_zero",
                    segmentFile.getAbsolutePath()
            );

            runProcess(cmd);
            segmentFiles.add(segmentFile);

            segmentStart += segmentDuration;
            remaining -= segmentDuration;
            segmentIndex++;
        }

        File rawClip = new File(tmpDir, clipLabel + "_raw.mp4");

        if (segmentFiles.size() == 1) {
            Files.copy(segmentFiles.get(0).toPath(), rawClip.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } else {
            concatClips(segmentFiles, tmpDir, rawClip);
        }

        return rawClip;
    }

    /**
     * Mutes the original audio track of the given video clip and replaces it with the scene's
     * narration audio track. The narration audio is never modified (no speed/volume changes).
     * <ul>
     *     <li>If the video is shorter than the narration, {@code slowdownFactor} (> 1.0) slows
     *     the clip's own playback down by that factor (via ffmpeg's {@code setpts}) so it
     *     stretches to exactly cover the full narration length, instead of freezing on the
     *     last frame.</li>
     *     <li>If the video is longer than the narration, {@code trimDuration} (equal to the
     *     narration's length) caps the output so the excess video is cut off.</li>
     * </ul>
     * Pass 0 for whichever doesn't apply — at most one of the two should be non-zero.
     */
    private void muteAndAddNarration(File videoClip, File narrationFile, File outputFile,
                                     double slowdownFactor, double trimDuration)
            throws IOException, InterruptedException {

        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(videoClip.getAbsolutePath());
        cmd.add("-i");
        cmd.add(narrationFile.getAbsolutePath());
        cmd.add("-map");
        cmd.add("0:v:0");
        cmd.add("-map");
        cmd.add("1:a:0");

        if (slowdownFactor > 1.0 + 1e-6) {
            // Stretch this clip's own playback speed so its duration matches the narration
            // exactly, rather than freezing on the last frame.
            cmd.add("-vf");
            cmd.add("setpts=" + String.format("%.6f", slowdownFactor) + "*PTS");
        }

        if (trimDuration > SYNC_TOLERANCE_SECONDS) {
            // Cap output length so excess video (beyond the narration) is trimmed off.
            cmd.add("-t");
            cmd.add(String.valueOf(trimDuration));
        }

        cmd.add("-c:v");
        cmd.add("libx264");
        cmd.add("-preset");
        cmd.add(VIDEO_PRESET);
        cmd.add("-crf");
        cmd.add(VIDEO_CRF);
        cmd.add("-c:a");
        cmd.add("aac");
        cmd.add("-b:a");
        cmd.add(AUDIO_BITRATE);
        cmd.add(outputFile.getAbsolutePath());

        runProcess(cmd);
    }

    /**
     * Reads the duration in seconds of a media file via ffprobe.
     */
    private double getMediaDuration(File mediaFile) throws IOException, InterruptedException {
        List<String> cmd = List.of(
                "ffprobe", "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                mediaFile.getAbsolutePath()
        );

        ProcessBuilder processBuilder = new ProcessBuilder(cmd);
        Process process = processBuilder.start();

        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            output = reader.readLine();
        }

        boolean finished = process.waitFor(1, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("ffprobe timed out reading duration for " + mediaFile.getAbsolutePath());
        }
        if (process.exitValue() != 0 || output == null || output.isBlank()) {
            throw new RuntimeException("ffprobe failed to read duration for " + mediaFile.getAbsolutePath());
        }

        return Double.parseDouble(output.trim());
    }

    /**
     * Concatenates a list of mp4 clips (expected to share matching codec parameters) into a
     * single file using ffmpeg's concat demuxer with stream copy — no re-encode, so no quality
     * loss at this step.
     */
    private void concatClips(List<File> clips, File tmpDir, File outputFile) throws IOException, InterruptedException {

        File concatListFile = new File(tmpDir, "concat_" + outputFile.getName() + ".txt");
        try (BufferedWriter writer = Files.newBufferedWriter(concatListFile.toPath())) {
            for (File clip : clips) {
                writer.write("file '" + clip.getAbsolutePath().replace("'", "'\\''") + "'");
                writer.newLine();
            }
        }

        List<String> cmd = List.of(
                "ffmpeg", "-y",
                "-f", "concat",
                "-safe", "0",
                "-i", concatListFile.getAbsolutePath(),
                "-c", "copy",
                outputFile.getAbsolutePath()
        );

        runProcess(cmd);
    }

    /**
     * Looks up the narration audio file for a given scene number in workingDir, trying a few
     * common extensions.
     */
    private File resolveNarrationFile(String workingDir, int sceneNumber) {
        String[] extensions = {"mp3", "wav", "m4a"};
        for (String ext : extensions) {
            File candidate = new File(workingDir, sceneNumber + "." + ext);
            if (candidate.exists()) {
                return candidate;
            }
        }
        throw new RuntimeException("No narration audio file found for scene " + sceneNumber
                + " in " + workingDir + " (expected " + sceneNumber + ".mp3, .wav, or .m4a)");
    }

    /**
     * Converts an SRT-style timestamp (HH:MM:SS,mmm) into total seconds.
     */
    private double timestampToSeconds(String timestamp) {
        String normalized = timestamp.replace(',', '.');
        String[] parts = normalized.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid timestamp format: " + timestamp);
        }
        double hours = Double.parseDouble(parts[0]);
        double minutes = Double.parseDouble(parts[1]);
        double seconds = Double.parseDouble(parts[2]);
        return hours * 3600 + minutes * 60 + seconds;
    }

    private void runProcess(List<String> command) throws IOException, InterruptedException {
        log.debug("Running ffmpeg command: {}", String.join(" ", command));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug(line);
            }
        }

        boolean finished = process.waitFor(FFMPEG_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("ffmpeg command timed out after " + FFMPEG_TIMEOUT_MINUTES
                    + " minutes: " + String.join(" ", command));
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("ffmpeg command failed with exit code " + exitCode
                    + ": " + String.join(" ", command));
        }
    }
}