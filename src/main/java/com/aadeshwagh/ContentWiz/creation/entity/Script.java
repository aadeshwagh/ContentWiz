package com.aadeshwagh.ContentWiz.creation.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Script {
    private String title;
    private String musicPrompt;
    private List<Scene> scenes;

}
