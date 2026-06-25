package com.aadeshwagh.ContentWiz.util.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
@Getter
@Setter
public class VideoInfo {
    String type;
    String title;
    String description;
    List<String> tags;
    String videoUrl;
    String coverImgPath;
    String videoPath;
    String caption;
    boolean isUploaded;

}
