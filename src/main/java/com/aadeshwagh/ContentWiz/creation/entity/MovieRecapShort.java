package com.aadeshwagh.ContentWiz.creation.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single short-form clip candidate carved out of an existing MovieRecapScript — a
 * contiguous, inclusive range of that script's sceneNo values, plus a punchy title for
 * the short itself.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovieRecapShort {
    private String title;
    private int startScene;
    private int endScene;
}