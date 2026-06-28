package com.aadeshwagh.ContentWiz;

import com.aadeshwagh.ContentWiz.creation.ScriptGeneratorGemini;
import com.aadeshwagh.ContentWiz.creation.entity.Script;
import com.aadeshwagh.ContentWiz.creation.imageGen.ImageGenService;
import com.aadeshwagh.ContentWiz.creation.tts.ChatterboxTtsService;
import com.aadeshwagh.ContentWiz.creation.tts.ChatterboxVoice;
import com.aadeshwagh.ContentWiz.creation.tts.Emotion;
import com.aadeshwagh.ContentWiz.util.FileServer;
import com.aadeshwagh.ContentWiz.upload.UploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import tools.jackson.databind.ObjectMapper;

import java.io.File;

@SpringBootApplication
public class ContentWizApplication implements CommandLineRunner {

	@Autowired
	FileServer fileServer;

	@Autowired
	UploadService uploadService;

	@Autowired
	ScriptGeneratorGemini scriptGenerator;

	@Autowired
	ChatterboxTtsService chatterboxTtsService;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	ImageGenService imageGenService;


	public static void main(String[] args) {

		SpringApplication.run(ContentWizApplication.class, args);

	}

	@Override
	public void run(String... args) throws Exception {
//		fileServer.getPublicBaseUrl();
//		uploadService.publishAllTypes();
//		System.out.println(scriptGenerator.generateScript("A poor fisherman finds a mysterious glowing box in the sea. Every night it whispers his name."));
//		Script script = objectMapper.readValue(new File("src/main/java/com/aadeshwagh/ContentWiz/creation/tts/short-sample.json"),Script.class);
//		chatterboxTtsService.generateForScript(script, ChatterboxVoice.WALTER, "src/main/java/com/aadeshwagh/ContentWiz/creation/tts");
//		imageGenService.generateImages(script,"a simple outline sketch black and white nothing too complex","src/main/java/com/aadeshwagh/ContentWiz/creation/imageGen");

	}



}
