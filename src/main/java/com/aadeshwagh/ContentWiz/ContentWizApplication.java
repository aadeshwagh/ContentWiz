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
//	    scriptGenerator.generateScript("The topic is \"Why Your Brain Lies to You About Time\" — covering the psychology and neuroscience of time perception: why boring moments drag, why exciting moments fly, why childhood summers felt infinite but adult years vanish, and the \"oddball effect\" where near-death survivors report time slowing down.\n" +
//				"Visual style: minimalist stick figures on a plain white background. Characters are expressive through simple body language — arms raised, slouching, running. Use a single stick figure narrator who reacts to each concept. Diagrams should be hand-drawn-style: simple clocks, basic brain outlines, timeline bars. No photorealistic elements. Everything should look like it was sketched on a whiteboard.\n" +
//				"Tone: curious, slightly mind-bending, conversational. Like a smart friend explaining something at 1am that you can't stop thinking about.\n" +
//				" Each scene introduces one concept, shows a stick figure experiencing it, and ends with a short punchy narration line. Transitions should feel like flipping between whiteboard sketches.\n" +
//				"Narration style: written for Chatterbox TTS full model — short punchy sentences mixed with longer builds, contractions mandatory, capitalization for stress, em dashes for breath. No paralinguistic tags. Per-scene exaggeration values should range from 0.45 (calm explainer scenes) to 0.85 (the near-death oddball effect scene).\n" +
//				"Camera movements: slow zoom-ins on diagrams, gentle pan across timeline visuals, static hold on emotional reaction moments.\n" +
//				"End with a retention hook that makes the viewer question something about their own daily experience of time.","src/main/java/com/aadeshwagh/ContentWiz/creation/prompts/long-video-script-system-prompt-stick-figure-style.txt","content/infochannel");
//		Script script = objectMapper.readValue(new File("content/infochannel/script.json"),Script.class);
  	  //  chatterboxTtsService.generateForScript(script, "src/main/resources/voices/dan.wav", "content/infochannel");
//		imageGenService.generateImages(script,"src/main/java/com/aadeshwagh/ContentWiz/creation/prompts/stick-figure-art-style-prompt.txt","content/infochannel");
		//videoAssemblyService.assembleVideo(script,"/Users/aadeshwagh/ContentWiz/content/infochannel","/Users/aadeshwagh/ContentWiz/content/infochannel/long");



	}



}
