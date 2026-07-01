package com.aadeshwagh.ContentWiz.creation.script;

import com.aadeshwagh.ContentWiz.creation.entity.Script;
import com.aadeshwagh.ContentWiz.creation.entity.Shorts;
import com.aadeshwagh.ContentWiz.util.entity.MetaData;
import com.aadeshwagh.ContentWiz.util.entity.VideoInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.List;

@Component
public class MetaDataService {

    private static final Logger log = LoggerFactory.getLogger(MetaDataService.class);
    @Value("${content.root-directory}")
    private String contentRoot;

    @Autowired
    private ObjectMapper objectMapper;

    public void addMetaDataForLongScript(Script script, String type){
        MetaData metaData = loadLongMetaData(type);
        VideoInfo longVideoInfo = new VideoInfo();
        longVideoInfo.setType(type);
        longVideoInfo.setTitle(script.getTitle());
        longVideoInfo.setDescription(script.getDescription());
        longVideoInfo.setVideoPath(contentRoot+"/"+type+"/long/"+sanitizeTitle(script.getTitle())+".mp4");
        longVideoInfo.setTags(List.of("stick animation", "stick figure animation", "stickman story", "productive animation", "motivational animation", "daily routine animation", "life transformation animation", "self improvement story"));
        longVideoInfo.setUploaded(false);

        List<VideoInfo> videoInfos = metaData.getInfos();
        videoInfos.add(longVideoInfo);
        metaData.setInfos(videoInfos);

        saveMetaData(metaData,contentRoot+"/"+type+"/long/metadata.json");


    }

    private String sanitizeTitle(String title) {
        if (title == null || title.isBlank()) return "output";
        return title.replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase();
    }

    public void addMetaDataForShortScript( List<Shorts> shorts, String type){
        MetaData metaData = loadShortMetaData(type);

        for(Shorts reel : shorts){
            VideoInfo shortVideoInfo = new VideoInfo();
            shortVideoInfo.setType(type);
            shortVideoInfo.setTitle(reel.getTitle());
            shortVideoInfo.setDescription(reel.getDescription());
            shortVideoInfo.setVideoPath(contentRoot+"/"+type+"/short/"+sanitizeTitle(reel.getTitle())+".mp4");
            shortVideoInfo.setTags(List.of("stick animation", "stick figure animation", "stickman story", "productive animation", "motivational animation", "daily routine animation", "life transformation animation", "self improvement story"));
            shortVideoInfo.setUploaded(false);
            shortVideoInfo.setCaption(reel.getTitle()+"          #stickanimation #productivity #selfimprovement #discipline #motivationstory #dailyroutine #habitbuilding #growthmindset #timemanagement #focus #successmindset #animatedstories");
            shortVideoInfo.setVideoUrl("https://accurate-tragedy-pancreas.ngrok-free.dev/"+type+"/short/"+ sanitizeTitle(reel.getTitle())+".mp4");
            List<VideoInfo> videoInfos = metaData.getInfos();
            videoInfos.add(shortVideoInfo);
            metaData.setInfos(videoInfos);

        }

        saveMetaData(metaData,contentRoot+"/"+type+"/short/metadata.json");



    }

    private MetaData loadLongMetaData(String type){
        File file = new File(contentRoot+"/"+type+"/long/metadata.json");
        if(!file.exists()){
             return new MetaData();
        }
        return objectMapper.readValue(new File(contentRoot+"/"+type+"/long/metadata.json"),MetaData.class);
    }

    private MetaData loadShortMetaData(String type){
        File file = new File(contentRoot+"/"+type+"/short/metadata.json");
        if(!file.exists()){
            return new MetaData();
        }
        return objectMapper.readValue(new File(contentRoot+"/"+type+"/short/metadata.json"),MetaData.class);
    }

    private void saveMetaData(MetaData metaData, String path){
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(new File(path), metaData);
        log.info("metadata saved successfully");
    }
}
