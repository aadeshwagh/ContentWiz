package com.aadeshwagh.ContentWiz.creation.script;

import com.aadeshwagh.ContentWiz.creation.entity.Script;
import com.aadeshwagh.ContentWiz.creation.entity.Shorts;

import java.util.List;

public interface ScriptGenerator {

    void generateScript(String storyOrPrompt, String scriptPromptPath, String outputDir, String name);

    List<Shorts> generateShorts(Script script);
}
