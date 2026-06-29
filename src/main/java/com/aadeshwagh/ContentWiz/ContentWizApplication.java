package com.aadeshwagh.ContentWiz;

import com.aadeshwagh.ContentWiz.creation.ScriptGeneratorGemini;
import com.aadeshwagh.ContentWiz.creation.entity.Emotion;
import com.aadeshwagh.ContentWiz.creation.entity.Script;
import com.aadeshwagh.ContentWiz.creation.imageGen.ImageGenService;
import com.aadeshwagh.ContentWiz.creation.tts.ChatterboxTtsService;
import com.aadeshwagh.ContentWiz.creation.entity.ChatterboxVoice;
import com.aadeshwagh.ContentWiz.creation.videoAssembly.VideoAssemblyService;
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

	@Autowired
	VideoAssemblyService videoAssemblyService;


	public static void main(String[] args) {

		SpringApplication.run(ContentWizApplication.class, args);

	}

	@Override
	public void run(String... args) throws Exception {
//		fileServer.getPublicBaseUrl();
//		uploadService.publishAllTypes(); v
//	    scriptGenerator.generateScript("Create an engaging educational storytelling video script on the topic: “Why Do We Wear Suits?”\n" +
//				"\n" +
//				"Explain how suits originated, how they evolved through history, and why they became a symbol of professionalism, power, discipline, and status. Cover the historical origins, cultural evolution, psychological impact, and modern relevance of suits in a way that feels compelling and easy to understand.\n" +
//				"\n" +
//				"The script should feel like a premium YouTube educational video—clear, engaging, and story-driven. Use strong hooks, smooth transitions, and curiosity-driven storytelling. Make complex ideas simple and relatable. Focus on making the viewer think differently about something they see every day.\n" +
//				"\n" +
//				"The narrative should answer questions like:\n" +
//				"- Where did suits come from?\n" +
//				"- Why did powerful people start wearing them?\n" +
//				"- Why do suits influence how others perceive us?\n" +
//				"- Why do suits still matter today?\n" +
//				"\n" +
//				"Keep the tone intelligent, cinematic, and easy to follow.","src/main/java/com/aadeshwagh/ContentWiz/creation/prompts/long-video-script-system-prompt-stick-figure-style.txt","content/infochannel");
		//Script script = objectMapper.readValue(new File("content/infochannel/script.json"),Script.class);
  	    //chatterboxTtsService.generateForScript(script, "src/main/resources/voices/voice_preview_jon - natural authority (agent _ assistant).mp3", "content/infochannel");
//		imageGenService.generateImages(script,"src/main/java/com/aadeshwagh/ContentWiz/creation/prompts/stick-figure-art-style-prompt.txt","content/infochannel");
//		videoAssemblyService.assembleVideo(script,"/Users/aadeshwagh/ContentWiz/content/infochannel","/Users/aadeshwagh/ContentWiz/content/infochannel/long");
//		videoAssemblyService.assembleVerticalVideo(script,"/Users/aadeshwagh/ContentWiz/content/infochannel","/Users/aadeshwagh/ContentWiz/content/infochannel/short");


	}



}
