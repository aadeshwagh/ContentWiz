package com.aadeshwagh.ContentWiz.creation.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Emotion {

    // Turbo ignores exaggeration and cfg_weight — only temperature matters.
    // Values kept symmetric with non-Turbo for forward compatibility.
    HAPPY      (0.90f, 0.85f, 0.50f),
    EXCITED    (1.40f, 1.10f, 0.40f),
    CHEERFUL   (1.00f, 0.90f, 0.45f),
    AMUSED     (0.95f, 0.85f, 0.50f),
    SAD        (0.60f, 0.60f, 0.55f),
    ANGRY      (1.50f, 1.10f, 0.35f),
    ANXIOUS    (1.10f, 1.00f, 0.40f),
    MELANCHOLIC(0.70f, 0.55f, 0.60f),
    NEUTRAL    (0.50f, 0.70f, 0.50f),
    CALM       (0.55f, 0.65f, 0.50f),
    SINCERE    (0.40f, 0.50f, 0.65f),
    DRAMATIC   (1.80f, 1.20f, 0.30f),
    WHISPER    (0.40f, 0.50f, 0.65f),
    WONDER     (1.00f, 0.95f, 0.45f),
    CONFUSED   (0.80f, 0.90f, 0.50f),
    SARCASTIC  (1.20f, 1.00f, 0.40f);

    private final float exaggeration;
    private final float temperature;
    private final float cfgWeight;

    /**
     * Parse the mood string coming from Scene.getMood().
     * Falls back to NEUTRAL for any unrecognised value so generation never fails.
     */
    public static Emotion fromMood(String mood) {
        if (mood == null || mood.isBlank()) return NEUTRAL;
        try {
            return valueOf(mood.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return NEUTRAL;
        }
    }
}