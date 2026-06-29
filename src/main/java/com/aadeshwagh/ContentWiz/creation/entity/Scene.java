package com.aadeshwagh.ContentWiz.creation.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Scene {
    private int sceneNumber;
    private String narration;
    private String mood;
    private String imagePrompt;
    private CameraMovement cameraMovement = CameraMovement.STATIC;
    private TransitionEffect transitionEffect = TransitionEffect.FADE;

}