package com.aadeshwagh.ContentWiz.upload;


import com.aadeshwagh.ContentWiz.util.entity.MetaData;
import com.aadeshwagh.ContentWiz.util.entity.VideoInfo;
import com.aadeshwagh.ContentWiz.upload.instagram.InstagramReelUploader;
import com.aadeshwagh.ContentWiz.upload.youtube.YouTubeVideoUploader;
import com.aadeshwagh.ContentWiz.util.ContentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

@Component
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);
    private final InstagramReelUploader instagramReelUploader;
    private final YouTubeVideoUploader youTubeVideoUploader;
    private final ObjectMapper objectMapper;
    private final String contentRoot;
    private final ContentProperties contentProperties;

    @Autowired
    UploadService(InstagramReelUploader instagramReelUploader, YouTubeVideoUploader youTubeVideoUploader, ObjectMapper objectMapper, @Value("${content.root-directory}") String contentRoot, ContentProperties contentProperties){
        this.instagramReelUploader = instagramReelUploader;
        this.youTubeVideoUploader = youTubeVideoUploader;
        this.objectMapper = objectMapper;
        this.contentRoot = contentRoot;
        this.contentProperties = contentProperties;
    }


    public void publishAllTypes(){
        for(String type : contentProperties.getTypes().keySet()){
            publishVideosForType(type);
        }
    }

    public void publishVideosForType(String type) {
        Map.Entry<MetaData, MetaData> vid = loadMetaDataForType(type);
        MetaData shortV = vid.getKey();
        MetaData longV = vid.getValue();

        for(VideoInfo videoInfo : shortV.getInfos()){
            publishShortVideo(videoInfo);
        }
        for(VideoInfo videoInfo : longV.getInfos()){
            publishLongVideo(videoInfo);
        }

    }

    public void publishShortVideo(VideoInfo videoInfo){
        //do something with return values update the metadata file
        String instaStatus  =uploadToInstagramChannel(videoInfo.getType(), videoInfo.getVideoUrl(),videoInfo.getCaption());
        log.info(instaStatus);
        String ytStatus = uploadToYoutubeChannel(videoInfo.getType(),videoInfo.getVideoPath(),videoInfo.getTitle(),videoInfo.getDescription(),videoInfo.getTags(),videoInfo.getCoverImgPath());

        log.info(ytStatus);
    }
    public void publishLongVideo(VideoInfo videoInfo){
        uploadToYoutubeChannel(videoInfo.getType(),videoInfo.getVideoPath(),videoInfo.getTitle(),videoInfo.getDescription(),videoInfo.getTags(),videoInfo.getCoverImgPath());
    }
    public Map.Entry<MetaData,MetaData> loadMetaDataForType(String type){
        try {
            MetaData shortVideoMetadata = objectMapper.readValue(new File(contentRoot + "/" + type + "/short/metadata.json"), MetaData.class);
            MetaData longVideoMetadata = objectMapper.readValue(new File(contentRoot + "/" + type + "/long/metadata.json"), MetaData.class);
            return new AbstractMap.SimpleEntry<>(shortVideoMetadata,longVideoMetadata);
        }catch (Exception e){
            throw new RuntimeException(e);
        }

    }

    public String uploadToInstagramChannel(String channelType, String publicVideoUrl, String caption){
        try {
            return  instagramReelUploader.publishReel(channelType, publicVideoUrl, caption);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public String uploadToYoutubeChannel(String type, String videoPath, String title, String description, List<String> tags, String coverImgPath){
        try {
            return youTubeVideoUploader.uploadVideo(type, Path.of(videoPath),Path.of(coverImgPath),title, description,tags);

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


}
