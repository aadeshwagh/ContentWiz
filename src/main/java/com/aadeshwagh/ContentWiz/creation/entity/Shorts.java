package com.aadeshwagh.ContentWiz.creation.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Shorts {

    private int startScene;
    private int endScene;
    private String title;
    private String description;

    public Shorts() {
    }

    public Shorts(int startScene, int endScene, String title) {
        this.startScene = startScene;
        this.endScene = endScene;
        this.title = title;
    }


}