package com.aadeshwagh.ContentWiz.creation.script;

import com.aadeshwagh.ContentWiz.creation.entity.Script;
import com.aadeshwagh.ContentWiz.creation.entity.Shorts;

import java.util.List;

public interface ScriptGenerator {
      void generateScript(String userInput,String scriptPromptPath ,String outputDir);

      List<Shorts> generateShorts(Script script);
}
