package com.aadeshwagh.ContentWiz.creation.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Scene {
    private int sceneNumber;
    private int duration;
    private String narration;
    private String mood;
    private String imagePrompt;
}
