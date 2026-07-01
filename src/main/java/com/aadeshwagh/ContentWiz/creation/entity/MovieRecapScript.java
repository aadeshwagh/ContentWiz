package com.aadeshwagh.ContentWiz.creation.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MovieRecapScript {
    String title;
    String description;
    List<MovieRecapScene> scenes;
}
