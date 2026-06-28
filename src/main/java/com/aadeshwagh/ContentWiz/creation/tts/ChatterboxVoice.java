package com.aadeshwagh.ContentWiz.creation.tts;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatterboxVoice {

    AARON     ("Aaron"),
    ABIGAIL   ("Abigail"),
    ANAYA     ("Anaya"),
    ANDY      ("Andy"),
    ARCHER    ("Archer"),
    BRIAN     ("Brian"),
    CHLOE     ("Chloe"),
    DYLAN     ("Dylan"),
    EMMANUEL  ("Emmanuel"),
    ETHAN     ("Ethan"),
    EVELYN    ("Evelyn"),
    GAVIN     ("Gavin"),
    GORDON    ("Gordon"),
    IVAN      ("Ivan"),
    LAURA     ("Laura"),
    LUCY      ("Lucy"),
    MADISON   ("Madison"),
    MARISOL   ("Marisol"),
    MEERA     ("Meera"),
    WALTER    ("Walter");

    /** Exact name as it appears on disk (without .wav extension). */
    private final String voiceName;

    /** Returns the filename as passed to the Python --voice flag. */
    public String fileName() {
        return voiceName + ".wav";
    }

    /**
     * Parse a string to a ChatterboxVoice, falling back to LUCY if unrecognised.
     */
    public static ChatterboxVoice fromString(String name) {
        if (name == null || name.isBlank()) return LUCY;
        try {
            return valueOf(name.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return LUCY;
        }
    }
}